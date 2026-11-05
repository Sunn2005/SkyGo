# Luồng đặt vé frontend SkyGo

## Phạm vi bản thử

Frontend Expo dùng dịch vụ giả lập; chưa gọi BE, chưa giữ ghế trên hệ thống thực và không nhận thanh toán thật. QR tĩnh chứa chuỗi minh họa, không chứa thông tin ngân hàng. Không tạo commit tự động.

Nguồn: PDF Flight_SkyGo, tài liệu nghiệp vụ nhóm và các quyết định mới nhất trong chat. Các quyết định mới thay thế yêu cầu cũ: hãng SkyGo (SGA), QR thay PayOS, hành lý 325.000đ/kiện và tối đa 4 kiện/người/chiều, trẻ em 90%, em bé 10%.

## Điều hướng và xác thực

- Bốn tab dùng chung: Trang chủ, Mua vé (`/search`), Lịch sử, Hồ sơ. Hiện riêng trên bốn màn chính, ẩn trong các bước đặt vé và xác thực.
- Khách tìm và xem chuyến bình thường. Chọn chuyến/hạng vé hiện hộp thoại Đăng nhập để đặt vé; chỉ chuyển đăng nhập khi bấm tiếp tục. Để sau giữ màn hiện tại.
- Lưu tạm tìm kiếm/chuyến/hạng vé trong context. Sau đăng ký → đăng nhập, quay lại kiểm tra chỗ và giữ chuyến đã chọn. Thời gian đăng nhập không tính vào lượt giữ chỗ.
- Các route hành khách, rà soát, hành lý, QR và vé yêu cầu đăng nhập. Lịch sử nằm ở tab riêng; Hồ sơ không còn nút xem lịch sử hay booking gần nhất.

## Các bước

1. Chọn một chiều/khứ hồi, sân bay, ngày bay và nhóm hành khách.
2. Chọn chuyến và hạng vé cho chặng đi. Dịch vụ mock giữ số ghế người lớn + trẻ em trong 15 phút. Em bé không chiếm ghế.
3. Khứ hồi: chọn thêm chuyến về và hạng vé riêng; dùng chung hạn giữ chỗ ban đầu.
4. Nhập thông tin từng người, giấy tờ và liên hệ. Người lớn có danh xưng. Em bé gắn với người lớn đi kèm, không có ghế riêng. Tích xác nhận thông tin mới được tiếp tục.
5. Rà soát hành trình, hành khách, liên hệ, giá từng nhóm/chặng. Giấy tờ được che bớt ở bước rà soát.
6. Mở Hành lý từ màn rà soát nếu cần. Lưu xong quay về và cập nhật tổng tiền. Màn hành lý chỉ hiện tổng phí hành lý.
7. Thanh toán: tạo booking chống gửi lặp, tạo yêu cầu QR có cùng `expiresAt` với giữ chỗ; không có thêm 15 phút ở bước này.
8. Nút Đã thanh toán chỉ đọc trạng thái. Công cụ thử nghiệm mô phỏng sự kiện nhận tiền; chỉ sau khi adapter trả booking `confirmed` mới tới Thành công → Chi tiết vé.

## Giá và hành khách

- `Search.count` là số người lớn; `children` và `infants` là hai số lượng bổ sung, mặc định 0. `Traveller.kind` và `companionIndex` được lưu trong snapshot.
- Người lớn từ 12 tuổi, trẻ em từ 2 đến dưới 12, em bé dưới 2; tuổi theo ngày khởi hành chặng đi như tài liệu nhóm. Không có bước chọn vị trí ghế.
- Người lớn 100%, trẻ em 90%, em bé 10% theo giá/hạng vé từng chặng, VND. Mức 90% mới nhất đang áp dụng cả quốc tế, thay mức 75% quốc tế trong tài liệu cũ.
- Giá nguồn mẫu hiện đã gồm thuế/phí. Bản thử áp dụng tỷ lệ trên giá mẫu trọn gói, không tự bịa biểu thuế riêng. Khi tích hợp, BE trả giá cơ sở, thuế/phí từng nhóm và tổng chuẩn.
- Giới hạn thử nghiệm hiện giữ tối đa 4 khách có ghế riêng và tối đa 1 em bé/người lớn; cần nhóm/BE xác nhận giới hạn chính thức. Không tự mở rộng số khách không giới hạn.
- Nội địa chọn CCCD/định danh cá nhân 12 số hoặc hộ chiếu; quốc tế bắt buộc hộ chiếu. Quy tắc định dạng giấy tờ hiện phục vụ test, cần BE xác nhận.
- Phổ thông: xách tay 10 kg, 1 kiện ký gửi 23 kg; Thương gia: xách tay 10 kg, 1 kiện 32 kg. Người lớn/trẻ em mua thêm 0–4 kiện mỗi chiều, 325.000đ/kiện, tối đa 23 kg và tổng 3 chiều 158 cm.
- Em bé không mua hành lý riêng; nhu cầu mua thêm thực hiện trên người lớn đi kèm. Hạn mức miễn cước riêng của em bé chưa có số liệu được chốt, UI ghi rõ chờ xác nhận.

## Thời hạn và tồn chỗ

- `reservation-service.ts` mô phỏng kho 12 chỗ/chuyến/hạng/ngày/tuyến, dùng cùng instance để test cạnh tranh. Đây là số mẫu, không phải tồn chỗ thật.
- Kiểm tra đủ chỗ ngay khi chọn; đổi chuyến chỉ thay giữ chỗ cũ sau khi chỗ mới thành công. Không reset `expiresAt`. Đổi chặng đi yêu cầu chọn lại chặng về để kiểm tra toàn hành trình.
- Cảnh báo một lần khi còn không quá 5 phút, xuất hiện ở bước đang thao tác. Chỉ màn QR hiện đồng hồ.
- Hết hạn: chặn thao tác, yêu cầu tìm lại chuyến; giữ thông tin hành khách/liên hệ trong phiên, không tự gia hạn QR hay lượt giữ chỗ cũ.
- Chưa triển khai cooldown/hạn mức cấp lượt giữ chỗ mới sau khi hết hạn: đây là nghiệp vụ còn cần chốt với BE, không mặc định thành 5 phút.
- Booking đã tạo và yêu cầu thanh toán lưu trên thiết bị theo tài khoản; bản nháp, tài khoản mới và kho giữ chỗ mẫu chỉ trong bộ nhớ. Reload/mở lại app chưa khôi phục lượt giữ chỗ đang nhập. BE phải cung cấp khôi phục giữ chỗ thực.
- Không dùng mock FE để bảo đảm tránh bán trùng giữa thiết bị. BE phải dùng giao dịch nguyên tử, thời gian server, quyền sở hữu và kiểm tra trạng thái thanh toán/giữ chỗ trong cùng giao dịch khi cần.

## Điểm nối BE

- Tra cứu chuyến theo tuyến/ngày/số ghế, báo giá theo nhóm hành khách và hạng vé từng chặng.
- Tạo/đổi/đọc/giải phóng giữ chỗ: trả `reservationId`, `expiresAt`, giờ server, lỗi thiếu chỗ/giá thay đổi. Kiểm soát số lượt giữ mới để tránh giữ vô hạn.
- Tạo booking có idempotency, xác thực chủ đơn, kiểm tra thông tin/giá và giữ chỗ. Không tin giá hay thời hạn FE tự gửi.
- Tạo QR thật và đọc trạng thái. Chỉ BE xác minh sự kiện thanh toán. Chốt xử lý tiền đến muộn; không tự phát vé sau hết hạn.
- `simulateWebhook` chỉ dành cho bản thử. Route `/payos` còn giữ tên nội bộ để tương thích route cũ, giao diện chỉ ghi QR.
- Lưu QR: web tải SVG minh họa; native dùng menu chia sẻ, chưa xác minh lưu ảnh vào thư viện trên thiết bị thật.

## Kiểm thử

`npm run lint`, `npm run typecheck`, `npm run test:account`, `npm run test:booking`.

Browser: khách → thông báo → đăng nhập → form gia đình → rà soát → hành lý → QR → mô phỏng nhận tiền → thành công; kiểm tra tổng tiền khớp giữa các bước, nút kiểm tra trạng thái không tự xác nhận, bottom nav chỉ ở màn chính. Test service kiểm tra tranh ghế cuối, đổi chuyến giữ hạn cũ, QR không gia hạn, sự kiện trả tiền muộn và tỷ lệ giá gia đình.

## Đối chiếu UI PDF sau rà soát

Các màn tìm chuyến (trang 8), kết quả (10/20), hành khách (11/21/22), hành lý (12), rà soát (13), QR (15), thành công (16) và chi tiết đặt chỗ (17/23) dùng nền xám, thẻ trắng, thứ bậc chữ và nhóm thông tin theo PDF. Hành lý là phần tùy chọn mở từ rà soát theo quyết định mới hơn, lưu xong quay lại. Hạng vé/giá và hành lý hiển thị theo từng chiều. QR và chi tiết đọc snapshot của booking, không dùng bản nháp đang tìm.

Không điền sẵn tên/ngày sinh/email/điện thoại mẫu. Không giả định số vé điện tử, hóa đơn VAT hay thuế tách riêng khi chưa có trường dữ liệu. Mã đặt chỗ mới là mã tham chiếu 6 ký tự, tách với ID nội bộ. Thời điểm hoàn tất thanh toán lấy từ sự kiện của adapter. Mức xách tay 10 kg và hãng SkyGo theo nghiệp vụ đã chốt, thay ví dụ 7 kg/Vietnam Airlines trong PDF.
