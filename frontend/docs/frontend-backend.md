# Kết nối frontend và backend SkyGo

## Phạm vi đã nối

Tài khoản, trang chủ, danh mục sân bay và tìm chuyến gọi Spring Boot thật.
Luồng chọn chuyến → giữ chỗ → báo giá → xác nhận hành khách → review → hành lý →
tạo đơn cũng đã nối API. Đơn thật dừng ở trạng thái chờ thanh toán; chưa mở QR
mô phỏng và chưa cấp vé. Lịch sử vé cũ vẫn là dữ liệu mô phỏng, chưa liệt kê đơn thật.

Bộ chọn điểm đến tải toàn bộ danh mục sân bay (có phân trang), bỏ sân bay đang là
điểm đi. Không gửi `origin` để lọc theo tuyến đang mở bán. Tuyến/ngày không có
chuyến sẽ trả kết quả rỗng khi tìm kiếm.

### Luồng booking thật trên Expo Go

- Chọn hạng vé yêu cầu đăng nhập. Khứ hồi chọn chuyến đi trước rồi chuyến về.
- FE đọc giữ chỗ hiện tại trước khi tạo/cập nhật. Giữ nguyên hạn BE, không tự gia hạn.
  BE chưa có API hủy hold/đổi loại hành trình; nếu hold khác loại hoặc đã gắn đơn,
  cần chờ hết hạn. Khởi động lại app không xóa hold trên BE.
- Giá sau khi chọn đủ chuyến lấy từ booking-quotes; review và tổng tiền chốt lấy
  từ booking-reviews. Màn review tách vé chưa VAT, hành lý chưa VAT, VAT và tổng.
- Hộ chiếu nhập quốc tịch/quốc gia cấp dạng mã ISO 2 chữ, ví dụ VN, SG, US.
- Hành lý đọc baggage-options, chỉnh bản nháp trong màn hình, chỉ gửi PUT khi lưu.
  Thoát không lưu bỏ bản nháp. Xung đột version yêu cầu tải lại và xác nhận, không gửi đè.
- Tạo đơn gửi reviewId, expectedVersion và Idempotency-Key giữ nguyên khi thử lại
  cùng review/version. Hiển thị mã đơn, tổng tiền và hạn giữ chỗ sau thành công.
- BE phải cấu hình khóa mã hóa review và giấy tờ booking như backend/README.md.
  FE hiển thị lỗi nếu BE thiếu cấu hình; không chuyển sang đặt vé mô phỏng.
- Các test adapter dùng fetch giả lập, chưa thay thế kiểm thử trên Expo Go với BE thật.

## Chạy để demo

1. Chạy MariaDB và backend như bạn đang chạy. Khởi động lại backend sau thay đổi SecurityConfig để CORS có hiệu lực.
2. Trong `frontend/.env.local`, thêm hoặc sửa `EXPO_PUBLIC_API_URL=http://localhost:8080` khi chạy web/iOS Simulator. Android Emulator dùng `http://10.0.2.2:8080`; điện thoại thật dùng IP LAN của máy chạy BE, ví dụ `http://192.168.1.20:8080` (cùng Wi-Fi, mở cổng 8080). Nếu không cấu hình, code mặc định localhost trên web/iOS và 10.0.2.2 trên Android.
3. Từ thư mục frontend, chạy `npm run web` hoặc `npm start`, rồi reload app sau khi thay URL.
4. Mở Tài khoản → Đăng ký tài khoản mới bằng dữ liệu thử → Đăng nhập → tải lại hồ sơ → Đăng xuất. Mật khẩu tối thiểu 12 ký tự, có hoa/thường/số/ký tự đặc biệt, tối đa 72 byte UTF-8. Tài khoản mock không tự tồn tại trong DB.
5. Trang chủ lấy tuyến, giá, ảnh và chuyến sắp tới qua `/api/v1/home`. Chuyến sắp tới chỉ xuất hiện nếu DB có đơn đã thanh toán và vé đủ điều kiện.

Nếu web chạy cổng khác 8081, dùng tham số `--skygo.cors.allowed-origins=http://localhost:8082` khi khởi động. Có thể truyền nhiều origin cách nhau dấu phẩy. Origin phải đúng cả giao thức, hostname và cổng. Cách dễ nhất là chạy web ở cổng 8081.

## Đường đi trong mã nguồn

- `src/shared/api/client.ts`: URL máy chủ, fetch, JSON, header, timeout 15 giây và lỗi mạng.
- `src/modules/account/data/api-account-service.ts`: bốn API tài khoản; đổi `userId` từ BE sang `id` mà UI đang dùng; chuyển lỗi BE thành AccountError.
- `src/modules/account/context/account-context.tsx`: lưu session và cung cấp login/register/me/logout cho màn hình. Đã đổi import từ mock sang API adapter.
- `src/modules/flights/services/home-service.ts`: contract và request trang chủ.
- `src/modules/flights/screens/home-screen.tsx`: gọi API khi mở/quay lại màn hình hoặc đổi tài khoản; hiển thị loading/lỗi/rỗng và ảnh từ BE. Hủy request cũ khi rời màn hình.
- `backend/.../auth/security/SecurityConfig.java`: cho phép origin web gọi API qua CORS.

## Ví dụ đăng nhập

FE gửi:

```http
POST /api/v1/auth/login
Content-Type: application/json

{"phoneNumber":"0912345678","password":"MatKhauThu123!"}
```

BE kiểm tra input, tìm tài khoản trong MariaDB, so khớp BCrypt và tạo phiên. BE trả JSON bọc trong `data`, gồm accessToken, expiresAt và user. Adapter bóc data, context lưu session trong bộ nhớ, React cập nhật giao diện.

Khi xem hồ sơ, FE gửi `GET /api/v1/auth/me` kèm `Authorization: Bearer <accessToken>`. BE kiểm tra hash token, hạn phiên và trạng thái thu hồi trước khi trả user. Token là chuỗi ngẫu nhiên opaque, không phải JWT; đây không phải cơ chế refresh token. Logout gọi BE thu hồi phiên và FE xóa session. Phiên FE hiện chỉ ở RAM: reload app cần đăng nhập lại, tài khoản trong DB vẫn còn.

## Câu trả lời khi thầy hỏi

“Em kết nối frontend với backend qua HTTP API, dữ liệu trao đổi dưới dạng JSON. Frontend dùng fetch gọi địa chỉ backend được cấu hình qua biến môi trường. Backend dùng controller nhận request, service xử lý nghiệp vụ, repository dùng JDBC truy vấn MariaDB rồi trả response. Frontend không truy cập database trực tiếp. Sau đăng nhập, frontend gửi Bearer token cho request cần xác thực. Với bản web, backend cấu hình CORS để cho phép origin của frontend. Em dùng adapter để chuyển định dạng API sang model giao diện nên không cần viết logic gọi mạng trong từng form.”

CORS là kiểm soát truy cập từ trình duyệt, không thay thế xác thực. Native app không bị trình duyệt áp CORS, nhưng vẫn phải có URL đúng và kết nối mạng tới BE.

## Kiểm tra và chẩn đoán

### Expo Go trên iPhone thật

- Đặt `EXPO_PUBLIC_API_URL=http://<IP LAN của máy chạy backend>:8080` trong `.env.local`. Không dùng `localhost` trên iPhone. IP có thể đổi khi đổi Wi-Fi/hotspot.
- Kết nối iPhone và máy chạy backend vào cùng mạng Wi-Fi; hoặc bật Điểm truy cập cá nhân trên iPhone và cho Mac kết nối vào đó.
- Mở `http://<IP LAN>:8080/api/v1/home` bằng Safari trên iPhone trước. Phải nhận được JSON; nếu không, kiểm tra backend, mạng, quyền Mạng cục bộ của Expo Go trong Cài đặt iPhone và firewall trên Mac.
- Sau khi sửa `.env.local`, tải lại app trong Expo Go. Nếu vẫn dùng cấu hình cũ, dừng Expo và chạy lại `npm start -- --clear`, rồi quét QR.
- Expo tunnel chỉ chuyển tiếp máy chủ phát triển Expo; backend cổng 8080 vẫn cần kết nối LAN hoặc một URL HTTPS công khai riêng.

- DevTools → Network: thấy request login/me/home, status và JSON. Không chia sẻ ảnh chụp có token/mật khẩu.
- 401: thông tin đăng nhập sai hoặc token không hợp lệ/hết hạn.
- 422: input không đạt validation; UI dùng details để đánh dấu trường lỗi.
- 429: khóa đăng nhập tạm thời, dùng lockedUntil để đếm ngược.
- Lỗi CORS chỉ trên web: kiểm tra origin và khởi động lại BE.
- Không kết nối được máy chủ: kiểm tra BE, port, URL theo thiết bị, mạng và firewall. localhost trên điện thoại là điện thoại, không phải máy tính.

Chạy `npm run typecheck`, `npm run lint`, `npm run test:account`, `npm run test:booking` và `npm run test:api` từ frontend. Test API dùng fetch giả lập để kiểm tra adapter; cần demo thực tế với BE/DB để xác nhận đầy đủ đường truyền.

## Kết quả kiểm tra khi tích hợp

Typecheck đạt; lint không có lỗi (còn hai warning cũ ở payos-screen). 10 test tài khoản, 14 test booking và 3 test API adapter đạt. BE biên dịch và 11 unit test đạt; ba test tích hợp DB bỏ qua vì chưa chỉ định DB test riêng. Request thật GET home và OPTIONS login đều trả 200 với CORS đúng. Chrome hiển thị tuyến SGN–HAN 3.564.000đ, HAN–DAD 1.991.000đ và ảnh từ BE. Chưa thao tác đăng ký/đăng nhập qua giao diện với DB thật; adapter đã được test bằng fetch giả lập.

## API danh mục và tìm chuyến sau khi pull

- `GET /api/v1/regions`: nhóm khu vực theo thứ tự BE.
- `GET /api/v1/airports?keyword=...&origin=SGN&page=0&size=100`: điểm đi và điểm đến đều lấy tất cả sân bay đang hoạt động, kể cả sân bay chưa có tuyến/chuyến; origin được backend kiểm tra nhưng không lọc danh sách. Bộ chọn nhóm khu vực → quốc gia → sân bay, tải hết trang danh mục và hủy request cũ khi gõ từ khóa. Nếu chọn trùng điểm đi/đến, frontend báo lỗi khi tìm; nếu không có chuyến phù hợp, màn kết quả báo rỗng và cho đổi tìm kiếm.
- `POST /api/v1/flights/search`: gửi tripType, origin, destination, departureDate, returnDate (chỉ khứ hồi), passengerCounts, direction, page, size. Với chiều về vẫn gửi origin/destination ban đầu, dùng direction RETURN để BE tự đảo tuyến.
- Danh sách sân bay và chuyến bay trả `{data: [], pagination: {...}}`, khác auth/home chỉ cần bóc data. Client dùng `unwrap: false` để giữ phân trang.
- Màn kết quả có nút chuyển chiều đi/về và phân trang, hiển thị giờ địa phương từ offset và đúng các hạng vé BE trả về; không tự tạo giá hạng vé thiếu. Giá hiện là một người lớn đã VAT, không phải tổng tiền gia đình.
- Giữ chỗ và tạo đơn đã được nối trong luồng hiện tại; xem phần phạm vi ở đầu tài liệu.

Kiểm tra sau lần nối mới: 6 test API adapter đạt, typecheck đạt, lint chỉ còn 2 warning cũ của PayOS. Bản BE đang chạy ở 8080 trả 401 cho endpoint mới; cần restart BE sau pull. Đã chạy tạm code BE mới ở 8082 và xác nhận regions/airports/search trả 200, CORS đúng; chiều RETURN ngày 26/11/2026 trả HAN → SGN lúc 10:30, Economy 3.564.000đ và Business 8.910.000đ. Các ngày không có lịch trả danh sách rỗng. Bản tạm được tắt sau kiểm tra.
