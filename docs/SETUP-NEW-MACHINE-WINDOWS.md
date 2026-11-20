# Chạy SkyGo trên máy Windows mới — MariaDB Vector

Phần cache và rate limit đổi chuyến cần Redis. Xem [hướng dẫn cài Docker, chạy Redis và backend](guide/backend-redis-docker.md) trước khi thử luồng đặt vé.

Hướng dẫn cho thành viên vừa pull code, dùng database riêng trên laptop. Dữ liệu tạo bởi migration trên máy mới nên ngày bay, tài khoản và số document có thể khác máy Quân. **Không cần Docker/Qdrant cho phần AI nữa.**

Thứ tự: **cài môi trường → tạo `.env` → chạy migration với AI tắt → lập index → bật backend AI → chạy Expo**. Nếu đã có vector trong Qdrant trên chính máy mình, dùng [hướng dẫn chuyển vector](ai/mariadb-vector-migration.md) thay vì embed lại toàn bộ.

## 1. Môi trường cần cài

| Thành phần | Yêu cầu |
| --- | --- |
| Git | Clone/pull code |
| JDK | Java 21; Maven Wrapper đã có trong `backend` |
| Node.js/npm | Node 24.x từ 24.3.0 hoặc Node 22.x từ 22.13.0; phù hợp dependency Expo 58/RN 0.88 của repo |
| MariaDB | Dùng MariaDB 11.8 như môi trường hiện tại; cần có vector (tối thiểu 11.7.1) |
| Điện thoại | Expo Go tương thích Expo SDK 58 |

Trong PowerShell:

```powershell
git --version
java -version
node --version
npm --version
```

Nếu có nhiều JDK, đặt `JAVA_HOME` về JDK 21 thực tế. Ví dụ:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
& "$env:JAVA_HOME\bin\java.exe" -version
```

Thay đường dẫn nếu dùng Temurin hoặc cài JDK ở thư mục khác. Có thể đặt `JAVA_HOME` trong Windows Environment Variables rồi mở terminal mới. Không cần cài Maven hay Expo CLI toàn cục.

## 2. Pull code và chọn thư mục

Clone repository hoặc pull đúng branch nhóm sử dụng. Cần đủ backend/frontend AI, migration V12, `skygo-ai.properties`, `META-INF/spring.factories`, prompts và các script. Ví dụ đặt project tại `C:\Projects\SkyGo`:

```powershell
Set-Location 'C:\Projects\SkyGo'
git status
```

Thay đường dẫn theo máy mình. Các lệnh script bên dưới chạy từ thư mục chứa `backend`, `frontend` và `scripts`.

## 3. Tạo database và cấu hình backend

### Database

Bật service MariaDB. Trong HeidiSQL, kết nối bằng tài khoản có quyền tạo/sửa schema và đọc/ghi database, chạy:

```sql
CREATE DATABASE IF NOT EXISTS skygo
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Dùng database rỗng mới. Không import `database/skygo-demo.sql` trước Flyway. Nếu đã có database cùng tên chứa dữ liệu riêng, chọn tên mới trong `DB_URL`, không xóa dữ liệu cũ.

### File `.env`

```powershell
if (-not (Test-Path -LiteralPath '.\backend\.env')) {
    Copy-Item -LiteralPath '.\backend\.env.example' -Destination '.\backend\.env'
}
notepad .\backend\.env
```

Sửa các dòng tương ứng, không thêm nhiều dòng trùng tên:

```dotenv
DB_URL=jdbc:mariadb://localhost:3306/skygo?createDatabaseIfNotExist=true
DB_USERNAME=TAI_KHOAN_DB_CUA_BAN
DB_PASSWORD=MAT_KHAU_DB_CUA_BAN
AUTH_SESSION_TTL=24h

SKYGO_REVIEW_ENCRYPTION_KEY=KHOA_BASE64_THU_NHAT
SKYGO_REVIEW_KEY_ID=v1
SKYGO_BOOKING_DOCUMENT_ENCRYPTION_KEY=KHOA_BASE64_THU_HAI
SKYGO_BOOKING_DOCUMENT_KEY_ID=v1

SKYGO_AI_ENABLED=false
GEMINI_API_KEY=API_KEY_GEMINI_CUA_BAN
SKYGO_AI_CHAT_MODEL=gemini-3.1-flash-lite
SKYGO_AI_EMBEDDING_MODEL=gemini-embedding-001
SKYGO_AI_EMBEDDING_DIMENSIONS=768
SKYGO_AI_INDEX_ENABLED=false
```

Các chữ kiểu `MAT_KHAU_DB_CUA_BAN` là chỗ phải thay. Đổi cả mật khẩu có sẵn trong `.env.example` thành mật khẩu trên máy mình. Giữ cấu hình ngưỡng, batch và quota còn lại của file mẫu trong lần đầu. Google project phải có quyền dùng model chat và embedding đã chọn; dùng chung project nghĩa là dùng chung quota.

Tạo hai khóa riêng, mỗi khóa 32 byte, để dùng cho dữ liệu mới trên máy mình:

```powershell
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try {
    foreach ($name in @('SKYGO_REVIEW_ENCRYPTION_KEY', 'SKYGO_BOOKING_DOCUMENT_ENCRYPTION_KEY')) {
        $bytes = New-Object byte[] 32
        $rng.GetBytes($bytes)
        Write-Output ($name + '=' + [Convert]::ToBase64String($bytes))
    }
} finally {
    $rng.Dispose()
}
```

Chép mỗi giá trị vào đúng dòng trong `.env`. Giữ nguyên khóa khi sử dụng database đó; đổi khóa sẽ không đọc được dữ liệu mã hóa bằng khóa cũ. Không commit `.env`, khóa hoặc API key. Các biến `PAYOS_*` có thể để trống để kiểm thử tra cứu/AI; tạo QR và thanh toán cần cấu hình payOS/webhook riêng.

## 4. Chạy migration khi AI tắt

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1 -DisableAi
```

Script nạp DB/khóa mã hóa từ `.env`, chạy backend cổng 8080 với AI và index tắt. Đợi Flyway áp dụng migration V1–V12 và `Started BackendApplication`. V2 tạo danh mục/tuyến/lịch bay mẫu theo thời điểm chạy; V12 tạo `ai_document` và `ai_vector_schema`.

Từ terminal khác, thử:

```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/regions'
```

Sau đó Ctrl+C ở terminal backend, đợi ứng dụng dừng. **Chạy bước này trước index** vì script index chủ động tắt Flyway. Không sửa checksum hoặc migration đã áp dụng.

Nếu script không tìm thấy JDK tại `C:\Program Files\Java\jdk-21`, cấu hình `JAVA_HOME` về JDK 21 đã cài. Nếu cổng 8080 đã có backend, dừng đúng tiến trình cũ trong IDE/terminal trước.

## 5. Lập index lần đầu từ dữ liệu của mình

MariaDB và Internet đang hoạt động, cấu hình Gemini hợp lệ; không cần Docker. Tại thư mục project:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\index-ai.ps1
```

Script tự nạp DB và AI từ `.env`, bật AI/index cho tiến trình một lần rồi tự đóng. Nó đọc bảng nghiệp vụ, tạo embedding và ghi vào MariaDB `ai_document`. Mặc định: chuyến 30 ngày tới, booking 15 ngày qua, batch tối đa 8 document, nghỉ 10 giây giữa các batch thay đổi, timeout 90 giây mỗi HTTP attempt.

Giữ terminal, MariaDB và Internet hoạt động. Cần thấy `AI index completed: ...` và **`BUILD SUCCESS`**. Số document/thời gian phụ thuộc dữ liệu/quota, không cố định 875 document hay 16 phút. Database mới chưa có booking nên không có document booking là bình thường. Lệnh này có tiêu quota embedding Gemini.

Nếu gặp 429, xem hạn mức đúng embedding model trong Google AI Studio, đợi giới hạn phút/ngày tương ứng hồi phục rồi chạy lại. Các document đã lưu với hash không đổi được bỏ qua, không cần xóa bảng để bắt đầu lại.

Kiểm tra trong HeidiSQL:

```sql
SELECT JSON_VALUE(metadata,'$.document_type') AS document_type,
       JSON_VALUE(metadata,'$.scope') AS scope, COUNT(*) AS documents
FROM ai_document GROUP BY document_type,scope;
```

## 6. Bật backend AI

Trong `backend/.env`, đặt:

```dotenv
SKYGO_AI_ENABLED=true
SKYGO_AI_INDEX_ENABLED=false
```

Chạy từ thư mục project:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1
```

Giữ terminal mở. Script nạp `.env`, bật AI, tắt index nền; không cần thiết lập lại biến thủ công mỗi lần. Các biến môi trường có sẵn được ưu tiên hơn `.env`, nên nếu có cấu hình cũ trong IDE/Windows thì cập nhật đúng biến đó. Log nằm tại `backend/target/backend-powershell.log`.

Model và số chiều phải khớp V12 (`gemini-embedding-001`, 768, cosine). Không đổi model embedding/số chiều tùy ý trên bảng hiện có. Model chat có thể đổi sang model Google project cho phép mà không đổi vector.

## 7. Chạy frontend trên điện thoại

Mở terminal khác:

```powershell
Set-Location 'C:\Projects\SkyGo\frontend'
npm ci
if (-not (Test-Path -LiteralPath '.env.local')) {
    Copy-Item -LiteralPath '.env.example' -Destination '.env.local'
}
notepad .env.local
```

Tìm IPv4 adapter **Wi-Fi đang dùng** của laptop bằng `ipconfig`. Ví dụ IP thật của laptop là `192.168.1.25`, đặt:

```dotenv
EXPO_PUBLIC_API_URL=http://192.168.1.25:8080
```

Thay bằng IP thực tế, không dùng IP WSL/Docker/VPN. `localhost` trên điện thoại trỏ đến điện thoại nên không dùng để gọi backend laptop. Gemini key chỉ ở backend, không thêm vào `EXPO_PUBLIC_*`. Luồng tra cứu/chat dùng API Spring Boot, các biến Supabase trong file mẫu không thay thế địa chỉ này.

Điện thoại/laptop dùng cùng mạng cho phép truy cập LAN. Chạy ở thư mục `frontend`:

```powershell
npx expo start --go --lan --clear
```

Quét QR bằng Expo Go; iPhone có thể dùng Camera. Nếu yêu cầu tài khoản, đăng nhập cùng tài khoản Expo ở CLI/Expo Go (`npx expo login`); đây là tài khoản phát triển Expo, độc lập với tài khoản SkyGo/Gemini.

Nếu Metro chọn sai adapter, Ctrl+C rồi dùng IP Wi-Fi thật:

```powershell
$env:REACT_NATIVE_PACKAGER_HOSTNAME = '192.168.1.25'
npx expo start --go --lan --clear
```

Nếu LAN bị chặn, có thể dùng `npx expo start --go --tunnel --clear` theo [hướng dẫn Expo](https://docs.expo.dev/get-started/start-developing/). Tunnel này chỉ dành cho Metro, **không tự mở backend 8080**. Backend vẫn phải có địa chỉ điện thoại truy cập được. Dùng Wi-Fi nhà hoặc hotspot riêng sẽ dễ kiểm tra hơn Wi-Fi quán có cô lập thiết bị.

## 8. Chọn chuyến có thật để kiểm thử

Trong HeidiSQL, chọn database của mình và chạy query sau. Nó lấy chuyến xuất phát từ Việt Nam, dùng UTC+7 để hiển thị giờ đi:

```sql
SELECT o.code AS origin, o.city AS origin_city,
       d.code AS destination, d.city AS destination_city,
       f.flight_number,
       DATE_ADD(f.departure_at_utc,INTERVAL 7 HOUR) AS departure_vn,
       ff.cabin_class, ff.available_seats,
       ROUND(ff.adult_base_fare*(1+p.vat_rate),0) AS adult_price_vnd
FROM flight f
JOIN air_route r ON r.id=f.route_id
JOIN airport o ON o.id=r.origin_airport_id
JOIN airport d ON d.id=r.destination_airport_id
JOIN flight_fare ff ON ff.flight_id=f.id
JOIN pricing_policy p ON p.id=ff.pricing_policy_id
WHERE o.country_code='VN'
  AND o.is_active=1 AND d.is_active=1 AND r.is_active=1
  AND f.sale_status='ON_SALE' AND ff.is_on_sale=1 AND ff.available_seats>=1
  AND f.departure_at_utc>UTC_TIMESTAMP()
  AND COALESCE(f.booking_closes_at_utc,f.departure_at_utc)>UTC_TIMESTAMP()
ORDER BY f.departure_at_utc,o.code,d.code,adult_price_vnd
LIMIT 20;
```

Query giúp chọn tuyến/ngày, API AI còn kiểm tra công thức giá/chính sách hợp lệ. Nếu sửa thủ công dữ liệu sai chính sách, một dòng ở đây vẫn có thể bị API loại.

Mở Trợ lý SkyGo, thay các phần trong ngoặc bằng dữ liệu vừa tìm:

> Tìm chuyến bay từ [thành phố đi] đến [thành phố đến] ngày [dd/mm/yyyy] cho 1 người lớn, so sánh phổ thông và thương gia.

Kiểm tra số hiệu, ngày, giờ, giá với SQL. Tiếp tục thử 2 người lớn, câu thiếu ngày, hỏi hành lý và lịch sử khi chưa đăng nhập. Migration không tạo đơn của tài khoản khác; không có lịch sử trên database mới là hợp lệ. Nguồn vector hiện là `MARIADB_VECTOR`, nguồn chuyến/giá là `MARIADB`.

Đợi khoảng 60 giây giữa các bài độc lập để hạn chế quota. Trả lời đúng bí danh chưa chứng minh vector được dùng vì model có thể đã đổi thành tên chính thức; xem nguồn để đánh giá nhánh vector.

## 9. Những lần chạy sau và lỗi thường gặp

Hằng ngày chỉ cần bật MariaDB, chạy `start-backend.ps1` và mở Expo trong terminal khác. Giữ hai terminal mở. Nếu IP Wi-Fi thay đổi, sửa `.env.local`, kiểm tra IP Metro rồi khởi động lại Expo. Chạy lại `index-ai.ps1` khi cần đồng bộ tài liệu mới/thay đổi; không cần lập lại index mỗi lần mở app. Chỉ chạy một writer index cho một database.

| Hiện tượng | Xử lý |
| --- | --- |
| Access denied / không kết nối DB | Kiểm tra MariaDB service, DB_URL, tài khoản/mật khẩu và biến môi trường cũ |
| V12 lỗi vì không có VECTOR | Kiểm tra `SELECT VERSION()`; cần MariaDB hỗ trợ vector, nên dùng 11.8 |
| Thiếu bảng `ai_document` / `ai_vector_schema` | Hoàn tất bước 4 trước index |
| Sai model/số chiều/index | Khôi phục cấu hình embedding khớp V12; không bật tự xóa/tạo bảng |
| HTTP 429 Gemini | Xem quota đúng chat/embedding model, đợi giới hạn tương ứng, tránh gửi liên tục |
| HTTP 503 ở model chat | Upstream chưa xử lý được request; thử lại sau, không mặc định do thiếu vector |
| `EMBEDDING_*` trong dòng `AI chat model call failed` | Bộ phân loại lỗi dùng chung; xem HTTP/transport. `round=1` là lần gọi model sau dữ liệu công cụ |
| Expo mở được nhưng API lỗi | Mở `http://IP_LAPTOP:8080/api/v1/regions` bằng trình duyệt điện thoại; kiểm tra backend/IP/firewall |
| Không tải được project trong Expo Go | Kiểm tra URL Metro, IP Wi-Fi, VPN và quyền truy cập LAN; cùng tên Wi-Fi không bảo đảm thiết bị nhìn thấy nhau |
| Wi-Fi có trang yêu cầu kết nối | Hoàn tất đăng nhập mạng trước, kiểm tra VPN nếu nó chặn trang xác thực |
| `npm.ps1`/`npx.ps1` bị ExecutionPolicy chặn | Dùng `npm.cmd`/`npx.cmd` tương ứng trong PowerShell |
| Không có chuyến ở ngày ví dụ của người khác | Chọn tuyến/ngày bằng SQL ở bước 8, lịch seed phụ thuộc thời điểm migration |

Trên mạng riêng đáng tin cậy, cho phép Java/Node qua Windows Firewall profile Private cho cổng cần dùng 8080/8081; không cần tắt toàn bộ firewall.

Chẩn đoán Gemini tùy nhu cầu, chạy tại thư mục project:

```powershell
# Không gọi Gemini
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\diagnose-gemini.ps1 -CheckOnly
# Một request ngắn trực tiếp đến Gemini, có dùng quota chat
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\diagnose-gemini.ps1
```

Khi gửi lỗi, kèm câu hỏi/thời điểm và HTTP/transport, che API key/token/dữ liệu cá nhân. Xem thêm [API chat](ai/chat-api.md), [đồng bộ dữ liệu](ai/data-indexing.md), [cấu hình nền tảng](ai/spring-ai-foundation.md). Hướng dẫn đối chiếu code ngày 11/11/2026.
