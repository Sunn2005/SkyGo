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

Flyway V4 thêm `checked_baggage_policy`, gắn mọi `fare_benefit_policy` với policy
phiên bản 1 và bỏ cột cấu hình `fare_benefit_policy.checked_piece_max_kg`.
Định nghĩa chung của một kiện ký gửi (miễn cước hoặc mua thêm) là một túi/vali tối đa
23 kg, tổng dài + rộng + cao tối đa 158 cm. Mua thêm tối đa 2 kiện/người/chiều,
đơn giá **325000 VND/kiện chưa VAT**; chỉ ADULT/CHILD được mua, INFANT không mua riêng.
Phổ thông/Thương gia vẫn miễn cước lần lượt 1/2 kiện cho ADULT/CHILD.

`booking_passenger_benefit` giữ cân nặng snapshot, thêm FK policy, kích thước,
số kiện mua thêm, đơn giá chưa VAT và thành tiền theo người/chiều.
`booking.extra_baggage_amount` mặc định 0; CHECK tổng tiền mới là
`total_amount = fare_subtotal + extra_baggage_amount + vat_amount`.
Backend tính `vat_amount = round_half_up((fare_subtotal + extra_baggage_amount) * 0.10)`
một lần cho toàn đơn. V4 không tính lại tiền, thay quyền lợi hay sửa ciphertext
của đơn/review cũ; policy/kích thước của snapshot lịch sử để NULL vì trước đây chưa lưu.
Sau V4 có 22 bảng nghiệp vụ và bảng lịch sử Flyway.

Triển khai V4 cùng bản code đọc policy mới; không chạy lại V1–V3 hoặc sửa checksum.
Khởi động backend áp dụng các migration còn thiếu qua Flyway. API review mới tạo
snapshot schema 2; GET/PUT hành lý đã hỗ trợ lựa chọn mua thêm theo người/chiều.
Chính sách đã được tham chiếu cần giữ nguyên nội dung; khi thay cấu hình tạo version mới,
kiểm tra khoảng hiệu lực và gán policy tương ứng bằng backend/quy trình quản trị.

`CheckedBaggageMigrationIntegrationTest` tạo database tạm tên
`skygo_baggage_migration_test_<uuid>` trên server kiểm thử, chạy V1–V3, nạp dữ liệu
lịch sử rồi nâng lên V4 để kiểm tra dữ liệu và ràng buộc. Tài khoản kiểm thử cần quyền
CREATE/DROP database tạm; test chỉ xóa đúng database tên UUID do nó tự tạo.
Các integration test khác tiếp tục dùng `SKYGO_TEST_DB_URL` và fixture riêng.

Flyway V5 thêm `booking_review.review_version INT UNSIGNED NOT NULL DEFAULT 1`
và CHECK `review_version >= 1`. Đây là version nội dung review, độc lập với
`snapshot_schema_version` là version định dạng JSON mã hóa. Mọi review hiện có
nhận giá trị 1, giữ nguyên ID, ciphertext, schema version, trạng thái, hạn và timestamp.
Không thêm bảng hoặc trigger, không tăng version khi chỉ đọc dữ liệu.

Sau V5, API PUT hành lý kiểm tra `expectedVersion`, cập nhật trên cùng review và
tăng version khi dữ liệu/giá thực sự thay đổi. API POST tạo review ban đầu version 1;
GET review và GET baggage-options trả version hiện tại. POST xác nhận lại hành khách
vẫn tạo review mới và vô hiệu bản trước; đổi chuyến/số khách cũng vô hiệu review.
Review đã dùng để tạo đơn sẽ không được sửa; booking giữ dữ liệu đã chốt.

`BookingReviewVersionMigrationIntegrationTest` tạo database riêng
`skygo_review_version_test_<uuid>`, nâng V4 lên V5 và kiểm tra giữ nguyên dữ liệu,
giá trị mặc định, ràng buộc version, cập nhật có điều kiện và rollback. Tài khoản
test cần quyền CREATE/DROP database tạm. Kiểm thử SQL này không thay thế kiểm thử
API cập nhật sẽ bổ sung ở bước sau.

Khi thay schema tiếp, thêm migration mới (V6...); giữ nguyên migration đã áp dụng.
`clean` của Flyway được tắt. Fixture `database/home-demo-seed.sql` yêu cầu schema V4.

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
| POST /api/v1/seat-holds | Giữ chuyến/hạng chiều đi trong 15 phút; cần Bearer token |
| GET /api/v1/seat-holds/current | Lượt giữ chỗ còn hiệu lực của tài khoản, hoặc data: null |
| PUT /api/v1/seat-holds/{seatHoldId}/legs/{direction} | Thêm chiều về hoặc đổi chuyến/hạng trước tạo đơn |
| PUT /api/v1/seat-holds/{seatHoldId}/passenger-counts | Đổi số khách và điều chỉnh ghế ở tất cả các chiều |
| POST /api/v1/bookings | Tạo đơn và payment PENDING từ review mới nhất; cần Idempotency-Key |
| POST /api/v1/bookings/{bookingId}/open-for-payment | Mở chi tiết đơn; dùng lại hold còn hạn hoặc kiểm tra và giữ lại chỗ trước khi trả dữ liệu |
| POST /api/v1/bookings/{bookingId}/resume-payment | Chủ động giữ lại chỗ cho đơn hết hạn; giữ nguyên đơn và giá |
| POST /api/v1/booking-quotes | Tổng tiền nhóm khách và toàn bộ hành trình trên hold; cần đăng nhập |
| POST /api/v1/booking-reviews | Kiểm tra hành khách/liên hệ, tạo review version 1 và trả đủ dữ liệu review; cần đăng nhập |
| GET /api/v1/booking-reviews/{reviewId} | Đọc toàn bộ review hiện tại của tài khoản/phiên |
| GET /api/v1/booking-reviews/{reviewId}/baggage-options | Dữ liệu màn hành lý và version hiện tại |
| PUT /api/v1/booking-reviews/{reviewId}/baggage | Thay thế lựa chọn hành lý trên cùng review, kiểm tra expectedVersion |

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

### API giữ chỗ và tiếp tục thanh toán

Mọi API dưới đây cần `Authorization: Bearer <accessToken>`. Chủ tài khoản lấy từ token.
Sử dụng `flightId` và `cabinClass` từ kết quả tìm kiếm; backend đọc lại chuyến/hạng,
trạng thái bán và tồn ghế. Một tài khoản chỉ có một hold ACTIVE chưa hết hạn.

Tạo hold: `POST /api/v1/seat-holds`, trả **201 Created**:

```json
{
  "tripType": "ROUND_TRIP",
  "passengerCounts": {"adults": 1, "children": 1, "infants": 0},
  "selection": {
    "direction": "OUTBOUND",
    "flightId": "flight-public-id-from-search",
    "cabinClass": "ECONOMY"
  }
}
```

`tripType` là `ONE_WAY` hoặc `ROUND_TRIP`. Tạo mới bắt đầu bằng `OUTBOUND`.
Giữ `adults + children` ghế; em bé không chiếm ghế. Hold có hiệu lực khi giờ server
**nhỏ hơn** `expiresAt`, hết hạn sau đúng 15 phút từ lần giữ đầu tiên.
Không yêu cầu số ghế còn bán phải đủ thêm lần nữa để đọc/tiếp tục lựa chọn đã giữ.

`GET /api/v1/seat-holds/current` trả 200, `data: null` khi không có hold còn hiệu lực,
hoặc object như sau (giá trị chỉ minh họa):

```json
{
  "data": {
    "seatHoldId": "hold-public-id",
    "status": "ACTIVE",
    "tripType": "ROUND_TRIP",
    "passengerCounts": {"adults": 1, "children": 1, "infants": 0},
    "expiresAt": "2026-11-18T03:15:00Z",
    "legs": [{
      "direction": "OUTBOUND",
      "flightId": "flight-public-id-from-search",
      "cabinClass": "ECONOMY",
      "origin": {"code": "SGN", "city": "TP. Hồ Chí Minh"},
      "destination": {"code": "HAN", "city": "Hà Nội"},
      "departureAt": "2026-11-18T10:30:00+07:00",
      "arrivalAt": "2026-11-18T12:35:00+07:00"
    }],
    "bookingId": null
  }
}
```

Tạo/sửa hold cũng trả schema này. UI dùng thời hạn để đếm ngược và thông tin chiều
bay để khôi phục màn hình; server vẫn là nơi quyết định hold còn hiệu lực.
Hold không cố định tổng giá đơn. Giá và quyền lợi được kiểm tra ở bước review/tạo đơn.

Thêm hoặc đổi chiều bay: `PUT /api/v1/seat-holds/{seatHoldId}/legs/RETURN` (hoặc
`OUTBOUND`), trả 200:

```json
{"flightId": "return-flight-public-id-from-search", "cabinClass": "BUSINESS"}
```

Chiều về chỉ dùng cho khứ hồi, đảo tuyến chiều đi, ngày khởi hành địa phương sau
ngày chiều đi. Hai chiều có thể khác hạng. Gọi lại cùng lựa chọn không trừ ghế thêm;
đổi lựa chọn trả ghế cũ và giữ ghế mới trong một transaction, thiếu chỗ thì giữ nguyên
lựa chọn cũ. Trước khi đổi chiều đi khi đã có chiều về, cặp mới vẫn phải hợp lệ.

Sửa số khách: `PUT /api/v1/seat-holds/{seatHoldId}/passenger-counts`, trả 200:

```json
{"adults": 2, "children": 0, "infants": 1}
```

Các số phải là số nguyên JSON, người lớn >=1, trẻ em/em bé >=0. Điều chỉnh ghế cho
tất cả các chiều trong một transaction, thiếu chỗ ở một chiều thì rollback toàn bộ.
Sửa lựa chọn/số khách không gia hạn và vô hiệu review chưa dùng. Hold đã gắn đơn
không sửa được. Đăng xuất không hủy hold; người dùng có thể đăng nhập lại và GET current.

Tiếp tục đơn hết hạn: `POST /api/v1/bookings/{bookingId}/resume-payment`, không có body,
chỉ gọi khi người dùng nhấn **Tiếp tục thanh toán**. Trả 200:

```json
{
  "data": {
    "bookingId": "booking-public-id",
    "bookingStatus": "PENDING_PAYMENT",
    "paymentStatus": "PENDING",
    "seatHoldId": "new-hold-public-id",
    "holdExpiresAt": "2026-11-18T03:35:00Z",
    "totalAmount": 2200000,
    "currency": "VND"
  }
}
```

Backend kiểm tra lại chuyến/hạng còn bán, chưa khởi hành và đủ chỗ ở mọi chiều,
giữ lại nguyên tử, tạo hold mới 15 phút liên kết cùng đơn. Giữ nguyên ID/mã đơn,
review, hành khách, hành trình, snapshot giá và payment hiện có. Nếu cùng đơn đã có
hold còn hạn thì trả hold đó, không gia hạn. Nếu thiếu chỗ ở bất kỳ chiều nào,
không trừ ghế ở chiều nào và giữ đơn EXPIRED; UI thông báo để khách chọn chuyến khác.
API này hoạt động với đơn đã lưu, bao gồm đơn tạo qua `POST /api/v1/bookings`.

Các mã lỗi:

| HTTP | Code | UI xử lý |
| --- | --- | --- |
| 401 | UNAUTHENTICATED | Đăng nhập |
| 422 | VALIDATION_ERROR | Sửa đầu vào |
| 404 | SEAT_HOLD_NOT_FOUND / BOOKING_NOT_FOUND | Không tìm thấy hoặc không thuộc tài khoản |
| 409 | ACTIVE_HOLD_EXISTS | Đọc GET current; error.activeSeatHoldId và error.bookingId chỉ lượt đang giữ |
| 409 | INSUFFICIENT_CAPACITY | Không đủ ghế, chọn chuyến/hạng khác |
| 409 | FLIGHT_UNAVAILABLE | Chuyến/hạng không còn bán hoặc đã khởi hành |
| 409 | SEAT_HOLD_EXPIRED | Chọn lại chuyến; ghế hết hạn đã được trả |
| 409 | HOLD_ATTACHED_TO_BOOKING | Tiếp tục đơn đã tạo, không sửa hold |
| 409 | BOOKING_ALREADY_PAID | Đơn đã thanh toán |
| 409 | BOOKING_NOT_RESUMABLE / HOLD_MISMATCH | Đơn không có trạng thái/hành trình hợp lệ để giữ lại |

Khi hết hạn, hold chuyển EXPIRED, trả ghế từng chiều đúng một lần và vô hiệu review
chưa dùng. Đơn/payment đang chờ chuyển EXPIRED. Các hold CONSUMED không trả ghế.
Thu hồi chạy trước khi tìm/giữ chỗ và qua tác vụ nền; không phụ thuộc hoàn toàn vào
tác vụ nền. Mỗi hold được thu hồi trong transaction riêng theo thứ tự khóa tài khoản,
hold, booking/payment rồi fare tăng dần. Mutation cũng dùng cùng thứ tự khóa và các
cập nhật tồn có điều kiện để không bán vượt chỗ. Expiry vẫn được commit khi request
trả lỗi hết hạn. Tìm kiếm mở transaction đọc tồn sau khi thu hồi hoàn tất.

Cấu hình `skygo.seat-hold.cleanup-enabled` (mặc định true), `cleanup-delay-ms`
(30000) và `cleanup-batch-size` (100) đặt trong application.properties. Mốc 15 phút
là quy tắc nghiệp vụ cố định. Kiểm thử giữ chỗ/resume/concurrency dùng MariaDB test
qua `SKYGO_TEST_DB_URL`, tắt scheduler và clock cố định. Fixture được commit để kiểm
tra các transaction đồng thời, rồi xóa trong teardown; chỉ chạy trên database test riêng.

### API giá và thông tin hành khách

Hai API yêu cầu Bearer token. Mỗi lần gọi kiểm tra hold thuộc tài khoản, ACTIVE,
chưa hết hạn, chưa gắn đơn và đủ các chiều. Request phải khớp loại hành trình,
số khách và lựa chọn đang lưu trong hold; không nhận giá do frontend tính.
Không trừ ghế thêm, không yêu cầu còn đủ ghế chưa bán cho cùng khách và không gia hạn.

Khi mở màn hành khách, gọi `POST /api/v1/booking-quotes`:

```json
{
  "seatHoldId": "hold-public-id",
  "tripType": "ONE_WAY",
  "passengerCounts": {"adults": 1, "children": 0, "infants": 0},
  "selections": [{"direction": "OUTBOUND", "flightId": "flight-public-id", "cabinClass": "ECONOMY"}]
}
```

Trả 200 với dữ liệu vừa đủ cho tổng tiền cuối màn:

```json
{"data": {"totalAmount": 3564000, "currency": "VND", "expiresAt": "2026-11-18T03:15:00Z"}}
```

Giá là **toàn bộ hành khách trên tất cả các chiều**, gồm VAT; khác giá một người lớn
trên thẻ tìm kiếm. Backend kiểm tra giá cơ sở từng hạng theo policy/cự ly đã lưu và
hai hạng của cùng chuyến phải nhất quán. Người lớn trả 100%; trẻ em/em bé lấy hệ số
từ `passenger_fare_rule` theo phạm vi tuyến. Làm tròn HALF_UP giá đơn vị đến đồng rồi
nhân số khách, cộng mọi dòng và tính VAT 10% một lần trên tổng tiền vé. Thiếu quy tắc
trẻ em nội địa ngoài Việt Nam trả 422 `UNSUPPORTED_FARE_RULE`; thiếu policy khác hoặc
giá/cự ly/quyền lợi không nhất quán trả 409 `PRICING_UNAVAILABLE`. Không coi cấu hình
thiếu là giá 0. Hành lý và suất ăn đã nằm trong giá, không có bước mua thêm hành lý.
Quote không lưu database hoặc tạo một thời hạn mới; `expiresAt` là hạn hold.

Khi người dùng nhấn Xác nhận, gọi `POST /api/v1/booking-reviews` với tiêu chí giống
quote, thêm hành khách và thông tin liên hệ:

```json
{
  "seatHoldId": "hold-public-id",
  "tripType": "ONE_WAY",
  "passengerCounts": {"adults": 1, "children": 0, "infants": 0},
  "selections": [{"direction": "OUTBOUND", "flightId": "flight-public-id", "cabinClass": "ECONOMY"}],
  "passengers": [{
    "clientPassengerId": "p1",
    "passengerType": "ADULT",
    "title": "MR",
    "givenNames": "Pedro",
    "surname": "Moreno",
    "dateOfBirth": "1990-03-12",
    "gender": "MALE",
    "document": {
      "documentType": "PASSPORT",
      "number": "ES1234567",
      "issuingCountryCode": "ES",
      "nationalityCountryCode": "ES",
      "expiresOn": "2030-03-12"
    }
  }],
  "contact": {"email": "pedro.moreno@example.com", "phoneNumber": "090 123 4567"}
}
```

Các ID, ngày và tiền trên chỉ minh họa. Trả **201 Created**, `data` có `reviewId`,
`expiresAt`, `totalAmount`, `currency`, `passengers` và `contact`. Thông tin hành khách
được chuẩn hóa khoảng trắng; document chỉ có `documentType`, `maskedNumber`,
`issuingCountryCode`, `nationalityCountryCode`, `expiresOn`, không có số đầy đủ.
Ví dụ `ES1234567` trả `*****4567`; giấy tờ ngắn <=4 ký tự che toàn bộ.

Quy tắc dữ liệu:

- Danh sách và số lượng mỗi nhóm phải khớp hold; `clientPassengerId` duy nhất.
- Tuổi tính đủ năm tại ngày khởi hành chiều đi theo múi giờ sân bay đi: ADULT >=12,
  CHILD từ 2 đến dưới 12, INFANT dưới 2. Ngày sinh không sau ngày hiện tại tại sân bay đi.
- Người lớn bắt buộc `title`: MR/MRS/MS; trẻ em và em bé phải bỏ qua hoặc null.
  `gender`: MALE/FEMALE/OTHER. Họ và tên đệm/tên bắt buộc, tối đa 200 ký tự mỗi trường.
- PERSONAL_ID cần số giấy tờ và quốc gia cấp; không gửi trường quốc tịch/hạn hộ chiếu.
  PASSPORT cần thêm quốc tịch và ngày hết hạn. Quốc gia là mã ISO alpha-2 viết hoa.
  Hành trình có chiều quốc tế bắt buộc mọi hành khách dùng hộ chiếu.
- Hộ chiếu phải còn hạn đến ngày khởi hành cuối cùng, không tự áp điều kiện 6 tháng.
  Số giấy tờ là chuỗi tối đa 64 ký tự, giữ số 0 đầu và không áp regex CCCD người lớn
  cho trẻ em/em bé.
- Email liên hệ bắt buộc, đúng định dạng, tối đa 254 ký tự; số liên hệ nhận di động VN
  dạng 0... hoặc số quốc tế + với 7–15 chữ số. Khoảng trắng/dấu ngoặc/gạch nối được bỏ,
  số di động VN được chuẩn hóa +84. Liên hệ nhập một lần cho cả đơn.
- Mỗi lần xác nhận tạo review mới và vô hiệu review chưa dùng trước đó của hold,
  không sửa ciphertext cũ. Hạn review bằng hạn hold. Review gắn tài khoản và phiên
  hiện tại; đăng xuất vô hiệu review chưa dùng. Sửa hold cũng vô hiệu review chưa dùng.

Review chưa tạo booking/payment. Snapshot schema version 2 chứa toàn bộ hành trình,
hãng/sân bay/lịch bay, hành khách, giấy tờ, liên hệ, quyền lợi từng khách/từng chiều,
các dòng giá, version, chính sách/lựa chọn hành lý và VAT chung để bước tạo đơn đối chiếu/sao chép. Dữ liệu này được mã hóa
AES-256-GCM với nonce ngẫu nhiên, tag 128 bit; envelope có phiên bản và key ID.
AAD gắn review/tài khoản/phiên/hold/schema và review_version để phát hiện tráo payload. Payload lưu tại
`booking_review.snapshot_ciphertext`; response không trả ciphertext hay khóa.
Lưu hành lý cập nhật snapshot trên cùng review và tăng version khi nội dung thay đổi. UI cần hiển thị tổng tiền
review trả về trước khi tạo đơn vì backend tính lại giá tại lần xác nhận.

### Cấu hình khóa mã hóa review

Đặt `SKYGO_REVIEW_ENCRYPTION_KEY` là Base64 của **32 byte ngẫu nhiên**, và tùy chọn
`SKYGO_REVIEW_KEY_ID` (mặc định v1, 1–64 ký tự chữ/số/gạch nối/gạch dưới).
Ví dụ tạo khóa cho môi trường local trong PowerShell, không in khóa ra terminal:

```powershell
$reviewKeyBytes = [byte[]]::new(32)
$reviewRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$reviewRng.GetBytes($reviewKeyBytes)
$reviewRng.Dispose()
$env:SKYGO_REVIEW_ENCRYPTION_KEY = [Convert]::ToBase64String($reviewKeyBytes)
$env:SKYGO_REVIEW_KEY_ID = 'local-v1'
# Khởi động backend từ chính terminal này để kế thừa biến môi trường.
.\mvnw.cmd spring-boot:run
```

Giữ khóa trong cấu hình bí mật bên ngoài database/repository và dùng lại khi restart;
không tạo khóa mới mỗi lần chạy nếu còn review cần dùng. Hiện hỗ trợ một khóa/key ID
đang cấu hình; chỉ thay sau khi review cũ đã dùng/hết hạn, hoặc triển khai key ring
trước khi xoay khóa cần giữ khả năng đọc review cũ.

Chưa có/sai cấu hình khóa: review trả **503 REVIEW_ENCRYPTION_UNAVAILABLE**, không lưu
review không mã hóa; quote và các chức năng khác vẫn hoạt động. Dữ liệu mã hóa sai
hoặc context không khớp bị từ chối với `REVIEW_INVALID`. Không có endpoint công khai
giải mã; bước tạo đơn phải kiểm tra quyền/phiên/hạn trước khi đọc snapshot.

Lỗi bổ sung của hai API: 401 UNAUTHENTICATED, 404 SEAT_HOLD_NOT_FOUND,
409 HOLD_MISMATCH / SEAT_HOLD_EXPIRED / HOLD_ATTACHED_TO_BOOKING / FLIGHT_UNAVAILABLE,
422 VALIDATION_ERROR / PASSENGER_AGE_MISMATCH. Chi tiết lỗi không chứa dữ liệu đã nhập.
Kiểm thử integration dùng khóa riêng cố định chỉ trong test, không dùng khóa này để
chạy ứng dụng. Các bài test bao phủ giá, policy thiếu, quyền sở hữu, hold hết hạn,
ghế cuối đã giữ, validate hành khách, logout, đọc dữ liệu không sửa ciphertext và phát hiện sửa/tráo payload.

## Màn thêm hành lý

Khi nhấn **Thêm hành lý** từ review, gọi:

```http
GET /api/v1/booking-reviews/{reviewId}/baggage-options
Authorization: Bearer <token>
```

Dùng `reviewId` vừa nhận từ `POST /api/v1/booking-reviews` và token của **cùng phiên
đăng nhập đã tạo review**. Không có request body. Trả **200 OK**, `Cache-Control: no-store`.
Response không lặp lại `reviewId`, trả `version` và giữ `passengerCounts` để hiển thị tóm tắt.
Ví dụ một người lớn, một chiều; mã sân bay/ngày/hạn dưới đây chỉ minh họa:

```json
{
  "data": {
    "version": 1,
    "expiresAt": "2026-11-18T00:15:00Z",
    "currency": "VND",
    "passengerCounts": {"adults": 1, "children": 0, "infants": 0},
    "passengers": [
      {"clientPassengerId": "p1", "fullName": "Nguyen An", "passengerType": "ADULT"}
    ],
    "legs": [{
      "direction": "OUTBOUND",
      "originCode": "SGN",
      "destinationCode": "HAN",
      "departureDate": "2026-11-19",
      "cabinClass": "ECONOMY",
      "checkedBaggagePolicy": {
        "pieceMaxKg": 23,
        "maxLinearDimensionsCm": 158,
        "maxExtraPiecesPerPassenger": 2,
        "extraPiecePriceExclVat": 325000
      },
      "passengerBaggage": [{
        "clientPassengerId": "p1",
        "included": {
          "carryOnMaxKg": 7,
          "checkedPieces": 1,
          "strollerPieces": 0,
          "strollerMaxKg": 0
        },
        "canPurchaseExtra": true,
        "selectedExtraPieces": 0,
        "extraAmountExclVat": 0
      }]
    }],
    "totalBaggageAmountExclVat": 0
  }
}
```

- Danh sách `passengers` có đầy đủ ADULT/CHILD/INFANT; `fullName` ghép họ rồi tên đệm/tên
  đã chuẩn hóa. Không trả ngày sinh, giới tính, giấy tờ, email, số điện thoại, ciphertext,
  ID nội bộ hoặc các dòng giá vé cho màn này.
- `legs` sắp OUTBOUND rồi RETURN. Mỗi chiều có hạng vé, ngày bay theo múi giờ sân bay
  đi và miễn cước riêng cho từng người. Hành trình/miễn cước lấy từ snapshot review đã
  xác nhận, không thay bằng danh mục hiện tại hoặc tính lại tiền vé khi mở màn hình.
- Chính sách ký gửi lấy theo FK từ `fare_benefit_policy` tới `checked_baggage_policy`
  của từng fare/nhóm khách được chọn. Kiểm tra hiệu lực, tiền tệ, giới hạn và sự thống
  nhất chính sách giữa các nhóm trên một chiều; thiếu/sai cấu hình trả lỗi, không ngầm
  dùng giá 0 hoặc mặc định. GET không cập nhật snapshot hay chốt giá mua thêm.
- Một kiện ký gửi (miễn cước hoặc mua thêm) cùng giới hạn 23 kg, tổng dài + rộng + cao
  158 cm. ADULT/CHILD được mua thêm tối đa 2 kiện/người/chiều ngoài miễn cước;
  INFANT có `canPurchaseExtra=false`, không hiển thị bộ chọn mua riêng.
- Mọi trường có hậu tố `ExclVat` là **chưa VAT**. Frontend tính tạm thành tiền bằng
  số kiện × đơn giá, cộng các người/chiều. VAT 10% tính chung với tiền vé khi lưu
  lựa chọn và cập nhật review bằng PUT. UI ghi rõ "Chưa gồm VAT".
- Review mới bắt đầu với số kiện/thành tiền mua thêm bằng 0. Sau PUT, mở lại màn
  hành lý trả đúng số kiện, thành tiền và version đã lưu. Chính sách thay đổi so với
  snapshot trả REVIEW_OUTDATED khi đọc options; PUT tính lại theo cấu hình hợp lệ
  hiện tại và trả review mới nhất để người dùng xem trước khi tiếp tục.
- Chỉ cho đọc review còn hiệu lực của đúng tài khoản/phiên, chưa dùng và hold còn
  ACTIVE chưa gắn đơn. GET không tạo/vô hiệu review mới, không giữ thêm ghế, không
  gia hạn. Khi hold thực sự hết hạn, cơ chế expiry chung vẫn trả ghế đúng một lần.

Lỗi dùng envelope booking hiện có, không trả kèm dữ liệu hành khách:

| HTTP | `error.code` | Trường hợp |
|---|---|---|
| 401 | `UNAUTHENTICATED` | Thiếu token, phiên đã hết hạn/đăng xuất |
| 404 | `REVIEW_NOT_FOUND` | ID không tồn tại hoặc review thuộc tài khoản/phiên khác |
| 409 | `SEAT_HOLD_EXPIRED` | Giữ chỗ đã hết hạn/không còn ACTIVE |
| 409 | `REVIEW_EXPIRED` | Review hết hạn trong khi hold còn ACTIVE |
| 409 | `REVIEW_ALREADY_USED` | Review đã dùng hoặc hold đã gắn đơn |
| 409 | `REVIEW_INVALID` | Review bị thay thế/vô hiệu, payload bị xóa/sai xác thực, schema chưa hỗ trợ hoặc snapshot không khớp |
| 409 | `PRICING_UNAVAILABLE` | Chính sách ký gửi thiếu, hết/chưa hiệu lực hoặc không nhất quán |
| 503 | `REVIEW_ENCRYPTION_UNAVAILABLE` | Thiếu/sai cấu hình khóa mã hóa review |

`BookingBaggageIntegrationTest` dùng MariaDB kiểm thử, bao phủ nhiều nhóm khách,
khứ hồi khác hạng, ngày bay địa phương, dữ liệu snapshot bất biến, quyền/phiên,
review không còn dùng được, policy lỗi và expiry trong lúc đọc policy. Không cần
migration mới ngoài V4 cho API GET này.

## Lưu hành lý và tải lại review

```http
PUT /api/v1/booking-reviews/{reviewId}/baggage
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "expectedVersion": 1,
  "selections": [
    {"clientPassengerId": "p1", "direction": "OUTBOUND", "extraPieces": 2}
  ]
}
```

Gửi đúng một lựa chọn cho **mỗi ADULT/CHILD trên mỗi chiều**, kể cả số kiện 0;
không gửi lựa chọn cho INFANT. Khứ hồi phải gửi cả OUTBOUND và RETURN. Các số
phải là số nguyên JSON; expectedVersion từ 1 đến 4294967295, extraPieces từ 0–2.
Thiếu/trùng/sai người hoặc chiều, mua cho em bé hoặc vượt giới hạn trả 422 VALIDATION_ERROR.
Client không gửi giá, VAT hoặc tổng thanh toán để backend sử dụng.

Backend kiểm tra quyền/phiên/hạn/chưa dùng và version, đối chiếu giữ chỗ,
tính lại vé/quyền lợi theo catalog, kiểm tra lại hành khách/giấy tờ rồi tính hành lý
theo policy hiện hành. Mỗi đơn có tiền vé chưa VAT và tiền hành lý chưa VAT:

```text
extraBaggageAmount = tổng(extraPieces × extraPiecePriceExclVat) của mọi người/chiều
vatAmount = round_half_up((fareSubtotal + extraBaggageAmount) × vatRate)
totalAmount = fareSubtotal + extraBaggageAmount + vatAmount
```

Không dùng giá đã gồm VAT trên màn tìm chuyến làm fareSubtotal. Với vé chưa VAT
561000 và 2 kiện × 325000, VAT là 121100 và tổng 1332100 VND. Giá và hạn trên ảnh
mock không thay thế policy backend: Phổ thông xách tay 7 kg, Thương gia 15 kg;
kiện ký gửi chung 23 kg/158 cm và miễn cước tương ứng 1/2 kiện cho khách có ghế.

Thành công **200 OK**, giữ reviewId, createdAt và expiresAt. Nếu nội dung/giá
thực sự thay đổi, cập nhật ciphertext, snapshot_schema_version=2, review_version+1
và updated_at_utc trong cùng transaction. Lưu lại dữ liệu không đổi với version
hiện tại giữ nguyên cả version lẫn ciphertext. Không cộng dồn qua các lần lưu,
không tạo bản ghi review mới, không giữ thêm ghế, không gia hạn hoặc tạo booking/payment.
Nếu giữ chỗ hết hạn khi đang xử lý, chỉ commit việc hết hạn/trả ghế; không lưu hành lý.
Lỗi mã hóa/ghi dữ liệu rollback cả payload và version.

POST tạo review, GET review và PUT lưu hành lý dùng chung cấu trúc data:

- reviewId, version, expiresAt, currency, totalAmount, tripType và passengerCounts.
- passengers: toàn bộ hành khách, giấy tờ đã che; contact: email/điện thoại đã chuẩn hóa.
- legs: chiều, chuyến/hãng/hạng vé, sân bay (code/name/city/countryCode), giờ đi/đến
  có offset theo sân bay, durationMinutes, checkedBaggagePolicy và passengerBaggage.
- passengerBaggage có clientPassengerId, included (xách tay/kiện miễn cước/xe đẩy),
  canPurchaseExtra, selectedExtraPieces và extraAmountExclVat. Unit price và giới hạn
  kiện dùng chung trong checkedBaggagePolicy; frontend nối tên bằng clientPassengerId.
- price có fareSubtotal, extraBaggageAmount, vatRate, vatAmount và totalAmount.
  Root totalAmount bằng price.totalAmount để giữ tương thích client hiện có.

Frontend nên hiển thị ba dòng: **Vé chưa VAT**, **Hành lý mua thêm chưa VAT**,
**VAT**, rồi tổng thanh toán; không cộng hành lý chưa VAT vào dòng vé đã gồm VAT.
Mở màn hành lý giữ lựa chọn tạm cục bộ, quay lại chưa lưu thì bỏ thay đổi.
Sau PUT thành công, thay review bằng data trả về và quay về màn Kiểm tra & thanh toán.

```http
GET /api/v1/booking-reviews/{reviewId}
Authorization: Bearer <token>
```

GET không tính lại giá hoặc sửa version/ciphertext. Với version cũ, PUT trả
409 REVIEW_VERSION_CONFLICT, không kèm dữ liệu hành khách; frontend gọi GET trên
để tải lại và cho người dùng xác nhận, không tự lấy version mới để gửi đè.
Nếu mất phản hồi sau khi PUT đã commit, gửi lại expectedVersion cũ cũng nhận 409;
đọc lại review sẽ thấy kết quả đã lưu. Hai cập nhật cùng expectedVersion chỉ một
yêu cầu được ghi; kiểm tra bằng WHERE review_version cùng quyền/phiên/trạng thái/hạn.

Các lỗi quyền/phiên/hạn/đã dùng/cấu hình/khóa mã hóa giống GET baggage-options.
GET/PUT đều trả Cache-Control: no-store. API tạo đơn nhận expectedVersion,
kiểm tra và sao chép snapshot trong cùng transaction rồi đánh dấu review đã dùng;
không cho sửa review sau khi tạo đơn.

Schema JSON 2 chứa version, snapshot policy và lựa chọn/giá theo người/chiều.
Envelope AES-GCM 2 gắn review_version vào AAD, không thay key/key ID. Đọc được
review schema/envelope 1 ở review_version=1 mà không ghi lại hoặc gia hạn; số kiện
mua thêm cũ là 0. Chỉ nâng thành schema/envelope 2 khi thực sự thay đổi dữ liệu.
Tráo payload khác review/session/hold/schema/version bị từ chối REVIEW_INVALID.
Giữ nguyên khóa mã hóa hiện có khi restart; việc nâng schema không phải xoay khóa.

BookingBaggageSaveIntegrationTest bao phủ lưu/xóa/không đổi, nhiều người/chiều,
đọc lại, xung đột đồng thời, quyền/phiên, kiểm tra input, expiry, rollback sau SQL,
khả năng đọc/nâng review cũ và chống replay payload. BookingBaggagePricingServiceTest
kiểm tra VAT chung, làm tròn và tràn số. Không cần migration sau V5 cho API này.

## Tạo booking từ review

`POST /api/v1/bookings` cần Bearer token của đúng phiên tạo review và header
`Idempotency-Key` gồm 1–128 ký tự ASCII không có khoảng trắng. Body:

```json
{"reviewId":"review-public-id","expectedVersion":2}
```

`expectedVersion` lấy từ POST/GET review hoặc PUT hành lý gần nhất. Không gửi lại
giá, hành khách hay lựa chọn hành lý. Backend kiểm tra khóa chống gửi lặp trước:
cùng tài khoản, khóa và body trả cùng đơn với HTTP 200, kể cả khi review đã dùng;
cùng khóa nhưng body khác trả 409 `IDEMPOTENCY_KEY_REUSED`. Review đã dùng với
khóa mới trả 409 `REVIEW_ALREADY_USED`.

Yêu cầu mới kiểm tra review đúng tài khoản/phiên, version, hạn và hold ACTIVE đủ
các chiều. Backend tính lại chuyến, quyền lợi, hành lý và VAT rồi so sánh với
snapshot review. Thay đổi trả 409 `REVIEW_OUTDATED`; version cũ trả 409
`REVIEW_VERSION_CONFLICT`. Trong một transaction, backend sao chép snapshot vào
`booking`, `booking_leg`, `booking_passenger`, `booking_fare_line` và
`booking_passenger_benefit`, tạo một `payment` QR_SIMULATION/PENDING, gắn hold với
booking và đặt `booking_review.used_at_utc`. Không trừ ghế hoặc gia hạn hold.
Lần đầu thành công trả 201, `Cache-Control: no-store`, gồm bookingId/bookingCode,
trạng thái PENDING_PAYMENT, hạn hold, tổng tiền, passengerId và payment PENDING.
Chưa tạo ticket hoặc invoice ở bước này.

`POST /api/v1/bookings/{bookingId}/open-for-payment` dùng Bearer token của chủ đơn,
không có body và trả `Cache-Control: no-store`. App gọi khi người dùng mở lại
đơn từ lịch sử. Nếu hold của chính đơn còn hạn, API trả nguyên hạn cũ và không
trừ chỗ lần nữa. Nếu đơn `EXPIRED` chưa có payment attempt PayOS, backend kiểm
tra toàn bộ chiều bay và hạng vé, giữ lại chỗ 15 phút trong một transaction rồi
trả chi tiết đơn. Không đủ chỗ trả 409 `INSUFFICIENT_CAPACITY` mà không giữ
một phần hành trình. Đơn đã `PAID` hoặc `RECONCILIATION_REQUIRED` vẫn có thể
đọc chi tiết nhưng không tạo hold mới. Link PayOS cũ chưa thể xác minh được trả
409 `PAYMENT_STATUS_UNVERIFIED`; phần đối soát với PayOS sẽ được nối khi tích
hợp API tạo link/webhook. API không tạo QR.

`data` gồm bookingId, bookingCode, bookingStatus, createdAt, tripType,
passengerCounts, contact, seatHoldId/holdExpiresAt hiện hành (nullable), price
gồm fareSubtotal/extraBaggageAmount/vatRate/vatAmount/totalAmount, currency,
payment, legs (snapshot chuyến/sân bay/giờ địa phương/hạng vé/giá từng nhóm)
và passengers (thông tin đã lưu, số giấy tờ che, quyền lợi và hành lý từng
chiều, ticketNumber nếu đã cấp). Không trả số giấy tờ đầy đủ hoặc giá chuyến
bay hiện tại để thay cho snapshot đã chốt.

Số giấy tờ trong booking được mã hóa AES-256-GCM bằng khóa riêng, gắn với
bookingId và passengerId. Đặt `SKYGO_BOOKING_DOCUMENT_ENCRYPTION_KEY` là Base64 của
32 byte ngẫu nhiên và tùy chọn `SKYGO_BOOKING_DOCUMENT_KEY_ID` (mặc định `v1`).
Khóa này phải được giữ lâu dài để đọc các booking đã lưu; không dùng khóa review
ngắn hạn thay thế. Thiếu hoặc sai khóa trả 503 `DOCUMENT_ENCRYPTION_UNAVAILABLE`
và transaction tạo đơn rollback. Không ghi khóa thật vào repository. Ví dụ tạo
khóa local trong PowerShell mà không in ra terminal:

```powershell
$documentKeyBytes = [byte[]]::new(32)
$documentRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$documentRng.GetBytes($documentKeyBytes)
$documentRng.Dispose()
$env:SKYGO_BOOKING_DOCUMENT_ENCRYPTION_KEY = [Convert]::ToBase64String($documentKeyBytes)
$env:SKYGO_BOOKING_DOCUMENT_KEY_ID = 'local-v1'
```

Không đổi khóa khi còn booking cần đọc; cần key ring trước khi xoay khóa. Các
integration test tạo booking dùng MariaDB test qua `SKYGO_TEST_DB_URL`, không dùng
database chứa dữ liệu cần giữ. Bảng và cột đã có từ Flyway V1–V5; API này không
cần migration mới.
