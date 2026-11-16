# Chạy Cloudflare và đăng ký webhook PayOS bằng một lệnh

## Cách dùng

1. Chạy MariaDB và backend tại `http://localhost:8080` trước. Backend cần được nạp
   các biến môi trường PayOS/mã hóa như khi chạy bình thường; Spring Boot không tự
   đọc `.env`. Script này kiểm tra backend, không tự khởi động backend hoặc DB.
2. Kiểm tra `backend/.env` có `PAYOS_CLIENT_ID` và `PAYOS_API_KEY` của đúng kênh
   thanh toán. Backend cần `PAYOS_CHECKSUM_KEY` của cùng kênh để xác minh webhook.
3. Nhấp đúp `start-payos-demo.cmd` ở thư mục gốc SkyGo. Một cửa sổ Windows
   PowerShell mới mở ra, tìm `cloudflared.exe` trong PATH hoặc
   `%USERPROFILE%\Downloads\cloudflared-windows-amd64.exe`, rồi mở Quick Tunnel.
4. Script tự đọc URL `https://...trycloudflare.com`, đợi kết nối được đăng ký và
   gọi `POST https://api-merchant.payos.vn/confirm-webhook` với URL đầy đủ
   `/api/v1/payos/webhook`. Có retry nếu DNS/đường truyền chưa sẵn sàng.
5. Khi thấy `PayOS webhook registration succeeded`, bắt đầu demo. Giữ cửa sổ này
   mở. Nhấn **Ctrl+C** để dừng đường hầm do script tạo.

Có thể chạy từ bất kỳ thư mục PowerShell nào:

```powershell
& 'Q:\Ki_07\Mobile\Flight\SkyGo\start-payos-demo.cmd'
```

Script ưu tiên một cặp khóa đầy đủ trong `backend/.env`; nếu thiếu, dùng cặp
`PAYOS_CLIENT_ID`/`PAYOS_API_KEY` từ môi trường của tiến trình. Không trộn hai nguồn,
không in khóa ra màn hình/log và không đưa khóa vào tham số tiến trình Cloudflare.
Không tạo QR hoặc thực hiện giao dịch thanh toán trong bước đăng ký webhook.

## Cập nhật frontend

Mặc định script in dòng cần chép vào `frontend/.env.local`:

```env
EXPO_PUBLIC_API_URL=https://URL-MOI.trycloudflare.com
```

Để script tự cập nhật dòng này và giữ nguyên các biến khác:

```powershell
& 'Q:\Ki_07\Mobile\Flight\SkyGo\start-payos-demo.cmd' -UpdateFrontendEnv
```

Khởi động lại Expo sau khi sửa. Không cần đổi URL frontend nếu app vẫn gọi backend
qua localhost/IP LAN; đường hầm khi đó chỉ dùng để nhận webhook.

## Khi đăng ký tự động chưa thành công

Script giữ đường hầm hoạt động và in chính xác URL cùng hướng dẫn Postman:

```http
POST https://api-merchant.payos.vn/confirm-webhook
Content-Type: application/json
x-client-id: <PAYOS_CLIENT_ID trong backend/.env>
x-api-key: <PAYOS_API_KEY trong backend/.env>
```

Chọn Authorization **No Auth**, Body **raw / JSON**:

```json
{"webhookUrl":"https://URL-MOI.trycloudflare.com/api/v1/payos/webhook"}
```

Kiểm tra backend còn chạy, checksum key khớp kênh PayOS, endpoint webhook không bị
chặn. HTTP 401/403 khi đăng ký cần kiểm tra Client ID/API Key. Không lấy URL webhook
để điền vào PAYOS_BASE_URL/RETURN_URL/CANCEL_URL: đây là các cấu hình khác nhau.

## Xem lại URL và log

Thông tin phiên mới nhất ở `backend/target/payos-demo/latest.json`, gồm tunnel URL,
webhook URL, trạng thái đăng ký, PID và trạng thái `running`. URL chỉ còn dùng được
khi `running=true` và tiến trình Cloudflare vẫn hoạt động. Mỗi phiên có thư mục log
riêng bên dưới `backend/target/payos-demo/`; thư mục target được Git bỏ qua.

Mỗi lần mở lại Quick Tunnel có URL mới; script tự đăng ký lại. Không mở hai phiên
script cho cùng thư mục trạng thái: mutex ngăn hai lần khởi chạy thay webhook của
nhau. Script chỉ dừng tiến trình Cloudflare mà chính nó đã tạo.

## Tham số nâng cao

Có thể chạy trực tiếp trong PowerShell hiện tại:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-payos-demo.ps1 `
  -CloudflaredPath 'C:\path\cloudflared.exe' -BackendUrl 'http://localhost:8080'
```

`-EnvFile` chọn file khóa khác; `-SkipRegistration` chỉ mở đường hầm và in cách
đăng ký thủ công; `-UpdateFrontendEnv` cập nhật frontend; `-TunnelTimeoutSeconds`
điều chỉnh thời gian đợi tunnel. ExecutionPolicy Bypass trong launcher chỉ áp dụng
cho tiến trình PowerShell mới, không đổi chính sách toàn máy.

## Kiểm thử script

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\tests\test-payos-demo.ps1
```

Dùng executable Cloudflare giả lập và HTTP giả lập, không gọi PayOS thật. Bao gồm
đọc dotenv, trích URL, cập nhật frontend, xác nhận đúng kênh/URL, retry, fallback
không lộ khóa, timeout, đóng tiến trình con và backend chưa chạy. Không cần chạy lại
test Java vì script không thay đổi backend Java/schema DB.

Tài liệu gốc: [Cloudflare Quick Tunnels](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/),
[API xác nhận webhook PayOS](https://payos.vn/docs/api/).
