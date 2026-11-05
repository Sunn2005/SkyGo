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

Flyway V2 nạp danh mục hệ thống: 24 sân bay thực tế thuộc 11 nhóm UI, 46 tuyến hai chiều, chính sách giá/quyền lợi và các chuyến bay mẫu với hai hạng ghế. V2 không tạo tài khoản, đơn, thanh toán hay vé. Lịch bay mẫu được tính từ ngày V2 chạy và sẽ hết trong tương lai; khi đó cần bổ sung lịch mới qua quy trình vận hành riêng. `database/home-demo-seed.sql` là fixture thủ công tạo các đơn mẫu cho đúng một tài khoản, không phải migration và không tự chạy khi backend khởi động.

Flyway V3 thêm `airport.cover_image_path` và gán ảnh phong cảnh cho 24 sân bay mẫu. Ảnh nằm tại `src/main/resources/static/airports/{IATA}.jpg`, được trả công khai ở `/airports/{IATA}.jpg`. Nguồn ảnh và giấy phép được ghi trong `AIRPORT_IMAGE_SOURCES.md`. Sân bay bổ sung sau này có thể để `cover_image_path` là `NULL` cho đến khi có ảnh.

Khi thay schema, thêm migration mới (V3, V4...); giữ nguyên migration đã áp dụng. `clean` của Flyway được tắt.

| Endpoint | Kết quả |
| --- | --- |
| POST /api/v1/auth/register | 201, thông tin tài khoản; không tạo phiên |
| POST /api/v1/auth/login | 200, accessToken/tokenType/expiresAt/user |
| GET /api/v1/auth/me | Tài khoản của Bearer token hiện tại |
| POST /api/v1/auth/logout | 204, thu hồi phiên và vô hiệu review chưa dùng |
| GET /api/v1/home | Trang chủ công khai; nếu có Bearer token hợp lệ thì thêm chuyến đã xác nhận sắp tới |
| GET /api/v1/regions | Danh sách nhóm khu vực có sân bay đang hoạt động, không cần đăng nhập |
| GET /api/v1/airports | Tra cứu sân bay để chọn điểm đi/đến, không cần đăng nhập |
| POST /api/v1/flights/search | Tìm chuyến một chiều hoặc từng chiều khứ hồi, không cần đăng nhập |

### API chọn điểm đi và điểm đến

`GET /api/v1/regions` trả `data: [{regionCode, regionName, sortOrder}]`, sắp theo
`sortOrder` rồi `regionCode`. Nhóm là `region_code` đang lưu trong database (ví dụ
`VIETNAM`, `SOUTHEAST_ASIA`), không gộp thành châu lục.

Khi mở một nhóm, gọi `GET /api/v1/airports?regionCode=VIETNAM`. Khi chọn điểm đến,
thêm điểm đi: `GET /api/v1/airports?regionCode=VIETNAM&origin=SGN`.
`origin` chỉ lọc sân bay có tuyến trực tiếp đang hoạt động và chuyến tương lai đang
bán; chưa kiểm tra ngày tìm kiếm hoặc số ghế. Không có điểm đến phù hợp trả `data: []`.
FE thu gọn/mở nhóm, ẩn nhóm có kết quả rỗng ở màn điểm đến, và kiểm tra lại điểm đến
khi đổi điểm đi. Có thể lấy nhiều nhóm trong một lần bằng cách bỏ `regionCode`, rồi
gom theo `regionCode` trên UI; vẫn phải tải hết các trang.

Các bộ lọc tùy chọn: `keyword` (tên, thành phố hoặc mã sân bay, không phân biệt hoa
thường và dấu tiếng Việt, tối đa 200 ký tự), `countryCode`, `regionCode`, `origin`.
Các mã phải đúng chữ hoa như danh mục. Mã không tồn tại/không hoạt động và tham số
không hợp lệ trả `422 VALIDATION_ERROR` với `details[].field`.
Phân trang: `page` mặc định 0, `size` mặc định 20 (1–100), sắp theo mã sân bay.
Response chỉ gồm dữ liệu màn chọn sân bay cần:

```json
{
  "data": [
    {"code":"HAN","name":"Sân bay quốc tế Nội Bài","city":"Hà Nội","countryName":"Việt Nam","regionCode":"VIETNAM"}
  ],
  "pagination": {"page":0,"size":20,"totalElements":1,"totalPages":1}
}
```

Tên nhóm và thứ tự lấy từ `/regions`; sân bay không trả ID nội bộ, múi giờ hoặc
trạng thái hoạt động. Đây là response rút gọn theo UI; ví dụ đầy đủ trong
`api-contract.docx` có thêm trường. Danh mục hiện có 24 sân bay, chưa có CXR.
Các kiểm thử API sân bay trong `AirportIntegrationTest` chỉ chạy khi có
`SKYGO_TEST_DB_URL`; fixture được rollback sau mỗi test. Lịch mẫu hết hạn có thể
khiến danh sách điểm đến rỗng, cần bổ sung lịch qua quy trình vận hành riêng.

Đăng ký yêu cầu fullName, phoneNumber, email hợp lệ, password và confirmPassword. Số di động Việt Nam dạng 0... hoặc +84... được chuẩn hóa về +84. Số điện thoại phải duy nhất; email không cần duy nhất. Mật khẩu ít nhất 12 ký tự, gồm chữ hoa, chữ thường, số, ký tự đặc biệt; tối đa 72 byte UTF-8 theo BCrypt. Không trim mật khẩu. Backend chỉ lưu hash mật khẩu và hash SHA-256 của token ngẫu nhiên 256 bit. Các repository auth dùng JDBC với transaction Spring để kiểm soát khóa hàng login_guard; model là record, không phải JPA entity.

Đăng nhập nhận phoneNumber/password. Sai định dạng trả 422; sai thông tin trả 401 chung; lần sai thứ năm trong cửa sổ trượt 15 phút trả 429 với Retry-After và lockedUntil. Trong thời gian khóa, mật khẩu đúng cũng không mở khóa và các request không kéo dài khóa. Login guard được ghi ngay cả khi request đăng nhập trả lỗi. Hết khóa hoặc đăng nhập đúng sẽ xóa chuỗi. Thời hạn phiên cấu hình qua AUTH_SESSION_TTL (mặc định 24h); hiện chưa có refresh token. Đăng xuất chỉ thu hồi phiên hiện tại, không hủy hold/đơn. Response thành công bọc trong data; lỗi bọc trong error.

`GET /api/v1/home` trả `period` theo giờ Việt Nam, `routeSource` (`POPULAR`, `SUGGESTED`, `EMPTY`), tiêu đề hai mục, `featuredRoute`, tối đa hai `otherRoutes`, và `upcomingFlight` hoặc `null`. Tuyến được chọn nhiều tính theo số `booking_leg` thuộc đơn `PAID` và thanh toán `SUCCEEDED` trong tháng. Chỉ xét tuyến đang hoạt động có chuyến tương lai đang bán và giá Phổ thông còn ghế; `priceFrom.amount` là giá một người lớn, một chiều, **đã gồm VAT**. Khi có dưới ba tuyến đã được thanh toán trong tháng, trả các tuyến đang bán dưới nhãn gợi ý. Nếu không có chuyến đủ điều kiện, trả `routeSource=EMPTY`, `featuredRoute=null`, `otherRoutes=[]`. `imageUrl` là đường dẫn tương đối đến ảnh phong cảnh của sân bay đến, ví dụ `/airports/HAN.jpg`; frontend ghép với base URL backend và hiển thị bằng chế độ `cover`. Ảnh có thể null nếu sân bay đến chưa được gán ảnh. Chuyến sắp tới lấy chặng gần nhất chưa khởi hành của đơn đã thanh toán và đã có vé; ngày giờ trả theo múi giờ sân bay từ snapshot đơn. API này không tạo hold hay đặt vé.

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

### API tìm chuyến bay

`POST /api/v1/flights/search` là API công khai, nhận JSON. Ví dụ:

```json
{
  "tripType": "ROUND_TRIP",
  "origin": "SGN",
  "destination": "HAN",
  "departureDate": "2026-11-18",
  "returnDate": "2026-11-22",
  "passengerCounts": {"adults": 1, "children": 0, "infants": 1},
  "direction": "OUTBOUND",
  "page": 0,
  "size": 20
}
```

- `tripType`: `ONE_WAY` hoặc `ROUND_TRIP`; `direction`: `OUTBOUND` hoặc `RETURN`.
- Một chiều: `returnDate` phải bỏ qua hoặc null, chỉ dùng `OUTBOUND`.
- Khứ hồi: `returnDate` bắt buộc, sau `departureDate`. Khi tìm chiều về, giữ nguyên
  tiêu chí gốc và đổi `direction` thành `RETURN`; backend tự đảo hai sân bay và dùng ngày về.
- Mã sân bay viết hoa và phải đang hoạt động, hai đầu khác nhau. Ngày đi không trước
  ngày hiện tại theo múi giờ sân bay đi ban đầu. Khoảng ngày tìm kiếm tính theo múi giờ
  sân bay khởi hành của chiều đang tìm; giờ đi/đến trong response có offset địa phương
  tương ứng, thời lượng tính từ thời gian UTC.
- `passengerCounts` bắt buộc đủ ba số nguyên: `adults >= 1`, `children >= 0`,
  `infants >= 0`. Không tự áp giới hạn số khách hoặc tỷ lệ em bé/người lớn chưa được chốt.
- `page` mặc định 0, `size` mặc định 20 (1–100). Sắp theo giờ khởi hành rồi `flightId`.
  Phân trang theo chuyến, mỗi chuyến giữ đủ các hạng vé hợp lệ trên cùng trang.

Chỉ trả tuyến hoạt động, chuyến chưa khởi hành đang bán và hạng đang bán còn ít nhất
`adults + children` ghế; em bé không cần ghế. Hạng không đủ ghế bị loại; chuyến không
còn hạng hợp lệ bị loại. Không có kết quả trả 200 với `data: []`, tổng phần tử/trang bằng 0.
Đầu vào không hợp lệ trả 422 `VALIDATION_ERROR`. Tìm kiếm không tạo đơn hoặc giữ chỗ.

Response rút gọn cho màn kết quả:

```json
{
  "data": [{
    "flightId": "seed-v2-SGN-HAN-1",
    "flightNumber": "SG201",
    "airline": {"code": "SG", "name": "SkyGo"},
    "origin": {"code": "SGN", "city": "TP. Hồ Chí Minh"},
    "destination": {"code": "HAN", "city": "Hà Nội"},
    "departureAt": "2026-11-18T10:30:00+07:00",
    "arrivalAt": "2026-11-18T12:35:00+07:00",
    "durationMinutes": 125,
    "fares": [
      {"cabinClass": "ECONOMY", "price": {"amount": 3564000, "currency": "VND", "includesVat": true}},
      {"cabinClass": "BUSINESS", "price": {"amount": 8910000, "currency": "VND", "includesVat": true}}
    ]
  }],
  "pagination": {"page": 0, "size": 20, "totalElements": 1, "totalPages": 1}
}
```

Giá mỗi hạng là giá tham khảo cho **một người lớn trên chiều đang tìm, đã gồm VAT**,
không phải tổng nhóm khách hoặc tổng khứ hồi. Backend lấy giá cơ sở và VAT từ fare/policy
đã lưu, làm tròn HALF_UP đến đồng. Quy ước này theo nghiệp vụ hiện hành và UI, thay cho
ví dụ `adultBaseFare` chưa VAT trong phần tìm kiếm cũ của `api-contract.docx`.
`flightId` và `cabinClass` dùng cho bước chọn chuyến/giữ chỗ tiếp theo. Hãng lấy từ
`skygo.airline.code` và `skygo.airline.name` trong cấu hình backend.

Ngày, mã và số chuyến trong JSON chỉ minh họa. Khi thử Postman cần dùng ngày có lịch
thật trong database; lịch mẫu V2 tính từ ngày migration chạy. Không tìm thấy chuyến
vào ngày minh họa là kết quả hợp lệ. Kiểm thử `FlightSearchServiceTest` dùng clock cố định;
`FlightSearchIntegrationTest` chạy HTTP qua MockMvc và MariaDB test khi có
`SKYGO_TEST_DB_URL`, fixture được rollback sau mỗi test.
