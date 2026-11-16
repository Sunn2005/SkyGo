# Mục 3 – Implementation: source code và hướng dẫn chạy SkyGo

Tài liệu này đáp ứng mục **3. Implementation** của file `MotaDetai_SDLC.txt` trong folder Flight: bàn giao mã nguồn, chỉ ra cách tổ chức module và giúp thành viên/giảng viên **lấy code, chạy tại máy phát triển và kiểm tra kết quả**. Tài liệu [Deployment](DEPLOYMENT-INSTALLATION.md) dùng khi đưa một bản mã xác định lên môi trường test/demo và ghi bằng chứng triển khai.

## 1. Source code và cấu trúc

Repository: [Sunn2005/SkyGo](https://github.com/Sunn2005/SkyGo)  
Địa chỉ clone: `https://github.com/Sunn2005/SkyGo.git`

| Thư mục/file | Vai trò |
| --- | --- |
| `backend/` | Java 21, Spring Boot; REST API, nghiệp vụ, bảo mật và tích hợp MariaDB/PayOS/AI |
| `backend/src/main/resources/db/migration/` | Flyway migration tạo schema và dữ liệu mẫu |
| `frontend/` | React Native, TypeScript, Expo SDK 58; màn hình, điều hướng, gọi API |
| `frontend/src/modules/` | Các module theo chức năng: tài khoản, chuyến bay, booking, thanh toán, vé, hỗ trợ |
| `database/` | Mô hình dữ liệu và các fixture SQL dùng cho mục đích riêng |
| `scripts/` | Script chạy backend và tác vụ hỗ trợ, gồm lập chỉ mục AI |
| `docs/` | Hướng dẫn, đặc tả, kiểm thử và triển khai |

Chỉ những thay đổi **đã commit và push** mới có trong bản clone từ GitHub. Khi bàn giao, ghi branch/commit được chọn và kiểm tra `git status --short` để không bỏ sót file còn ở máy cá nhâ                                      n.

## 2. Chuẩn bị để chạy code trên Windows

Cần Git, JDK 21, MariaDB 11.8, Node.js 24.x từ 24.3.0 hoặc 22.x từ 22.13.0 và npm. Maven Wrapper nằm trong `backend/`; không cần cài Maven hoặc Expo CLI toàn cục. Dùng Expo Go tương thích SDK 58 để mở app trên điện thoại. MariaDB 11.8 đáp ứng migration V12 dùng vector.

Clone mã nguồn và làm việc tại thư mục gốc dự án:

```powershell
git clone https://github.com/Sunn2005/SkyGo.git
Set-Location .\SkyGo
git rev-parse HEAD
```

Khởi động MariaDB. Tạo database **rỗng** `skygo` bằng HeidiSQL hoặc MariaDB client:

```sql
CREATE DATABASE IF NOT EXISTS skygo
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Tài khoản DB dùng để chạy backend cần quyền tạo/sửa bảng và đọc/ghi trong database này. Nếu database `skygo` đã chứa dữ liệu riêng, tạo một database rỗng tên khác và sửa `DB_URL`. Không import `database/skygo-demo.sql` trước Flyway; migration tự tạo schema và nạp danh mục/chuyến mẫu.

## 3. Chạy backend ở máy phát triển

Từ gốc repo:

```powershell
if (-not (Test-Path .\backend\.env)) {
    Copy-Item .\backend\.env.example .\backend\.env
}
notepad .\backend\.env
```

Sửa các dòng có sẵn cho đúng database trên máy:

```dotenv
DB_URL=jdbc:mariadb://localhost:3306/skygo
DB_USERNAME=TAI_KHOAN_DB_CUA_BAN
DB_PASSWORD=MAT_KHAU_DB_CUA_BAN
SKYGO_REVIEW_ENCRYPTION_KEY=KHOA_BASE64_32_BYTE_THU_NHAT
SKYGO_BOOKING_DOCUMENT_ENCRYPTION_KEY=KHOA_BASE64_32_BYTE_THU_HAI
SKYGO_AI_ENABLED=false
SKYGO_AI_INDEX_ENABLED=false
```

Hai khóa mã hóa là Base64 của **hai chuỗi 32 byte ngẫu nhiên khác nhau**. Lệnh tạo khóa và giải thích chi tiết ở [hướng dẫn Windows, mục 3](SETUP-NEW-MACHINE-WINDOWS.md#3-tạo-database-và-cấu-hình-backend). Giữ nguyên khóa khi dùng lại cùng database. Không commit `.env`, mật khẩu hoặc khóa. Script dưới đây đọc `.env`; Spring Boot chạy trực tiếp từ IDE/Maven không tự đọc file này.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1 -DisableAi
```

Đợi `Started BackendApplication`. Lần đầu Flyway chạy migration V1–V12 và nạp dữ liệu mẫu. Giữ terminal này mở. Từ terminal khác, kiểm tra API:

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/regions
Invoke-RestMethod http://localhost:8080/api/v1/home
```

Hai lệnh phải trả JSON. Nếu lỗi khởi động, xem `backend/target/backend-powershell.log`. `-DisableAi` cho phép chạy chức năng chính khi chưa có Gemini key.

## 4. Chạy frontend ở máy phát triển

Trong terminal khác tại gốc repo:

```powershell
Set-Location .\frontend
npm ci
if (-not (Test-Path .\.env.local)) {
    Copy-Item .\.env.example .\.env.local
}
notepad .\.env.local
```

Đặt `EXPO_PUBLIC_API_URL` là **gốc backend**:

| Cách mở app | Giá trị ví dụ |
| --- | --- |
| Web trên cùng máy | `http://localhost:8080` |
| Android Emulator | `http://10.0.2.2:8080` |
| Điện thoại Android/iOS thật | `http://192.168.1.25:8080` (thay bằng IPv4 LAN của máy chạy backend) |

Không thêm `/api/v1`. Điện thoại thật phải truy cập được máy backend qua cùng LAN; `localhost` trên điện thoại không trỏ về máy phát triển. Chạy:

```powershell
npx expo start --go --lan --clear
```

Quét QR bằng Expo Go. Muốn xem trên web, chạy `npm run web`. Nếu đổi `.env.local`, khởi động lại Expo. Các biến `EXPO_PUBLIC_*` hiển thị trong app, không chứa khóa PayOS/Gemini.

## 5. Kiểm tra sau khi chạy và khi sửa code

Sau khi hai phần đã chạy, mở trang chủ, tải sân bay, tìm chuyến còn mở bán, đăng ký/đăng nhập bằng dữ liệu giả. Đây là kiểm tra nhanh rằng frontend gọi backend và backend đọc MariaDB. Chuyến mẫu có ngày phụ thuộc thời điểm migration V2 chạy; không dùng ngày bay cố định từ máy khác.

Các lệnh kiểm tra mã nguồn, chạy trong đúng thư mục:

```powershell
# Tại frontend/
npm run typecheck
npm run lint
npm run test:account
npm run test:booking
npm run test:api
npm run test:payment
```

```powershell
# Tại backend/
.\mvnw.cmd test
```

Một số integration test cần database test riêng qua `SKYGO_TEST_DB_URL`; không trỏ biến này vào database chứa dữ liệu muốn giữ. Kết quả test case và báo cáo thực thi thuộc [tài liệu Testing](testing/README.md).

Trợ lý AI cần Gemini key và bước lập index; thanh toán PayOS cần khóa, URL chuyển hướng và webhook HTTPS. Hai luồng này được hướng dẫn riêng trong [cài đặt Windows có AI](SETUP-NEW-MACHINE-WINDOWS.md) và [demo PayOS](payos-demo.md). Có thể chạy và kiểm tra luồng cơ bản với AI tắt, chưa tạo giao dịch PayOS.

## 6. Phân biệt với mục 5 – Deployment

| | Mục 3 – Implementation | Mục 5 – Deployment |
| --- | --- | --- |
| Câu hỏi cần trả lời | Source ở đâu, tổ chức ra sao, lập trình viên chạy/kiểm tra code thế nào? | Bản mã nào đã được đưa lên môi trường test, cấu hình gì và đã hoạt động chưa? |
| Đối tượng | Thành viên phát triển và người chấm cần xem/chạy source | Người dựng máy demo, nghiệm thu hoặc lặp lại lần triển khai |
| Hướng dẫn chạy | Lệnh clone, cấu hình local, chạy backend/Expo, kiểm tra code | Trình tự triển khai trên máy test, địa chỉ thiết bị/webhook, kiểm tra dịch vụ và quy trình xử lý lỗi |
| Bằng chứng | Link GitHub, branch/commit, cấu trúc module, lệnh chạy/test | Biên bản môi trường, migration, API/app trên thiết bị, ảnh/log và kết quả PayOS/AI nếu dùng |

Hai tài liệu có một số lệnh giống nhau vì cùng chạy SkyGo. Mục đích khác nhau: file này giúp **tái tạo môi trường phát triển từ source**, còn [hướng dẫn Deployment](DEPLOYMENT-INSTALLATION.md) xác nhận **một lần đưa bản mã lên môi trường test cụ thể**.
