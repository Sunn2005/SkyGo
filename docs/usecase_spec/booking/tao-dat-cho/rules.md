# Feature — Luật nghiệp vụ tạo đặt chỗ

Quy tắc đã nêu trong kế hoạch được ghi riêng với giả định kiểm thử. Các giả định bên dưới chỉ phục vụ chạy thử tool và không được xem là nghiệp vụ SkyGo đã chốt.

## Quy tắc theo kế hoạch triển khai

- **BR-BKG-001:** User phải đăng nhập trước khi bắt đầu tạo booking.
- **BR-BKG-002:** MVP chỉ hỗ trợ chuyến một chiều và từ 1 đến 4 hành khách người lớn.
- **BR-BKG-003:** Trước khi thanh toán, User được xem lại chuyến bay, hành khách, hành lý và tổng giá.
- **BR-BKG-004:** Booking chỉ được tạo cho chuyến bay nằm trong dữ liệu mẫu hiện có.
- **BR-BKG-005:** Backend phải chống tạo booking trùng khi User gửi lặp thao tác xác nhận.

## Giả định kiểm thử — chưa chốt

- **BR-BKG-101:** Mỗi hành khách cần họ, tên, ngày sinh, giới tính và email/số điện thoại liên hệ của người đặt chỗ.
- **BR-BKG-102:** Mỗi hành khách chọn 0, 1 hoặc 2 kiện hành lý ký gửi; phí giả định là 0 USD, 45 USD hoặc 90 USD cho mỗi hành khách.
- **BR-BKG-103:** Tổng giá mẫu bằng tổng giá vé của chuyến nhân với số hành khách, cộng phí hành lý; không cộng thuế hoặc phí khác trong bài thử này.
- **BR-BKG-104:** Khi một yêu cầu xác nhận giống nhau được gửi lại, backend trả lại mã booking đã tạo lần đầu và không tạo bản ghi mới.
- **BR-BKG-105:** Nếu dữ liệu hành khách không hợp lệ hoặc chuyến bay không còn phù hợp, booking không được tạo và ứng dụng cho phép User sửa thông tin hoặc chọn lại chuyến.

## Câu hỏi mở trước khi dùng nghiệp vụ thật

| Mã | Câu hỏi |
|---|---|
| `OQ-BKG-001` | Trường hành khách nào là bắt buộc và quy tắc xác thực tuổi/ngày sinh là gì? |
| `OQ-BKG-002` | Gói hành lý và biểu phí chính thức gồm những lựa chọn nào? |
| `OQ-BKG-003` | Giá vé, thuế, phí và tiền tệ được tính/fix tại thời điểm nào? |
| `OQ-BKG-004` | Booking có trạng thái nào trước khi PayOS xác nhận thanh toán? |
| `OQ-BKG-005` | Khóa chống lặp, thời hạn idempotency và cách xử lý retry chính thức là gì? |
