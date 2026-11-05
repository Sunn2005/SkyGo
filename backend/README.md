# SkyGo backend

Java 21, Spring Boot, MariaDB 11.8. Chạy từ thư mục `backend`:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:DB_URL='jdbc:mariadb://localhost:3306/skygo?createDatabaseIfNotExist=true'
$env:DB_USERNAME='your_local_user'
$env:DB_PASSWORD='your_local_password'
.\mvnw.cmd spring-boot:run
```

MariaDB cần đang chạy. Tài khoản cần quyền CREATE DATABASE nếu dùng tự tạo database; có thể tạo database rỗng `skygo` trước nếu không có quyền này. File `.env.example` chỉ là mẫu; Spring Boot không tự đọc file `.env`, hãy truyền biến môi trường hoặc cấu hình trong IDE.

Flyway chạy `V1__create_skygo_schema.sql` khi khởi động, tạo đủ 21 bảng theo `database/data-model.md` và bảng lịch sử Flyway. V1 chỉ tạo schema, chưa nạp chuyến bay hay tài khoản demo. Không chạy `database/skygo-demo.sql` cùng với Flyway. Database cũ đã có bảng nhưng chưa có lịch sử Flyway cần được chuyển đổi riêng hoặc reset thủ công nếu toàn bộ là dữ liệu thử nghiệm. Ứng dụng không tự drop database hoặc bật baseline để bỏ qua khác biệt schema.

Khi thay schema, thêm migration V2, V3...; giữ nguyên migration đã áp dụng. `clean` của Flyway được tắt.

| Endpoint | Kết quả |
| --- | --- |
| POST /api/v1/auth/register | 201, thông tin tài khoản; không tạo phiên |
| POST /api/v1/auth/login | 200, accessToken/tokenType/expiresAt/user |
| GET /api/v1/auth/me | Tài khoản của Bearer token hiện tại |
| POST /api/v1/auth/logout | 204, thu hồi phiên và vô hiệu review chưa dùng |

Đăng ký yêu cầu fullName, phoneNumber, email hợp lệ, password và confirmPassword. Số di động Việt Nam dạng 0... hoặc +84... được chuẩn hóa về +84. Số điện thoại phải duy nhất; email không cần duy nhất. Mật khẩu ít nhất 12 ký tự, gồm chữ hoa, chữ thường, số, ký tự đặc biệt; tối đa 72 byte UTF-8 theo BCrypt. Không trim mật khẩu. Backend chỉ lưu hash mật khẩu và hash SHA-256 của token ngẫu nhiên 256 bit. Các repository auth dùng JDBC với transaction Spring để kiểm soát khóa hàng login_guard; model là record, không phải JPA entity.

Đăng nhập nhận phoneNumber/password. Sai định dạng trả 422; sai thông tin trả 401 chung; lần sai thứ năm trong cửa sổ trượt 15 phút trả 429 với Retry-After và lockedUntil. Trong thời gian khóa, mật khẩu đúng cũng không mở khóa và các request không kéo dài khóa. Login guard được ghi ngay cả khi request đăng nhập trả lỗi. Hết khóa hoặc đăng nhập đúng sẽ xóa chuỗi. Thời hạn phiên cấu hình qua AUTH_SESSION_TTL (mặc định 24h); hiện chưa có refresh token. Đăng xuất chỉ thu hồi phiên hiện tại, không hủy hold/đơn. Response thành công bọc trong data; lỗi bọc trong error.

Kiểm thử đơn vị (không ghi database):

```powershell
.\mvnw.cmd test
```

Kiểm thử migration và HTTP trên database thử nghiệm riêng (không dùng database chứa dữ liệu cần giữ):

```powershell
$env:SKYGO_TEST_DB_URL='jdbc:mariadb://localhost:3306/skygo_auth_test?createDatabaseIfNotExist=true'
$env:SKYGO_TEST_DB_USERNAME='your_local_user'
$env:SKYGO_TEST_DB_PASSWORD='your_local_password'
.\mvnw.cmd test
```

Frontend hiện vẫn dùng mock; bước này hiện thực backend, chưa thay adapter frontend.
