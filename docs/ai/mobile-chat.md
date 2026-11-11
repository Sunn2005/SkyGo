# Trợ lý SkyGo trên mobile

Frontend đã nối `POST /api/v1/ai/chat`. Từ **Trang chủ → Hỏi trợ lý SkyGo**, người dùng mở màn hình chat riêng; thanh điều hướng vẫn có bốn mục hiện tại.

## Cách dùng

- Chọn gợi ý hoặc nhập câu hỏi, sau đó nhấn nút gửi. Gợi ý, lựa chọn sân bay và lịch chỉ điền bản nháp, không tự gọi AI.
- Kết quả chuyến bay hiển thị giờ địa phương của từng sân bay, hạng vé và tổng giá nhóm hành khách một chiều. Mỗi câu trả lời hiện tối đa ba thẻ ban đầu; có thể mở thêm kết quả và chi tiết.
- Đơn đặt vé có mã đơn, trạng thái, các chặng và tổng tiền toàn đơn. Chỉ tra cứu, chưa có thao tác đặt vé hoặc thanh toán từ chat.
- Câu trả lời dài có nút đọc tiếp. Nguồn và thời điểm dữ liệu nằm trong phần thu gọn.
- Khi API trả `LOGIN_REQUIRED`, dialog xuất hiện trước khi điều hướng. Chọn **Để sau** để ở lại chat. Sau khi đăng nhập, chỉ câu hỏi đã chọn được khôi phục làm bản nháp; người dùng kiểm tra rồi gửi lại.
- Hội thoại lưu trong bộ nhớ trong thời gian ứng dụng chạy, giữ lại khi quay về Trang chủ. Đổi phiên đăng nhập hoặc đăng xuất sẽ xóa hội thoại và hủy nhận kết quả đang chờ. Không lưu chat vào bộ nhớ thiết bị.
- **Dấu +** trên header bắt đầu hội thoại mới sau khi xác nhận. HTTP 429 hiển thị thời gian chờ; frontend không tự gửi lại. Hết thời gian chờ không bảo đảm quota ngày đã phục hồi.

Màn hình chat dùng một cơ chế tránh bàn phím. Header và vùng an toàn nằm trong cùng cây layout, nên không cộng lại chiều cao của chúng vào `keyboardVerticalOffset`. Khi mở bàn phím, phần chào thu gọn để giữ chỗ cho hội thoại và ô nhập; danh sách không tự cộng thêm keyboard inset của iOS.

## Điều kiện chạy

1. Giữ MariaDB, Docker/MariaDB Vector và backend hoạt động, có kết nối Internet.
2. Dùng cấu hình backend như [chat-api.md](./chat-api.md): `SKYGO_AI_ENABLED=true`, `SKYGO_AI_INDEX_ENABLED=false`, `SKYGO_AI_INITIALIZE_SCHEMA=false`. Không chạy lại index để mở chat.
3. `frontend/.env.local` đặt `EXPO_PUBLIC_API_URL` là địa chỉ backend mà điện thoại truy cập được. Không dùng `localhost` của laptop trên điện thoại. Ví dụ mạng LAN riêng:

   ```dotenv
   EXPO_PUBLIC_API_URL=http://192.168.1.10:8080
   ```

   Hoặc dùng URL HTTPS tunnel backend hiện có. Tunnel Metro chỉ giúp tải app; API backend vẫn cần địa chỉ truy cập riêng.

4. Khởi động Expo theo cách đang dùng và mở bằng Expo Go. Khi thay `.env.local`, khởi động lại Expo để nạp cấu hình.

API key Gemini chỉ ở backend. Frontend gửi bearer token qua header khi có phiên đăng nhập; không gửi `userId`. Mỗi câu hỏi có thể dùng nhiều lượt model hoặc tạo embedding truy vấn RAG, nên chỉ gửi khi người dùng nhấn nút. Timeout riêng của chat là 150 giây; các API khác vẫn giữ timeout mặc định 15 giây. Hủy chờ ở frontend không bảo đảm server dừng xử lý một request đã nhận.

## Kiểm tra

Từ thư mục `frontend`:

```powershell
npm run typecheck
npm run lint
npm run test:ai
npm run test:api
```

`test:ai` và `test:api` dùng HTTP giả lập; không gọi Gemini, MariaDB Vector hoặc backend. Các kiểm tra này không thay thế kiểm tra bàn phím và điều hướng trên thiết bị.

Kiểm tra thủ công trên điện thoại:

| Tình huống                            | Kết quả cần thấy                                                 |
| ------------------------------------- | ---------------------------------------------------------------- |
| Mở chat và chọn gợi ý                 | Có bản nháp; chưa gửi request AI                                 |
| Mở bàn phím, nhập nhiều dòng          | Ô nhập có chiều cao giới hạn, nút gửi vẫn sử dụng được           |
| Gửi câu hỏi, chạm gửi liên tiếp       | Chỉ có một request đang chờ                                      |
| Thiếu sân bay/ngày                    | Có lựa chọn phù hợp từ API; chọn để điền bản nháp                |
| So sánh nhiều chuyến/hạng vé          | Ba thẻ đầu, mở thêm kết quả, tổng tiền đúng số khách             |
| Khách hỏi đơn gần đây                 | Dialog; hủy ở lại chat; đồng ý mới đến đăng nhập                 |
| Đăng nhập từ chat                     | Trở lại chat, bản nháp câu hỏi được khôi phục, không tự gửi      |
| Quay về Trang chủ rồi mở chat         | Hội thoại vẫn còn trong cùng phiên                               |
| Đăng xuất/đổi tài khoản khi đang chờ  | Không hiện lịch sử hoặc kết quả của phiên trước                  |
| Backend trả 429/mất mạng/phản hồi lỗi | Thông báo rõ, không tự gửi lại; thử lại bằng thao tác người dùng |
| Hội thoại mới                         | Có xác nhận xóa; vẫn giữ thời gian chờ 429 hiện tại              |
| Quay ngang hoặc màn hình hẹp          | Thẻ và ô nhập nằm trong khung ứng dụng                           |
