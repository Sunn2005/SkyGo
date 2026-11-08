# Đổi lượt giữ chỗ sau khi đã tạo QR

## Quy tắc

- Giữ nguyên đơn cũ, mã đơn, review đã dùng, hành khách, hành lý và snapshot giá.
- Kết thúc hold cũ bằng `RELEASED`; giữ liên kết booking/payment attempt để truy vết.
- Xác minh QR cũ đã đóng và chưa có tiền trước khi chuyển. Có tiền hoặc chưa rõ
  trạng thái thì không tạo hold mới; giao dịch đến muộn được lưu để đối soát.
- Hold mới `ACTIVE`, chưa gắn booking, version 1, dùng để quote/review chuyến khác.
- Sao chép `started_at_utc`/`expires_at_utc` của hold cũ sang hold mới. Thao tác đổi
  không gia hạn 15 phút; `created_at_utc` vẫn là thời điểm tạo bản ghi mới.
- Mỗi tài khoản có tối đa một hold ACTIVE và một thao tác chuyển đang xử lý.

## Request

```http
POST /api/v1/bookings/{{bookingId}}/switch-hold
Authorization: Bearer {{accessToken}}
Content-Type: application/json
Idempotency-Key: {{switchKey}}
```

```json
{
  "expectedSeatHoldId": "{{seatHoldId}}",
  "expectedVersion": 1,
  "tripType": "ONE_WAY",
  "passengerCounts": {"adults": 1, "children": 0, "infants": 0},
  "selections": [{
    "direction": "OUTBOUND",
    "flightId": "{{newFlightId}}",
    "cabinClass": "BUSINESS"
  }]
}
```

Lấy hold ID/version từ GET `/api/v1/seat-holds/current`. `expectedVersion` là
version hold, độc lập với review version. Các số phải là số nguyên JSON.
`Idempotency-Key` gồm 1–128 ký tự ASCII hiển thị, không có khoảng trắng.
Giữ nguyên key/body khi thử lại cùng thao tác; thao tác mới dùng key mới.

Danh sách thay thế toàn bộ lựa chọn: ONE_WAY chỉ OUTBOUND; ROUND_TRIP bắt buộc
OUTBOUND và cho phép chưa chọn RETURN. Có RETURN thì đảo tuyến và ngày địa phương
sau chiều đi; không trùng chiều. Các quy tắc số khách/hạng giống API đổi selection.
Endpoint dành cho hold ACTIVE thuộc đơn chưa thanh toán đã có QR được tạo xong.
Nếu hold đã hết hạn, đọc GET current và bắt đầu lựa chọn mới bằng POST seat-holds.

## Response

Trả 200, `Cache-Control: no-store`:

```json
{
  "data": {
    "switchId": "switch-public-id",
    "previousBooking": {
      "bookingId": "old-booking-public-id",
      "status": "AWAITING_REHOLD"
    },
    "seatHold": {
      "seatHoldId": "new-hold-public-id",
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
      "version": 1
    }
  }
}
```

Replay trả cùng `switchId` và ID hold mới, không trừ thêm ghế. Thông tin booking/hold
được đọc theo trạng thái hiện tại: hold có thể đã hết hạn hoặc được sửa sau lần
đầu. Không tự mở lại hold đã kết thúc khi replay.

## Transaction, lỗi mạng và tranh ghế

Backend kiểm tra lựa chọn/tồn ghế trước khi gọi PayOS, lưu `VERIFYING_PAYMENT`, rồi
gọi nhà cung cấp bên ngoài transaction khóa DB. Sau khi xác nhận không có tiền và
QR đã CANCELLED/EXPIRED, lưu `QR_CLOSED`. Bước cuối khóa tài khoản/hold/booking và
hợp tập fare cũ/mới theo ID tăng dần, điều chỉnh chênh lệch ghế, kết thúc hold cũ,
tạo hold mới và lưu `COMPLETED` trong một transaction. Ghế đang giữ được tái sử dụng.

Nếu đã biết thiếu ghế trước khi đóng QR, giữ nguyên hold/QR cũ. Nếu ghế mục tiêu
vừa hết sau khi QR đã được đóng, rollback phần đổi ghế, kết thúc hold cũ và giữ
đơn ở AWAITING_REHOLD; không tạo hold mới. Frontend phải đọc GET current sau lỗi và
cho chọn chuyến khác/tiếp tục đơn cũ, không hiển thị lại QR đã đóng.

Không thể rollback thao tác đóng QR từ nhà cung cấp. Nếu ghi DB bị lỗi sau khi
đóng QR, tiến trình QR_CLOSED còn được lưu để thử lại bằng cùng key/body. Khi
trạng thái PayOS chưa rõ, giữ tiến trình đang xử lý và chặn tạo QR/resume/chuyển
bằng key khác. Tác vụ nền thử phục hồi mỗi chu kỳ cleanup (mặc định 30 giây, tối
đa 20 thao tác/lượt); thao tác có hold nguồn đã kết thúc sẽ chuyển FAILED và
không khóa tài khoản vô thời hạn. Thử lại thủ công cũng dùng cùng key/body.

## Tiếp tục thanh toán và hạn đặt vé

POST `/api/v1/bookings/{bookingId}/resume-payment` hoặc `/open-for-payment` hỗ trợ
AWAITING_REHOLD và EXPIRED cũ. Trước khi giữ lại, kiểm tra giao dịch của các QR cũ.
Nếu đủ ghế thì tạo hold mới 15 phút cho **hành trình và giá đã lưu của đơn cũ**;
sau đó gọi `/payment-link` để tạo QR/order code mới. Nếu đang có hold ACTIVE của
lựa chọn khác, trả ACTIVE_HOLD_EXISTS để UI xin xác nhận chuyển lựa chọn trước.
Sau xác nhận, POST resume-payment với body tùy chọn:

```json
{"expectedSeatHoldId": "current-unbooked-hold-id", "expectedVersion": 1}
```

Backend chỉ thay hold chưa gắn đơn, kiểm tra ID/version, giữ ghế cho đơn cũ và trả
ghế của lựa chọn đang xem trong cùng transaction. Thiếu ghế hoặc version cũ giữ
nguyên hold đang xem. Gửi lại sau thành công dùng lại hold của đơn cũ, không gia
hạn/trừ thêm ghế. Nếu hold hiện tại đã gắn một đơn khác, dùng switch-hold của đơn
đó trước. Không có body thì vẫn giữ hành vi kiểm tra xung đột cũ. open-for-payment
không tự chuyển hold của lựa chọn khác.

Thiếu ghế: trả 409 INSUFFICIENT_CAPACITY, thông báo **“Hiện chưa đủ ghế. Hãy quay
lại sau nhé.”**, giữ đơn. Hết hạn giữ 15 phút cũng giữ đơn AWAITING_REHOLD, payment
tổng EXPIRED; không phải CANCELLED.

V10 thêm `flight.booking_closes_at_utc`; NULL dùng giờ khởi hành. Hạn không được
sau giờ khởi hành; đơn nhiều chiều dùng hạn sớm nhất. Đúng thời điểm cutoff đã
được xem là quá hạn. Chỉ quá mốc này mới hủy đơn chưa thanh toán, ghi
`cancelled_at_utc` và `cancellation_reason=BOOKING_WINDOW_CLOSED`. Thiếu ghế hoặc
tạm đóng bán không phải lý do hủy đơn. Đơn PAID hoặc đang cần đối soát không bị
tác vụ nền hủy tự động. Thanh toán đến sau cutoff được lưu đối soát, không cấp vé.

Kiểm tra cutoff khi resume/tạo QR/nhận webhook, cùng tác vụ nền mỗi chu kỳ cleanup.
Tìm chuyến thông thường và nearby-days đều loại chuyến đã qua cutoff.

GET `/api/v1/bookings/{bookingId}` bổ sung `requiresRehold`, `canResumePayment`,
`bookingClosesAt`, `cancelledAt`, `cancellationReason`. `canResumePayment` thể hiện
điều kiện trạng thái/thời hạn của đơn, không bảo đảm còn ghế hoặc không có hold khác.

## Các mã lỗi chính

| HTTP | Code | Xử lý |
| --- | --- | --- |
| 401 | UNAUTHENTICATED | Đăng nhập. |
| 404 | BOOKING_NOT_FOUND / SEAT_HOLD_NOT_FOUND | Kiểm tra ID/quyền sở hữu. |
| 422 | VALIDATION_ERROR | Sửa header/body/cặp chuyến. |
| 409 | SEAT_HOLD_VERSION_CONFLICT | GET current và xác nhận lại. |
| 409 | IDEMPOTENCY_KEY_REUSED | Key đã dùng cho nội dung khác. |
| 409 | HOLD_SWITCH_IN_PROGRESS | Tiếp tục kiểm tra/thử lại thao tác đang xử lý với key cũ. |
| 409 | PAYMENT_STATUS_UNVERIFIED | Thử lại cùng key/body; không tạo QR mới. |
| 409 | PAYMENT_RECONCILIATION_REQUIRED | Có giao dịch cần đối soát. |
| 409 | BOOKING_ALREADY_PAID | Hiển thị đơn đã thanh toán. |
| 409 | INSUFFICIENT_CAPACITY | Giữ đơn, thông báo quay lại sau; đọc GET current. |
| 409 | BOOKING_WINDOW_CLOSED | Chuyến đã qua cutoff; kiểm tra trạng thái đơn cũ vì chuyến mục tiêu có thể là chuyến quá hạn. |
| 409 | SEAT_HOLD_EXPIRED | Hold nguồn hết hiệu lực; đọc GET current. |
| 409 | FLIGHT_UNAVAILABLE / HOLD_MISMATCH | Kiểm tra chuyến/hạng/hold đang chọn. |

## Thử bằng Postman

1. Đăng nhập; tạo hold, review, booking và gọi payment-link để có QR.
2. GET current, lưu hold ID/version; chọn flightId/hạng mới từ kết quả tìm kiếm.
3. Gọi switch-hold với body trên và một UUID làm switchKey. Xác nhận booking cũ
   AWAITING_REHOLD, hold mới khác ID, bookingId null, hạn giống hold cũ.
4. Gửi lại cùng key/body: cùng switchId/hold ID. Đổi body nhưng giữ key: 409.
5. Tiếp tục quote/review bằng hold mới; mở lại đơn cũ qua GET để kiểm tra snapshot.
6. Gọi resume-payment cho đơn cũ; nếu còn hold chưa gắn đơn của lựa chọn mới, xác
   nhận chuyển và gửi body hold ID/version như trên. Sau đó gọi payment-link;
   xác nhận hold/QR mới, cùng mã đơn và giá đã lưu.

Kiểm thử tự động dùng MariaDB test thật và PayOS giả lập, gồm webhook có chữ ký.
Không gọi nhà cung cấp PayOS thật hoặc phát sinh giao dịch thật trong test.
