# SkyGo

Ứng dụng Expo SDK 58 / React Native, triển khai thiết kế **Flight_SkyGo** bằng tiếng Việt với dữ liệu mẫu.

## Chạy ứng dụng

```sh
npm install
npm start
```

Mở bằng Expo Go tương thích SDK 58 trên iOS/Android. Xem nhanh trên web bằng `npm run web`. Bản web có chiều rộng 402 px để xem bố cục di động.

## Luồng trải nghiệm

Tìm chuyến → Kết quả → Chi tiết → Hành khách → Hành lý → Chỗ ngồi → Thanh toán → Xác nhận / Xem vé.

- Một chiều, khứ hồi và hai chặng nhiều điểm đến; chọn chuyến riêng cho mỗi chặng.
- Tối đa 4 người lớn từ 12 tuổi. Thông tin mỗi người, hành lý và ghế ngồi được giữ khi quay lại hoặc sửa từ màn thanh toán.
- Lọc theo hãng, giá tối đa, bay thẳng, giờ khởi hành; sắp xếp và lưu chuyến yêu thích.
- Giá USD đã gồm thuế. Ký gửi: $45/kiện/người/chặng. Ghế: $24/chỗ/chặng. Bảo vệ chuyến đi: $29/người/hành trình. Thương gia: 2 lần giá vé Phổ thông.
- Không thể chọn ghế đã có người hoặc trùng ghế của hành khách khác trong cùng chặng.
- Mastercard `9876` hoặc Visa `4242`: thanh toán mẫu thành công. Thẻ `0002`: bị từ chối, đổi thẻ rồi thử lại.
- Mặc định 1 người: vé $486 + 1 kiện $45 + ghế 12A $24 = **$555**.

Đây là bản mẫu: không có API hãng bay, giao dịch thật, email hay vé có giá trị đi lại. Dữ liệu chỉ ở trong bộ nhớ và được xóa khi khởi động lại ứng dụng. Chỉ nhập thông tin giả.

## Cấu trúc

- `src/app/`: các route Expo Router.
- `src/context/booking-context.tsx`: trạng thái hành trình, lựa chọn và vé đã xác nhận.
- `src/data/booking.ts`: dữ liệu mẫu, định dạng và kiểm tra ngày.
- `src/components/`: các thành phần giao diện dùng chung.
- `src/theme/`: màu sắc và font Inter theo Figma.
- `assets/figma/`: ảnh, SVG gốc được tải từ thiết kế, dùng trực tiếp từ tệp cục bộ.

## Kiểm tra

```sh
npm run lint
npm run typecheck
npx expo export --platform web
npx expo export --platform ios --platform android --output-dir /tmp/skygo-native-export
```

Đã kiểm thử luồng trên trình duyệt: đặt vé một chiều, email sai rồi sửa, đổi hành lý, bỏ chọn ghế, thanh toán thất bại rồi phục hồi, xem vé; khứ hồi 2 người với lựa chọn hành lý/ghế riêng và bảo vệ chuyến đi. Đã kiểm tra đóng gói iOS và Android. Cần kiểm thử thêm trên thiết bị thật cho bàn phím, vùng an toàn và các điều khiển native.
