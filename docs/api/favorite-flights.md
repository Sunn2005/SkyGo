# Chuyến bay yêu thích

Tất cả endpoint yêu cầu `Authorization: Bearer <accessToken>`. Tài khoản lấy từ phiên đăng nhập.

- `PUT /api/v1/favorite-flights/{flightId}`: lưu chuyến cụ thể, trả 204. Lưu trùng không đổi thời điểm lưu. Chuyến không tồn tại trả 404; chuyến đã khởi hành chưa được lưu trước đó trả 422.
- `DELETE /api/v1/favorite-flights/{flightId}`: bỏ lưu của tài khoản hiện tại, trả 204 kể cả khi không còn mục đó.
- `GET /api/v1/favorite-flights?page=0&size=20`: mới lưu trước; size 1–100, page từ 0. Trả `data`, `pagination` và `favoriteFlightIds` (toàn bộ ID đã lưu của tài khoản để hiển thị trái tim chính xác trên kết quả tìm kiếm).

Mỗi phần tử `data` gồm `savedAt`, `availability` và `flight`. `flight` dùng cấu trúc kết quả tìm chuyến: mã chuyến, hãng, sân bay, thời gian địa phương có UTC offset, thời lượng và các hạng vé với giá hiện tại cho 1 người lớn đã gồm VAT.

`availability`: `AVAILABLE`, `SOLD_OUT`, `CLOSED`, `DEPARTED`. Chuyến không còn khả dụng vẫn hiện trong danh sách, không trả giá giả bằng 0. Mua vé tiếp tục dùng API tìm chuyến và giữ chỗ để xác nhận giá/khả năng đáp ứng số khách.

Migration V11 tạo `favorite_flight`, khóa chính `(user_id, flight_id)`. Thao tác yêu thích không thay đổi giá, tồn ghế, giữ chỗ hoặc đơn đặt vé. Các response không được cache công khai (`Cache-Control: no-store`).

Test tích hợp: `FavoriteFlightIntegrationTest`, yêu cầu cấu hình database test qua `SKYGO_TEST_DB_URL` cùng thông tin đăng nhập tương ứng.
