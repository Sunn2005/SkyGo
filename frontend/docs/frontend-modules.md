# Hướng dẫn chỉnh sửa frontend

## Bắt đầu từ đâu?

Chạy các lệnh từ thư mục `frontend/`:

```sh
npm install
npm start
# Hoặc xem trên trình duyệt
npm run web
```

| Chức năng muốn sửa                    | File trong `src/modules/`              | Route        |
| ------------------------------------- | -------------------------------------- | ------------ |
| Trang chủ, nhập tìm chuyến            | `flights/screens/home-screen.tsx`      | `/`          |
| Chọn sân bay, ngày, số người, hạng vé | `flights/screens/options-screen.tsx`   | `/options`   |
| Danh sách kết quả                     | `flights/screens/results-screen.tsx`   | `/results`   |
| Bộ lọc                                | `flights/screens/filters-screen.tsx`   | `/filters`   |
| Chi tiết chuyến bay                   | `flights/screens/detail-screen.tsx`    | `/detail`    |
| Chuyến yêu thích                      | `flights/screens/saved-screen.tsx`     | `/saved`     |
| Thông tin hành khách và liên hệ       | `booking/screens/traveller-screen.tsx` | `/traveller` |
| Hành lý theo người/chặng              | `booking/screens/baggage-screen.tsx`   | `/baggage`   |
| Xem lại, tạo booking mẫu              | `payment/screens/payment-screen.tsx`   | `/payment`   |
| Xác nhận thành công                   | `tickets/screens/success-screen.tsx`   | `/success`   |
| Xem và chia sẻ vé                     | `tickets/screens/booking-screen.tsx`   | `/booking`   |
| Hồ sơ                                 | `account/screens/profile-screen.tsx`   | `/profile`   |
| Đăng nhập                             | `account/screens/login-screen.tsx`     | `/login`     |
| Đăng ký                               | `account/screens/register-screen.tsx`  | `/register`  |
| Trợ giúp                              | `support/screens/help-screen.tsx`      | `/help`      |

## Vai trò các thư mục

```text
src/
  app/                      # Route và _layout.tsx
  modules/
    flights/
      screens/              # Giao diện tìm/chọn chuyến
      components/           # FlightTimes
      data/                 # Sân bay và chuyến bay mẫu
      utils/                # Nhãn hành trình, danh sách chặng
      types.ts              # Flight, Search, Leg, AirportCode…
    booking/
      screens/              # Hành khách và hành lý
      components/           # ActionBar, TripSummary
      context/              # BookingProvider, useBooking
      data/                 # Khởi tạo, adapter booking và chuẩn hóa dữ liệu đã lưu
      services/             # Validation, hành lý và tính giá
      utils/                # Hiển thị họ tên
      types.ts              # Traveller, BookingInput, BookingPrice, BookingSnapshot, BookingService
    payment/
      screens/              # Xem lại và PayOS mẫu
      types.ts              # PaymentAttempt, PaymentStatus, PaymentOutcome
    tickets/
      screens/
      components/           # Ticket
    account/
      screens/              # Hồ sơ, đăng nhập và đăng ký
      components/           # Ô mật khẩu có ẩn/hiện
      context/              # AccountProvider, useAccount
      data/                 # Adapter và tài khoản mẫu trong bộ nhớ
      utils/                # Validation và chuẩn hóa số điện thoại
      types.ts              # Hợp đồng AccountService, user, session, lỗi
    support/screens/
  shared/
    components/
      ui.tsx                # Label, Icon, Button, Card, Row…
      fields.tsx            # Field, SelectField
      screen.tsx            # Khung màn hình, header, footer
    assets/index.ts         # Ánh xạ ảnh/SVG cục bộ
    theme/index.ts          # Màu, font, khoảng cách, bo góc
    utils/                  # Định dạng tiền, ngày, kiểm tra ngày
```

Chỉ tạo thư mục `components`, `hooks`, `data`, `utils` trong module khi có mã thực tế cần đặt vào. Component chỉ dùng trong một module thuộc module đó; UI cơ bản không phụ thuộc nghiệp vụ thuộc `shared`.

## Cách chỉnh giao diện

- Sửa bố cục, nội dung và style riêng trong `modules/<module>/screens/`.
- Sửa màu, font, khoảng cách dùng toàn ứng dụng trong `shared/theme/index.ts`.
- Sửa nút, chữ, thẻ, thông báo dùng chung trong `shared/components/ui.tsx`; ô nhập liệu trong `fields.tsx`.
- Sửa khung màn hình, header và vùng footer trong `shared/components/screen.tsx`. File này cũng hiển thị tiến trình các bước đặt vé hiện tại.
- Sửa phần hiển thị giờ bay ở `flights/components/flight-times.tsx`, phần tổng hành trình/thanh hành động ở `booking/components/`, phần vé ở `tickets/components/ticket.tsx`.
- Thêm ảnh vào `assets/figma/` và khai báo đường dẫn tĩnh trong `shared/assets/index.ts`.

## Trạng thái và dữ liệu mẫu

`booking/context/booking-context.tsx` giữ trạng thái dùng xuyên suốt luồng tìm chuyến → đặt vé → thanh toán → xem vé. `BookingProvider` được gắn một lần ở `src/app/_layout.tsx`; các màn hình dùng `useBooking()` để đọc/cập nhật cùng hành trình. Trạng thái hiện bao gồm tìm kiếm, bộ lọc, yêu thích, hành khách, hành lý và booking đang xem. Vé xác nhận được suy ra từ booking đã thanh toán. Chưa tách thành các store độc lập để giữ vòng đời hiện có của luồng đặt vé.

Dữ liệu sân bay và chuyến bay mẫu nằm trong `flights/data/`. Giá trị tìm kiếm mặc định và hành khách mẫu nằm trong `booking/data/defaults.ts`. Model dùng chung nằm trong `booking/types.ts`; component vé nhận `BookingSnapshot` qua props. Dữ liệu nhập `BookingInput` không chứa giá hoặc mã đặt chỗ; dịch vụ mẫu tính giá và trả snapshot.

Bản nháp và tài khoản mẫu dùng dữ liệu trong bộ nhớ; booking đã tạo lưu trên thiết bị. Xem [booking-flow.md](booking-flow.md) về giá, giấy tờ, PayOS mô phỏng và trạng thái. Khi tích hợp backend, thêm lớp gọi API ở module phụ trách và ánh xạ phản hồi sang type tương ứng trước khi đưa vào màn hình. Không gọi API trực tiếp trong component UI cơ bản.

Module tài khoản có hướng dẫn trải nghiệm và thay adapter tại [account-module.md](account-module.md). `AccountProvider` giữ phiên khi chuyển màn; `BookingProvider` đặt lại dữ liệu mẫu khi đổi tài khoản để tránh hiển thị dữ liệu của phiên trước.

## Thêm màn hình mới

1. Tạo `src/modules/<module>/screens/<ten>-screen.tsx` và export mặc định component màn hình.
2. Tạo file route tương ứng trong `src/app/`, chỉ export màn hình:

   ```tsx
   export { default } from "@/modules/flights/screens/example-screen";
   ```

3. Nếu cần modal hoặc cấu hình navigator, khai báo trong `src/app/_layout.tsx`. Điều hướng bằng Expo Router.
4. Dùng alias `@/` cho import từ gốc `src`. Không import màn hình qua `src/app` từ module.
5. Giữ `shared` độc lập với module nghiệp vụ; module có thể dùng `shared` và các type/component cần thiết từ module liên quan. Không thêm component hoặc helper vào thư mục route.
6. Chạy `npm run lint` và `npm run typecheck`. Khi đổi đường dẫn file/asset hoặc routing, kiểm tra đóng gói bằng `npx expo export --platform web`.

Quy tắc route dựa trên [tài liệu Expo Router](https://docs.expo.dev/router/basics/core-concepts/).

Luồng mới: `/payos` mô phỏng kết quả thanh toán; `/bookings` hiển thị lịch sử theo tài khoản và tiếp tục thanh toán booking đang chờ.
