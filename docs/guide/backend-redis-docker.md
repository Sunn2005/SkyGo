# Chạy backend với Redis: hướng dẫn cho máy mới

Hướng dẫn dành cho thành viên vừa clone/pull SkyGo, chưa cài Docker hoặc Redis.
Các lệnh dùng **PowerShell trên Windows**, chạy tại **gốc repo SkyGo**, nơi có
`backend`, `frontend` và `scripts`, trừ khi bước đó ghi rõ vị trí khác.
Ví dụ repo nằm ở `C:\Projects\SkyGo`; thay đường dẫn này bằng thư mục của bạn.

## 1. Các thành phần cần chạy

| Thành phần | Chạy ở đâu trong hướng dẫn này? | Công dụng |
| --- | --- | --- |
| Backend Java 21 | Máy tính, bằng Maven Wrapper hoặc IntelliJ | API, mặc định cổng 8080 |
| MariaDB 11.8 | Service MariaDB đã cài trên máy, mặc định cổng 3306 | Dữ liệu nghiệp vụ và migration Flyway |
| Redis | Container Docker, cổng 6379 trên máy | Cache tìm kiếm và giới hạn yêu cầu đổi chuyến |

**Docker Desktop không tự chạy Redis hoặc backend sau khi bạn pull code.**
Repo có sẵn file `backend/compose.redis.yaml`; bạn vẫn cần cài Docker, mở
Docker Desktop và chạy lệnh khởi động Redis. Không cần cài Redis riêng vào Windows.
File compose này chỉ chạy Redis, không tạo MariaDB hoặc chạy backend.

Hướng dẫn này tắt AI khi khởi động, nên chưa cần Gemini API key hoặc lập chỉ mục AI.
Phần tạo QR/thanh toán PayOS có cấu hình riêng ở [hướng dẫn PayOS](../payos-demo.md).
Tìm kiếm, cache, giữ chỗ và rate limit không cần khóa PayOS.

## 2. Cài môi trường một lần

### JDK và database

- Cài **JDK 21**, đặt `JAVA_HOME` về thư mục JDK thực tế.
- Cài/chạy **MariaDB 11.8**. Repo có migration dùng `VECTOR`, nên không thay bằng
  MySQL hoặc MariaDB cũ không hỗ trợ tính năng này, kể cả khi tắt AI.
- Cài Git để clone/pull. Maven Wrapper đã nằm trong `backend`, không cần cài Maven toàn cục.

Kiểm tra trong PowerShell:

```powershell
git --version
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
& "$env:JAVA_HOME\bin\java.exe" -version
```

Đổi đường dẫn JDK nếu bạn dùng Temurin hoặc thư mục cài khác. Có thể đặt
`JAVA_HOME` trong Windows Environment Variables để không phải nhập lại mỗi terminal.

### Docker Desktop

1. Cài theo [hướng dẫn Docker Desktop cho Windows](https://docs.docker.com/desktop/setup/install/windows-install/).
2. Nếu máy chưa có WSL, mở PowerShell với quyền Administrator và chạy `wsl --install`;
   khởi động lại nếu Windows yêu cầu. Có thể cập nhật bằng `wsl --update`.
3. Trong Docker Desktop, sử dụng WSL 2 engine và Linux containers theo hướng dẫn cài đặt.
4. Mở Docker Desktop, đợi engine chạy xong, rồi mở terminal mới.

Kiểm tra:

```powershell
docker --version
docker compose version
docker info
```

`docker --version` chỉ xác nhận có CLI. `docker info` phải kết nối được engine.
Nếu báo lỗi named pipe/không kết nối Docker API, hãy mở Docker Desktop và chờ engine sẵn sàng.

## 3. Lấy code và chọn đúng thư mục

Máy chưa có repo:

```powershell
Set-Location 'C:\Projects'
git clone https://github.com/Sunn2005/SkyGo.git
Set-Location '.\SkyGo'
```

Đã có repo, kiểm tra code đang làm trước khi pull:

```powershell
Set-Location 'C:\Projects\SkyGo'
git status
git pull --ff-only origin main
```

Nếu đang có thay đổi local, cất chúng bằng commit/stash trước khi pull.
Không ghi đè các thay đổi của mình để làm theo hướng dẫn.

## 4. Khởi động Redis bằng Docker

Tại gốc repo:

```powershell
docker compose -f .\backend\compose.redis.yaml up -d
docker compose -f .\backend\compose.redis.yaml ps
docker compose -f .\backend\compose.redis.yaml exec redis redis-cli ping
```

Lần đầu Docker sẽ tải image `redis:8.10.3-alpine`. Lệnh cuối cần trả về:

```text
PONG
```

Ý nghĩa lệnh:

| Thành phần | Ý nghĩa |
| --- | --- |
| `docker compose` | Quản lý các service được mô tả trong file compose |
| `-f .\backend\compose.redis.yaml` | Chọn file cấu hình Redis của dự án |
| `up` | Tạo và khởi động các container cần thiết |
| `-d` | Chạy nền để tiếp tục dùng terminal |
| `exec redis redis-cli ping` | Chạy lệnh kiểm tra bên trong service Redis |

Compose hiện ánh xạ `127.0.0.1:6379:6379`: backend chạy trên máy kết nối tới
`redis://localhost:6379`. Redis chỉ được công bố trên loopback của máy này.
Frontend/điện thoại gọi backend, không kết nối trực tiếp Redis.

Nếu mở terminal tại `backend`, lệnh tương đương là
`docker compose -f .\compose.redis.yaml up -d`. Không ghép đường dẫn của hai cách.

## 5. Chuẩn bị MariaDB và file môi trường

Mở service MariaDB. Dùng HeidiSQL hoặc công cụ SQL với tài khoản của bạn, tạo
database rỗng nếu chưa có:

```sql
CREATE DATABASE IF NOT EXISTS skygo
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Tài khoản chạy backend cần quyền đọc/ghi và thay đổi schema để Flyway tạo bảng,
index và ràng buộc. Nếu đang có database chứa dữ liệu cần giữ, có thể chọn tên
database mới rồi sửa `DB_URL`; không xóa database cũ.
Không import `database/skygo-demo.sql` vào database trước khi chạy Flyway.

Tại gốc repo, chỉ tạo `.env` khi chưa có:

```powershell
if (-not (Test-Path -LiteralPath '.\backend\.env')) {
    Copy-Item -LiteralPath '.\backend\.env.example' -Destination '.\backend\.env'
}
notepad .\backend\.env
```

Sửa các dòng tương ứng, không thêm nhiều dòng trùng tên:

```dotenv
DB_URL=jdbc:mariadb://localhost:3306/skygo?createDatabaseIfNotExist=true
DB_USERNAME=TAI_KHOAN_MARIADB_CUA_BAN
DB_PASSWORD=MAT_KHAU_MARIADB_CUA_BAN
SKYGO_REVIEW_ENCRYPTION_KEY=
SKYGO_REVIEW_KEY_ID=v1
SKYGO_BOOKING_DOCUMENT_ENCRYPTION_KEY=
SKYGO_BOOKING_DOCUMENT_KEY_ID=v1
SKYGO_AI_ENABLED=false
```

Thay hai giá trị tài khoản/mật khẩu bằng thông tin MariaDB thực tế. Các biến
`PAYOS_*` và `GEMINI_API_KEY` có thể để trống trong lần chạy này.

### Tạo khóa mã hóa cho database mới

Giữ hai dòng khóa trong file mẫu ở trạng thái trống, lưu file rồi chạy đoạn
sau tại gốc repo. Đoạn này chỉ điền **các dòng khóa đang trống**, không in khóa
ra terminal và không thay khóa đã có:

```powershell
$configPath = (Resolve-Path '.\backend\.env').Path
$configText = [IO.File]::ReadAllText($configPath)
$keyGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
try {
    foreach ($keyName in @('SKYGO_REVIEW_ENCRYPTION_KEY', 'SKYGO_BOOKING_DOCUMENT_ENCRYPTION_KEY')) {
        $emptyKeyPattern = '(?m)^' + [regex]::Escape($keyName) + '=[ \t]*\r?$'
        if ([regex]::IsMatch($configText, $emptyKeyPattern)) {
            $keyBytes = New-Object byte[] 32
            $keyGenerator.GetBytes($keyBytes)
            $keyValue = [Convert]::ToBase64String($keyBytes)
            $configText = [regex]::Replace($configText, $emptyKeyPattern, $keyName + '=' + $keyValue)
        }
    }
    [IO.File]::WriteAllText($configPath, $configText, [Text.UTF8Encoding]::new($false))
} finally {
    $keyGenerator.Dispose()
}
```

Hai khóa là hai giá trị riêng, mỗi giá trị là Base64 của 32 byte ngẫu nhiên.
Giữ nguyên khóa khi tiếp tục dùng database đó; dữ liệu review/giấy tờ/hồ sơ đã
lưu phụ thuộc vào khóa này. Nếu dùng chung hoặc khôi phục database đã có dữ
liệu mã hóa, lấy đúng khóa tương ứng từ người quản lý môi trường thay vì tạo khóa mới.
`.env` là cấu hình riêng của máy, không commit lên GitHub.

## 6. Chạy backend

Ở **cùng terminal tại gốc repo**, đặt Redis URL rồi chạy script có sẵn:

```powershell
$env:REDIS_URL = 'redis://localhost:6379'
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1 -DisableAi
```

Script tìm JDK 21, nạp DB/khóa từ `backend/.env`, rồi chạy Maven Wrapper với AI
và AI indexing tắt. Biến môi trường đã có trong terminal được ưu tiên hơn `.env`.
Lần đầu Maven cần mạng để tải dependency. Giữ terminal này mở khi sử dụng backend.

**Lưu ý cách nạp `.env`:** Spring Boot không tự nạp mọi biến từ file này.
Post-processor trong repo chỉ hỗ trợ fallback cho biến AI; script
`start-backend.ps1` nạp thêm nhóm DB, SKYGO, AUTH và PAYOS. Script hiện **không
nạp `REDIS_URL` từ `.env`**, nên đặt `$env:REDIS_URL` như trên, hoặc để dùng mặc
định `redis://localhost:6379` trong `application.properties`.

Flyway sẽ tạo schema và áp dụng các migration còn thiếu, bao gồm V15 cho hành
khách đã lưu. Khi thấy `Started BackendApplication`, mở terminal thứ hai và thử:

```powershell
curl.exe 'http://localhost:8080/api/v1/regions'
curl.exe 'http://localhost:8080/api/v1/airports?keyword=SGN&size=7'
```

Kết quả là JSON. Không dùng `/actuator/health` để kiểm tra vì hướng dẫn này
không dựa vào việc dự án có Actuator.

### Chạy bằng IntelliJ thay cho script

Chọn JDK 21 và main class `BackendApplication`. Trong Run Configuration:

- Nạp `backend/.env` bằng chức năng/plugin hỗ trợ env file, hoặc khai báo các
  biến DB/khóa ở mục Environment variables.
- Đặt `REDIS_URL=redis://localhost:6379` trong Environment variables.
- Đặt Program arguments: `--skygo.ai.enabled=false --skygo.ai.index.enabled=false`.
- Chạy ứng dụng, không chạy đồng thời script và IDE trên cùng cổng 8080.

Nếu chạy trực tiếp `.\mvnw.cmd spring-boot:run` trong `backend`, cần tự truyền
các biến DB/khóa vào terminal trước; chỉ có file `.env` chưa đủ cho các biến đó.

## 7. Cache và rate limit đã có cấu hình sẵn

Không cần thêm dependency hoặc viết lại cấu hình Redis sau khi pull.
Các giá trị hiện tại trong `backend/src/main/resources/application.properties`:

| Cấu hình | Giá trị | Tác dụng |
| --- | --- | --- |
| `spring.data.redis.url` | `${REDIS_URL:redis://localhost:6379}` | Redis backend kết nối tới |
| `spring.data.redis.timeout` | `2s` | Timeout thao tác Redis |
| `skygo.cache.airports.ttl` | `10m` | Cache danh sách sân bay |
| `skygo.cache.flight-search.ttl` | `1m` | Cache tìm chuyến và các ngày xung quanh |
| `skygo.flight-switch.rate-limit.max-requests` | `10` | Hạn mức yêu cầu đổi chuyến mỗi tài khoản |
| `skygo.flight-switch.rate-limit.window` | `60s` | Cửa sổ trượt tính hạn mức |

Muốn đổi TTL hoặc hạn mức, sửa property tương ứng rồi khởi động lại backend.
Đổi TTL không cập nhật ngay thời hạn của các cache entry đã có; entry mới dùng
TTL mới. Bộ đếm rate limit hiện có cũng cần hết cửa sổ cũ trước khi thử lại mức mới.

Rate limit tính chung cho ba API:

- `PUT /api/v1/seat-holds/{seatHoldId}/selection`
- `PUT /api/v1/seat-holds/{seatHoldId}/legs/{direction}`
- `POST /api/v1/bookings/{bookingId}/switch-hold`

Một tài khoản đổi A → B → A vẫn tính hai yêu cầu. Các yêu cầu hợp lệ về cấu
trúc và đến bước kiểm tra limiter đều được tính, kể cả khi nghiệp vụ sau đó
trả lỗi. Lần thử vượt hạn mức trả **429 `FLIGHT_SWITCH_RATE_LIMITED`** kèm
`Retry-After` (số giây tối thiểu cần chờ theo thời điểm kiểm tra).
Đây là giới hạn tần suất, tách biệt với hạn giữ chỗ 15 phút.
GET giữ chỗ hiện tại, tạo giữ chỗ ban đầu và sửa số hành khách không dùng hạn mức này.

Redis không chạy có hai ảnh hưởng khác nhau:

- Cache sân bay/chuyến bay đọc lại MariaDB, vẫn có thể trả kết quả nhưng có thể chậm hơn.
- Các API đổi chuyến trả **503 `RATE_LIMIT_UNAVAILABLE`**, vì không kiểm tra được hạn mức.

Vì vậy, backend có thể khởi động được nhưng chức năng đổi chuyến vẫn lỗi nếu
Redis chưa sẵn sàng. Không kết luận Redis hoạt động chỉ từ việc tìm được sân bay.
Kết quả tìm chuyến có thể lưu tối đa 1 phút; backend vẫn kiểm tra ghế/điều kiện
nghiệp vụ khi giữ chỗ hoặc đổi chuyến.

## 8. Kiểm tra Redis thực sự được dùng

### Danh sách sân bay

Gọi cùng URL hai lần, sau đó tìm khóa cache:

```powershell
curl.exe 'http://localhost:8080/api/v1/airports?keyword=SGN&size=7'
curl.exe 'http://localhost:8080/api/v1/airports?keyword=SGN&size=7'
docker compose -f .\backend\compose.redis.yaml exec redis redis-cli --scan --pattern 'skygo:airports:v1:*'
```

Kiểm tra TTL của một khóa vừa tìm được:

```powershell
$airportCacheKey = docker compose -f .\backend\compose.redis.yaml exec -T redis redis-cli --scan --pattern 'skygo:airports:v1:*' | Select-Object -First 1
if ($airportCacheKey) {
    docker compose -f .\backend\compose.redis.yaml exec -T redis redis-cli TTL $airportCacheKey
}
```

TTL là số giây còn lại, tối đa khoảng 600 cho cache mới. `-2` nghĩa là khóa
không còn tồn tại; gọi lại API rồi thử lại. Hai lần gọi API trả JSON giống
nhau là bình thường; dữ liệu đầu ra không cần thay đổi khi dùng cache.

Sau khi tìm chuyến/ngày xung quanh trên frontend, kiểm tra tương tự:

```powershell
docker compose -f .\backend\compose.redis.yaml exec redis redis-cli --scan --pattern 'skygo:flights:search:v1:*'
docker compose -f .\backend\compose.redis.yaml exec redis redis-cli --scan --pattern 'skygo:flights:nearby:v1:*'
```

### Rate limit

Đăng nhập bằng tài khoản test, chọn/đổi chuyến nhiều lần trong 60 giây. Khi
vượt hạn mức, frontend sẽ hiện thời gian chờ và tạm khóa nút đổi chuyến.
Lượt chọn đầu tiên tạo hold chưa dùng hạn mức; số lần bấm trên UI không luôn
bằng số request vì có bước xác nhận hoặc lấy dữ liệu trung gian.

Có thể kiểm tra khóa và số yêu cầu được limiter cho qua trong cửa sổ hiện tại:

```powershell
docker compose -f .\backend\compose.redis.yaml exec redis redis-cli --scan --pattern 'skygo:rate-limit:flight-switch:v1:user:*'
```

Dùng khóa vừa tìm được với `redis-cli ZCARD <key>` để xem số mục trong sorted
set; ở luồng hoạt động bình thường không quá 10. Các mục cũ được dọn khi có
request mới, còn cả khóa tự hết hạn khi không được sử dụng. Đợi cửa sổ cũ hết
rồi thử lại, không cần khởi động lại Redis để mở khóa tài khoản.

## 9. Quy trình mỗi ngày và lệnh quản lý

Sau lần cài đặt đầu tiên, mỗi lần làm việc:

1. Mở Docker Desktop và service MariaDB.
2. Mở terminal tại gốc repo, bật Redis bằng `up -d`, kiểm tra `PONG`.
3. Đặt `$env:REDIS_URL` nếu cần, chạy `start-backend.ps1 -DisableAi`.
4. Chạy frontend ở terminal khác theo [hướng dẫn Windows](../SETUP-NEW-MACHINE-WINDOWS.md).

Các lệnh dùng tại gốc repo:

```powershell
# Xem trạng thái và log Redis
docker compose -f .\backend\compose.redis.yaml ps
docker compose -f .\backend\compose.redis.yaml logs --tail 50 redis

# Tạm dừng, giữ container; khi cần chạy lại dùng up -d
docker compose -f .\backend\compose.redis.yaml stop

# Dừng và xóa container/network của compose Redis local
docker compose -f .\backend\compose.redis.yaml down
```

`Ctrl+C` ở terminal backend dừng backend, không dừng Redis. File compose local
hiện không gắn volume dữ liệu; cache và bộ đếm không được đảm bảo giữ lại khi
xóa/tạo lại container. Thông tin vé/hành khách nằm trong MariaDB, không phải Redis.
Không cần dùng `FLUSHALL` để vận hành bình thường.

## 10. Lỗi thường gặp

| Hiện tượng | Kiểm tra và cách xử lý |
| --- | --- |
| Không tìm thấy lệnh `docker` | Cài Docker Desktop rồi mở terminal mới. |
| Docker báo named pipe, daemon chưa chạy hoặc permission denied | Mở Docker Desktop, đợi engine; kiểm tra `docker info` và hướng dẫn quyền của Docker. |
| `no configuration file provided` | Dùng đúng `-f .\backend\compose.redis.yaml` từ gốc repo. |
| Không bind được cổng 6379 | Kiểm tra `docker ps` hoặc `Get-NetTCPConnection -LocalPort 6379`; xác định service đang dùng cổng trước khi dừng nó. |
| Backend tìm kiếm được nhưng đổi chuyến trả 503 | Thử `redis-cli ping`, kiểm tra `REDIS_URL` và log Redis. |
| Đổi chuyến trả 429 | Hết hạn mức, chờ theo `Retry-After`; đây là hành vi dự kiến. |
| Connection refused ở MariaDB hoặc Access denied | Bật service và đối chiếu `DB_URL`, tài khoản, mật khẩu, quyền database. |
| Migration lỗi ở `VECTOR` | Kiểm tra đang dùng MariaDB 11.8, không dùng MySQL/MariaDB cũ. |
| Backend yêu cầu Gemini key | Dùng `-DisableAi`; với IntelliJ đặt program argument tắt AI. |
| `REVIEW_ENCRYPTION_UNAVAILABLE` hoặc `DOCUMENT_ENCRYPTION_UNAVAILABLE` | Kiểm tra khóa Base64 32 byte và cách nạp `.env`; giữ khóa khớp database. |
| Cổng 8080 đã được sử dụng | Dừng đúng backend cũ, hoặc chạy script với `-DisableAi -Port 8082` rồi cập nhật URL frontend. |
| Tìm kiếm không có chuyến | Kiểm tra ngày/tuyến trong MariaDB; `PONG` chỉ chứng minh Redis chạy, không chứng minh có lịch bay. |

Nếu cổng 6379 đã được dùng và bạn muốn Redis dự án chạy trên 6380, đổi mapping
trong compose thành `127.0.0.1:6380:6379`, chạy lại `up -d`, đặt
`$env:REDIS_URL='redis://localhost:6380'` rồi khởi động lại backend.
Cổng bên trong container vẫn là 6379.

## 11. Phân biệt chạy local và triển khai toàn bộ bằng Docker

Hướng dẫn này dùng **Redis trong Docker + backend trên máy**:
`REDIS_URL=redis://localhost:6379`.

File `deploy/aws/docker-compose.yml` chạy cả backend, MariaDB, Redis và Caddy;
backend trong mạng Docker dùng `redis://redis:6379`. Trong container backend,
`localhost` trỏ đến chính container backend, không trỏ về Redis hay máy của bạn.
Nếu triển khai toàn bộ stack, dùng [hướng dẫn AWS](../../deploy/aws/README.md),
không chạy chồng hai compose như cùng một môi trường.

Tài liệu tham khảo: [Docker Compose up](https://docs.docker.com/reference/cli/docker/compose/up/),
[Docker Compose down](https://docs.docker.com/reference/cli/docker/compose/down/),
[cấu hình ngoài ứng dụng của Spring Boot](https://docs.spring.io/spring-boot/reference/features/external-config.html).
