# Module tài khoản — frontend mẫu

## Phạm vi

Đăng ký bằng họ tên, số điện thoại, email tùy chọn, mật khẩu và xác nhận mật khẩu. Đăng ký thành công chuyển sang đăng nhập, điền sẵn số điện thoại; không tự đăng nhập. Có xem hồ sơ, tải lại thông tin và đăng xuất về trang chủ. Không có OTP, quên mật khẩu, sửa hồ sơ hoặc đổi mật khẩu.

Chỉ thay đổi frontend. Không gọi API, Supabase hay database. Module này không triển khai hoặc thay đổi backend/MariaDB. Luồng đặt vé yêu cầu đăng nhập trước khi tiếp tục chọn chuyến/nhập hành khách; xem [booking-flow.md](booking-flow.md).

## Trải nghiệm

Từ trang chủ, nhấn biểu tượng tài khoản ở góc phải hoặc mở `/profile`.

- Tài khoản mẫu: `0912345678` / `SkyGoDemo@2027`, tên Nguyễn Văn An.
- Nút **Điền tài khoản mẫu** chỉ điền form; nhấn **Đăng nhập** để tiếp tục.
- Đăng ký một số khác rồi đăng nhập bằng mật khẩu vừa nhập; email để trống hiển thị “Chưa cung cấp”.
- Mật khẩu tối thiểu 12 ký tự, có chữ hoa, chữ thường, số và ký tự đặc biệt. Không trim mật khẩu.
- Để trống, email sai, xác nhận mật khẩu sai và số đã đăng ký đều có thông báo; lỗi không xóa nội dung form.
- Sai thông tin đăng nhập 5 lần trong 15 phút với cùng số đã chuẩn hóa: khóa số đó 15 phút. Giao diện có đếm ngược; đổi sang số khác vẫn đăng nhập được. Đăng nhập thành công xóa bộ đếm sai.

Dữ liệu chỉ nằm trong bộ nhớ: tài khoản mới, phiên, bộ đếm sai và khóa tạm thời đều đặt lại khi tải lại app. Phiên mock có thời lượng minh họa 24 giờ; hết hạn trả về trạng thái khách. Khi đăng xuất/đổi tài khoản/hết phiên, bản nháp cá nhân và yêu thích được đặt lại; đăng nhập từ khách giữ lựa chọn chuyến công khai. Booking đã xác nhận tạo được lưu riêng trên thiết bị theo tài khoản, chưa lưu ở backend. Không lưu mật khẩu vào thiết bị.

Quy tắc điện thoại của mock theo đề xuất trong API draft: số Việt Nam 10 chữ số bắt đầu 03/05/07/08/09 hoặc dạng tương ứng +84, chấp nhận khoảng trắng/dấu ngoặc/gạch ngang; chuẩn hóa về +84. Cần thống nhất quy tắc với BE khi tích hợp.

## Nối API sau này

`types.ts` khai báo `AccountService`. Context là nơi duy nhất cung cấp thao tác cho màn hình; `data/mock-account-service.ts` là adapter mẫu. Tạo adapter API thực hiện cùng interface rồi thay import `accountService` trong `context/account-context.tsx`.

| Method | Endpoint dự kiến | Kết quả sau khi bóc `{ data }` |
| --- | --- | --- |
| `register(input)` | `POST /api/v1/auth/register` | `AccountUser` |
| `login(input)` | `POST /api/v1/auth/login` | `AccountSession` |
| `me(accessToken)` | `GET /api/v1/auth/me` | `AccountUser` |
| `logout(accessToken)` | `POST /api/v1/auth/logout` | 204, không parse JSON |

Adapter ánh xạ `{ error: { code, message, details } }` sang `AccountError`, chuyển lỗi field từ `details` sang `fields`. Với 429, chuyển `Retry-After` thành `retryAt` (epoch milliseconds). Các thao tác USER gửi Bearer token. Token hết hạn hoặc bị thu hồi phải xóa session. Khi chuyển sang API thật, thêm lưu token bằng SecureStore trên native và cơ chế phù hợp cho web, khôi phục/kiểm tra phiên trước khi hiển thị trạng thái tài khoản; bỏ credential mẫu khỏi bản production. `accessToken` của mock chỉ là mã giả, không dùng để xác thực thật.

UI đã có trạng thái đang gửi, chặn gửi lặp, giữ form khi lỗi và cho thử lại; không cần thay màn hình để nối adapter.

## Kiểm tra

```sh
npm run lint
npm run typecheck
npm run test:account
npx expo export --platform web --output-dir /tmp/skygo-account-web
```

Test kiểm tra vòng đời đăng ký/đăng nhập/xem hồ sơ/đăng xuất, lỗi xác thực, số trùng sau chuẩn hóa, khóa tạm thời và phục hồi, reset bộ đếm, hết hạn phiên và bảo toàn mật khẩu. Kiểm thử giao diện trên web; vẫn cần kiểm tra bàn phím và vùng an toàn trên thiết bị iOS/Android thật.
