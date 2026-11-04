# Luồng đặt vé frontend mẫu

Phạm vi đã duyệt: một chiều hoặc khứ hồi, 1–4 hành khách người lớn (từ 12 tuổi trong bản mẫu). Không có backend, thanh toán thật hoặc gửi email.

## Luồng

Khách được tìm kiếm và xem chi tiết chuyến. Chọn chuyến yêu cầu đăng nhập; giữ tìm kiếm và các chuyến đã chọn khi chuyển từ khách sang tài khoản. Đăng ký xong chuyển sang đăng nhập, không tự đăng nhập. Đăng xuất/đổi tài khoản xóa thông tin cá nhân trong bản nháp.

Khứ hồi chọn chuyến đi rồi chuyến về, chung danh sách hành khách, một booking và một lần thanh toán cho toàn bộ hành trình.

Thông tin hành khách → hành lý theo từng người/từng chặng → xem lại → tạo booking chống trùng → tạo yêu cầu PayOS mẫu → mô phỏng kết quả dịch vụ → lấy lại trạng thái → thành công khi booking được xác nhận.

Màn hành khách, hành lý, xem lại, PayOS, lịch sử và vé yêu cầu đăng nhập, kể cả mở trực tiếp route. Không còn bước chọn ghế/thẻ/bảo hiểm trong luồng đã duyệt.

## Giấy tờ

Mỗi hành khách bắt buộc họ, tên đệm và tên, ngày sinh, giới tính, loại giấy tờ và số giấy tờ. Email/số điện thoại liên hệ nhập một lần cho booking.

- CCCD: 12 chữ số.
- Hộ chiếu: 6–12 chữ/số theo quy tắc minh họa; quốc tịch, quốc gia cấp, ngày hết hạn. Hạn giấy tờ phải đến ngày cuối hành trình. Đây chưa phải quy tắc kiểm tra giấy tờ chính thức của hãng bay.
- Ngày sinh và hết hạn nhập NN/TT/NNNN, kiểm tra ngày có thực.

## Giá mẫu (VND)

| Khoản                                        | Phổ thông                     | Thương gia                    |
| -------------------------------------------- | ----------------------------- | ----------------------------- |
| Vé mặc định / người / chặng, đã gồm thuế phí | 1.494.000đ                    | 3.957.000đ                    |
| Đồ cá nhân + xách tay                        | Tổng 10 kg đã gồm             | Tổng 10 kg đã gồm             |
| Ký gửi đã gồm                                | 1 kiện tối đa 23 kg           | 1 kiện tối đa 32 kg           |
| Mua thêm, tối đa 1 kiện / người / chặng      | 1 kiện tối đa 23 kg: 324.000đ | 1 kiện tối đa 23 kg: 324.000đ |

Các chuyến mẫu khác có giá khác nhau. Không sử dụng kiện miễn phí không giảm giá vé. Tổng bằng giá vé các chặng × số người + hành lý mua thêm; không cộng thuế lần nữa. Không có phí chọn ghế/bảo hiểm.

Ví dụ 2 người, khứ hồi, vé Phổ thông mặc định, một người mua thêm một kiện chỉ lượt đi: 6.300.000đ.

## Dịch vụ mẫu và lưu trữ

`modules/booking/types.ts` định nghĩa `BookingInput`, `BookingPrice`, `BookingSnapshot`, `Booking`, `BookingService`; `modules/payment/types.ts` chỉ định nghĩa trạng thái và lần thanh toán. Đây là interface nội bộ frontend, chưa phải HTTP contract được BE xác nhận.

`modules/booking/data/mock-booking-service.ts` mô phỏng tạo booking, chống trùng bằng idempotency key, tính lại giá từ dữ liệu chuyến mẫu, tạo yêu cầu thanh toán, đọc trạng thái và danh sách theo tài khoản. Thao tác trong cùng instance được tuần tự hóa để tránh nhấn lặp; không thay thế transaction/unique constraint của BE và không đảm bảo chống trùng giữa nhiều tab/quy trình.

Booking: `pending_payment` → `confirmed`. Payment attempt: `pending` → `paid` / `failed` / `cancelled` / `expired`. Yêu cầu chờ hết hạn sau 15 phút, kiểm tra khi đọc và trên màn PayOS. Retry dùng booking cũ và tạo attempt mới; yêu cầu pending được dùng lại. Kết quả terminal không bị ghi đè bởi sự kiện lặp. Frontend chỉ hiển thị thành công sau khi đọc trạng thái `confirmed` từ adapter.

`modules/booking/data/booking-service.ts` lưu JSON có version qua localStorage (SQLite polyfill trên native, localStorage trên web). Booking lưu theo số điện thoại tài khoản đã chuẩn hóa, giữ qua lần mở app sau. Bản nháp chưa xác nhận không lưu. Không lưu mật khẩu hoặc token. Tài khoản mới và phiên auth vẫn nằm trong bộ nhớ theo module account hiện có: tải lại phải đăng nhập; dùng tài khoản mẫu để test phục hồi xuyên lần mở app, hoặc đăng ký lại cùng số thử nghiệm trước khi xem lịch sử của tài khoản mới.

Dữ liệu trên thiết bị chỉ dành cho thử nghiệm với thông tin giả, không phải biện pháp phân quyền/bảo mật production. Khi nối BE: thay adapter, xác thực Bearer và quyền sở hữu tại BE, tính giá/idempotency tại BE, tạo link PayOS thật; chỉ BE xác minh webhook. Không sử dụng `simulateWebhook` trong production.

## Kiểm thử

```sh
npm run lint
npm run typecheck
npm run test:account
npm run test:booking
npx expo export --platform web --output-dir /tmp/skygo-booking-web
```

Thử giao diện: khách chọn khứ hồi → đăng nhập → chọn chuyến về → nhập CCCD/hộ chiếu → mua thêm hành lý một lượt → xem tổng → xác nhận → hủy/thất bại/hết hạn → retry cùng mã → thành công → xem vé. Tải lại khi đang chờ rồi đăng nhập và mở **Tài khoản → Đặt chỗ của tôi** để tiếp tục.

Đã thêm `expo-sqlite`: dự án có native folders, development build cũ cần build lại để nhận native module. Vẫn cần kiểm thử trên thiết bị iOS/Android; kiểm thử web không thay thế kiểm thử native.

Kết quả kiểm tra: lint/typecheck, 8 test tài khoản, 9 test booking (gồm phục hồi dữ liệu model cũ) và export web thành công. Kiểm tra tương tác web bao gồm khách → đăng nhập → giữ chuyến khứ hồi → CCCD → hành lý lượt đi → tạo booking → hủy/retry giữ mã → tải lại/đăng nhập → phục hồi booking chờ → thanh toán thành công/xem vé. Đã thử một chiều Thương gia với 2 người, một hộ chiếu và một CCCD, quyền lợi 32 kg; đăng ký/đăng nhập tài khoản mới không thấy lịch sử của tài khoản mẫu. Export bundle iOS/Android thành công; chưa chạy trên thiết bị native.

Dọn model: đã xóa route `/seat`, `/cards` và màn hình tương ứng; không còn state chọn ghế/thẻ/bảo hiểm, hệ số giá cũ, tổng kiện `bags` hoặc loại chuyến nhiều điểm đến. Dữ liệu lịch sử cũ được chuẩn hóa theo model hiện tại khi đọc, giữ mã booking, tổng tiền và các lần thanh toán. Giá gồm vé đã có thuế/phí và hành lý mua thêm; không còn trường thuế tách riêng bằng 0.
