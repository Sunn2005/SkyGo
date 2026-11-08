# API đổi lựa chọn chuyến bay trên lượt giữ chỗ

## Endpoint

`PUT /api/v1/seat-holds/{seatHoldId}/selection`

Headers:

```http
Authorization: Bearer {{accessToken}}
Content-Type: application/json
```

Body mẫu:

```json
{
  "expectedVersion": 1,
  "tripType": "ONE_WAY",
  "passengerCounts": {
    "adults": 1,
    "children": 0,
    "infants": 0
  },
  "selections": [
    {
      "direction": "OUTBOUND",
      "flightId": "{{newFlightId}}",
      "cabinClass": "BUSINESS"
    }
  ]
}
```

Lấy `flightId` từ kết quả tìm kiếm. `expectedVersion` là `data.version` của hold,
đọc từ API tạo hold, sửa hold hoặc `GET /api/v1/seat-holds/current`.
Version này độc lập với version của booking review; không dùng lẫn hai giá trị.
Các trường version/số khách phải là số nguyên JSON, không chấp nhận chuỗi hoặc số thập phân.

`selections` thay thế **toàn bộ** lựa chọn cũ:

- `ONE_WAY`: đúng một `OUTBOUND`.
- `ROUND_TRIP`: bắt buộc `OUTBOUND`, có thể chưa chọn `RETURN`.
- Mỗi chiều xuất hiện tối đa một lần. Nếu có chiều về, phải đảo tuyến và có ngày
  khởi hành địa phương sau ngày đi.
- Bỏ `RETURN` khỏi request sẽ trả ghế và xóa chiều về cũ.
- Hạng ghế là `ECONOMY` hoặc `BUSINESS`; hai chiều có thể khác hạng.
- Người lớn >= 1, trẻ em/em bé >= 0. Mỗi chiều giữ `adults + children` ghế.

## Kết quả và tính nguyên tử

Trả `200` với envelope `{"data": ...}` và cùng schema với GET current:

```json
{
  "data": {
    "seatHoldId": "same-hold-public-id",
    "status": "ACTIVE",
    "tripType": "ONE_WAY",
    "passengerCounts": {"adults": 1, "children": 0, "infants": 0},
    "expiresAt": "2030-01-10T00:15:00Z",
    "legs": [{
      "direction": "OUTBOUND",
      "flightId": "new-flight-public-id",
      "cabinClass": "BUSINESS",
      "origin": {"code": "SGN", "city": "TP. Hồ Chí Minh"},
      "destination": {"code": "HAN", "city": "Hà Nội"},
      "departureAt": "2030-01-11T10:00:00+07:00",
      "arrivalAt": "2030-01-11T12:00:00+07:00"
    }],
    "bookingId": null,
    "version": 2
  }
}
```

Giữ nguyên `seatHoldId`, thời điểm bắt đầu và `expiresAt`; không cấp lại 15 phút.
Khi loại hành trình, số khách hoặc lựa chọn thực sự thay đổi, version tăng đúng 1
và các review chưa dùng bị vô hiệu. Không tự tạo review, booking, payment hoặc
chuyển thông tin hành khách/hành lý từ review cũ. Frontend giữ bản nháp để tạo
review mới và lưu lại hành lý; backend sẽ kiểm tra và tính lại giá/quyền lợi.

Gửi đúng nội dung hiện tại với đúng version trả `200` và giữ nguyên version,
review, tồn ghế. Các API sửa từng chiều/số khách cũ cũng tăng version khi thay đổi.
Hold mới tạo (kể cả giữ lại cho đơn hết hạn) bắt đầu từ version 1.

Khóa tài khoản, hold và toàn bộ fare cũ/mới theo thứ tự; chỉ điều chỉnh phần chênh
lệch số ghế trong một transaction. Ghế đang giữ được tái sử dụng, kể cả khi tồn
còn lại bằng 0. Nếu một chiều không đủ ghế, rollback toàn bộ: lựa chọn, tồn ghế,
review và version cũ vẫn giữ nguyên khi hold còn hạn. Nếu hết hạn trong lúc xử lý,
rollback việc đổi và commit thu hồi ghế của lựa chọn cũ đúng một lần.

## Lỗi và cách frontend xử lý

| HTTP | Code | Xử lý |
| --- | --- | --- |
| 401 | `UNAUTHENTICATED` | Đăng nhập lại. |
| 404 | `SEAT_HOLD_NOT_FOUND` | Hold không tồn tại hoặc không thuộc tài khoản. |
| 422 | `VALIDATION_ERROR` | Sửa dữ liệu hoặc cặp chuyến không hợp lệ. |
| 409 | `SEAT_HOLD_VERSION_CONFLICT` | GET current, hiển thị lựa chọn mới nhất để người dùng xác nhận lại. |
| 409 | `INSUFFICIENT_CAPACITY` | Báo không đủ ghế; tiếp tục dùng lựa chọn cũ còn hạn. |
| 409 | `FLIGHT_UNAVAILABLE` | Chọn chuyến/hạng còn mở bán và chưa khởi hành. |
| 409 | `SEAT_HOLD_EXPIRED` | Bắt đầu lượt giữ chỗ mới bằng POST sau khi người dùng chọn lại. |
| 409 | `HOLD_ATTACHED_TO_BOOKING` | Hold đã gắn đơn; API này không hủy đơn hoặc link thanh toán. |

Sau lỗi mạng không biết request đã thành công hay chưa, GET current để đối chiếu.
Gửi lại version cũ sau một thay đổi thành công trả conflict; không tự lấy version
mới để ghi đè lựa chọn mới nhất. Hai request đổi cùng version chỉ một request được ghi.

Luồng UI: chọn chuyến/hạng khác → xác nhận đổi → gọi PUT một lần → thay state hold
bằng response → xóa reviewId cũ → dùng bản nháp hành khách để review lại.
Không gọi hủy hold rồi POST hold mới.

## Chạy thử bằng Postman

1. Đăng nhập, lưu token vào `accessToken`; tìm chuyến, lưu public ID vào `newFlightId`.
2. Tạo hold bằng `POST /api/v1/seat-holds` hoặc đọc GET current, lưu `data.seatHoldId`
   và `data.version`. Dùng hold chưa tạo booking và còn hạn.
3. Gọi PUT với body trên, thay `expectedVersion` bằng version vừa đọc. Kiểm tra
   cùng hold ID/hạn cũ, chuyến/hạng mới và version tăng 1.
4. Gửi lại body với version cũ: nhận `409 SEAT_HOLD_VERSION_CONFLICT`.
5. Dùng version mới, gửi nguyên lựa chọn hiện tại: nhận `200`, version giữ nguyên.

Schema cần Flyway V9 (`seat_hold.hold_version`, mặc định 1, bắt buộc >= 1).
Không sửa migration V8 đã chạy. Khởi động backend với DB cần dùng sẽ tự áp dụng V9.
