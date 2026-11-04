# SkyGo

Ứng dụng Expo SDK 58 / React Native, triển khai thiết kế **Flight_SkyGo** bằng tiếng Việt với dữ liệu mẫu.

## Chạy ứng dụng

```sh
npm install
npm start
```

Mở bằng Expo Go tương thích SDK 58 trên iOS/Android. Xem nhanh trên web bằng `npm run web`. Bản web có chiều rộng 402 px để xem bố cục di động.

## Luồng trải nghiệm

Tìm chuyến → Chọn chuyến → Đăng nhập nếu cần → Hành khách → Hành lý → Xem lại → Tạo booking → PayOS mô phỏng → Xác nhận / Xem vé.

- Một chiều hoặc khứ hồi, 1–4 người lớn; khứ hồi chọn đủ hai chuyến và thanh toán chung.
- Hành khách dùng CCCD hoặc hộ chiếu. Hành lý mua thêm chọn riêng từng người/từng chặng.
- Giá VND đã gồm thuế/phí. Phổ thông gồm 1 kiện 23 kg; Thương gia gồm 1 kiện tối đa 32 kg. Mua thêm tối đa 1 kiện 23 kg giá 324.000đ mỗi người mỗi chặng.
- Lọc, sắp xếp và lưu chuyến yêu thích. PayOS mẫu hỗ trợ thành công, thất bại, hủy, hết hạn; thử lại dùng cùng booking.
- Booking đã tạo lưu trên thiết bị theo tài khoản. Tài khoản mới, phiên đăng nhập và bản nháp chỉ giữ trong bộ nhớ.

Đây là bản mẫu: không có API hãng bay, giao dịch thật, email hay vé có giá trị đi lại. Chỉ nhập thông tin giả.

## Cấu trúc frontend theo module

Mã nguồn được chia theo chức năng. Khi sửa giao diện, bắt đầu từ `screens/` của module tương ứng. Xem [hướng dẫn frontend](docs/frontend-modules.md) để tra cứu file và quy tắc thêm màn hình.

| Thư mục                | Phụ trách                                            |
| ---------------------- | ---------------------------------------------------- |
| `src/app/`             | Điểm vào Expo Router và cấu hình điều hướng          |
| `src/modules/flights/` | Trang chủ, tìm chuyến, bộ lọc, chi tiết và yêu thích |
| `src/modules/booking/` | Hành khách, hành lý, model và dịch vụ booking        |
| `src/modules/payment/` | Xem lại, PayOS mẫu và trạng thái thanh toán          |
| `src/modules/tickets/` | Xác nhận, xem và chia sẻ vé mẫu                      |
| `src/modules/account/` | Hồ sơ tài khoản                                      |
| `src/modules/support/` | Trợ giúp                                             |
| `src/shared/`          | UI cơ bản, theme, asset, định dạng tiền và ngày      |
| `assets/figma/`        | Ảnh và SVG gốc từ thiết kế                           |

Module tài khoản có đăng ký, đăng nhập, hồ sơ và đăng xuất bằng dữ liệu mẫu. Dùng `0912345678` / `SkyGoDemo@2027` hoặc tạo tài khoản mới để trải nghiệm trong phiên sử dụng. Xem [hướng dẫn tài khoản và nối API](docs/account-module.md).

## Kiểm tra

```sh
npm run lint
npm run typecheck
npm run test:account
npm run test:booking
npx expo export --platform web
npx expo export --platform ios --platform android --output-dir /tmp/skygo-native-export
```

Đã kiểm thử luồng đăng nhập, một chiều/khứ hồi, CCCD/hộ chiếu, hành lý, hủy/thử lại thanh toán và phục hồi booking trên web. Cần kiểm thử thêm trên thiết bị thật cho bàn phím, vùng an toàn và các điều khiển native.

## Luồng đặt vé đã duyệt

Xem [booking-flow.md](docs/booking-flow.md) cho luồng một chiều/khứ hồi yêu cầu đăng nhập, CCCD/hộ chiếu, hành lý theo chặng, bảng giá VND, lịch sử trên thiết bị và PayOS mô phỏng. Luồng mới thay phần chọn ghế/thẻ/bảo hiểm mẫu; tài khoản mới vẫn chỉ tồn tại trong phiên.
