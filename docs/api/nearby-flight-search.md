# API tìm chuyến trong 7 ngày lân cận

API trả trang đầu của bảy ngày liên tiếp, lấy ngày tìm kiếm làm trung tâm (±3 ngày).
Mỗi ngày có tối đa 10 chuyến và thông tin phân trang riêng. API không yêu cầu đăng
nhập, không tạo booking hoặc giữ chỗ mới. Chưa nối API này vào các tab frontend.

## Request ban đầu

`POST /api/v1/flights/search/nearby-days`

```json
{
  "tripType": "ONE_WAY",
  "origin": "SGN",
  "destination": "HAN",
  "departureDate": "2026-11-21",
  "passengerCounts": {"adults": 1, "children": 0, "infants": 0},
  "direction": "OUTBOUND"
}
```

- `OUTBOUND`: ngày trung tâm là `departureDate`.
- `RETURN`: ngày trung tâm là `returnDate`; giữ nguyên sân bay và tiêu chí của
  hành trình gốc, backend tự đảo tuyến. Khứ hồi bắt buộc có `returnDate` sau `departureDate`.
- Bắt buộc người lớn >= 1, trẻ em/em bé >= 0, dùng số nguyên JSON; em bé không chiếm ghế.
- Ngày đi gốc không trước ngày hiện tại tại sân bay đi gốc. Các sân bay phải đang
  hoạt động và khác nhau. Lỗi trả 422 theo envelope `error` hiện tại.
- Phạm vi ±3, trang 0 và kích thước 10 cố định ở backend; request không có tham số
  phân trang/phạm vi. Trường lạ được bỏ qua theo cấu hình JSON hiện tại, không làm
  thay đổi các giới hạn này.

## Response

Response bọc trong `data`, gồm `centerDate`, `startDate`, `endDate`, `direction`,
`days`. Với ngày trung tâm 21/11, range luôn là 18–24/11, theo thứ tự tăng dần.
`days` luôn có đủ 7 phần tử. Ví dụ một phần tử ngày không có chuyến:

```json
{
  "date": "2026-11-21",
  "selectable": true,
  "disabledReason": null,
  "flights": [],
  "pagination": {
    "page": 0,
    "size": 10,
    "totalElements": 0,
    "totalPages": 0,
    "hasMore": false
  }
}
```

`flights` dùng nguyên cấu trúc phần tử `data` của `POST /api/v1/flights/search`:
flightId, flightNumber, airline, origin, destination, departureAt, arrivalAt,
durationMinutes, fares. Mỗi chuyến chứa các hạng vé đang bán, còn đủ chỗ.
Giá mỗi hạng là **một người lớn, một chiều, đã VAT**, không phải giá cả nhóm.

- `totalElements`: tổng số chuyến hợp lệ của riêng ngày đó, không đếm hạng vé.
- `totalPages = ceil(totalElements / 10)`; ngày rỗng có 0 trang.
- `hasMore = totalElements > 10` cho trang đầu. Đúng 10 chuyến thì false.
- Sắp theo giờ khởi hành rồi flightId, cùng thứ tự API tải thêm. Hai hạng vé không
  bị tách qua trang. Không trả số ghế nội bộ.

Ngày không hợp lệ vẫn có phần tử trong dải, với `selectable: false`, flights rỗng,
pagination bằng 0 và một trong các `disabledReason`:

| Mã | Ý nghĩa |
|---|---|
| `PAST_DATE` | Ngày đã qua tại sân bay khởi hành của chiều đang tìm |
| `RETURN_NOT_AFTER_DEPARTURE` | Ngày về không sau ngày đi gốc |
| `DEPARTURE_NOT_BEFORE_RETURN` | Ngày đi không trước ngày về đã chọn |

Ưu tiên PAST_DATE nếu ngày vi phạm nhiều điều kiện. Ngày hợp lệ nhưng không có
chuyến có selectable=true, disabledReason=null. Không tự dịch dải 7 ngày để bù
ngày bị vô hiệu hóa. Khoảng ngày vượt khả năng lưu DATETIME của MariaDB trả 422.

## Tải thêm một ngày

Dùng API cũ, trang bắt đầu từ 0. Trang đầu đã có từ batch; lần tải thêm đầu tiên
gửi `page: 1`, `size: 10` và ngày của tab đang xem:

```http
POST /api/v1/flights/search
Content-Type: application/json
```

```json
{
  "tripType": "ONE_WAY",
  "origin": "SGN",
  "destination": "HAN",
  "departureDate": "2026-11-24",
  "passengerCounts": {"adults": 1, "children": 0, "infants": 0},
  "direction": "OUTBOUND",
  "page": 1,
  "size": 10
}
```

Response giữ nguyên `{data: [...], pagination: {page, size, totalElements, totalPages}}`.
API cũ không thêm `hasMore`; frontend dùng `page + 1 < totalPages`.
Với RETURN, thay returnDate bằng ngày tab, giữ departureDate và tuyến gốc.
Với OUTBOUND khứ hồi, thay departureDate và giữ returnDate. Chỉ gọi ngày hợp lệ.

Frontend lưu dữ liệu/trang/trạng thái tải theo từng ngày và bộ tiêu chí, nối kết quả
theo flightId, tăng trang sau khi thành công, không gửi trùng khi đang tải.
Đổi tab trong range không tự tạo range mới. Giá/chỗ có thể thay đổi giữa các request;
phân trang không đóng băng tồn chỗ. Khi đặt vẫn phải kiểm tra lại ở backend.

## Truy vấn và kiểm thử

Backend tạo các ranh giới UTC cho từng ngày bằng múi giờ IANA ở Java, hỗ trợ ngày
23/25 giờ do DST và không phụ thuộc bảng múi giờ MariaDB. Một truy vấn batch dùng
COUNT/ROW_NUMBER theo ngày, lọc chỗ trước khi đếm và giới hạn trước khi JOIN hạng vé.
Không gọi lại service tìm kiếm bảy lần. Dọn hold quá hạn một lần trước transaction
đọc như API cũ; việc tìm kiếm không giữ thêm hoặc gia hạn chỗ.

Không cần migration. Kiểm thử bằng Java 21:

```powershell
.\mvnw.cmd '-Dtest=FlightSearchServiceTest,FlightSearchIntegrationTest' test
```

Test tích hợp cần SKYGO_TEST_DB_URL/USERNAME/PASSWORD trỏ đến **database kiểm thử
riêng**. Flyway tạo schema/seed; fixture test rollback sau mỗi test. Không trỏ vào
database app đang dùng.

Kết quả kiểm tra ngày 08/11/2026: 9 unit test và 8 integration test tìm kiếm đạt
trên MariaDB 11.8.6, gồm cả API cũ tải trang 1 sau batch. Đo riêng truy vấn batch
trên database tạm với 10.000 chuyến giả (100 chuyến/ngày, mỗi chuyến hai hạng):
700 chuyến khớp trong tuần, trả 70 chuyến/140 dòng hạng vé. Sau một lượt làm nóng,
10 lượt đo có trung vị khoảng 14 ms, lớn nhất khoảng 16 ms trên máy phát triển.
ANALYZE xác nhận dùng ix_flight_search và uq_flight_cabin; JOIN được điều khiển
từ chuyến đã lọc để tránh đọc toàn bộ bảng giá theo pricing_policy_id. Đây là
số đo truy vấn SQL local, không phải SLA API hoặc kiểm thử nhiều người đồng thời.
