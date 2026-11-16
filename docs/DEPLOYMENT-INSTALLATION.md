# Mục 5 – Kế hoạch build iOS và phân phối SkyGo qua TestFlight

Tài liệu này trình bày trạng thái hiện tại, kế hoạch **build một file iOS `.ipa`** để demo trong buổi báo cáo chính thức **21/11/2026**, và định hướng **phân phối bản thử nghiệm qua Apple TestFlight** sau đó. Nội dung tương ứng mục 5 – Deployment trong file `MotaDetai_SDLC.txt` ở folder Flight. Các bước dưới đây là **hướng dẫn dự kiến thực hiện**, không phải biên bản đã triển khai.

## 1. Trạng thái tại thời điểm nộp báo cáo

| Nội dung | Trạng thái ngày 12/11/2026 |
| --- | --- |
| Source code frontend, backend và hướng dẫn chạy | Đã có trong repository SkyGo |
| Build file `.ipa` và cài trên iPhone để demo ngày 21/11 | **Dự kiến thực hiện trước buổi báo cáo**, chưa ghi nhận kết quả build/cài đặt |
| Phân phối bản iOS cho người thử qua TestFlight | **Định hướng Deployment sau buổi báo cáo**, chưa thực hiện |
| Biên bản triển khai chính thức và ảnh/log minh chứng | Chưa có; sẽ bổ sung sau khi chạy trên thiết bị thật |

SkyGo hiện vẫn là source code có thể chạy trong môi trường phát triển. **Build** là đóng gói phần ứng dụng di động (mã React Native/JavaScript, tài nguyên và thành phần iOS) thành file `.ipa` đã ký để cài trên iPhone phù hợp; việc này **không đóng gói Spring Boot hay MariaDB** vào cùng file. **Deployment/phân phối** là đưa một bản build và dịch vụ backend cần thiết đến môi trường mà người thử nghiệm có thể sử dụng. TestFlight là kênh phân phối beta trên iPhone thật, không phải bản phát hành công khai trên App Store. Báo cáo này **không khẳng định app đã có trên TestFlight**.

## 2. Kế hoạch build file iOS và demo ngày 21/11/2026

Nhóm dự kiến dùng **EAS Build** tạo file `.ipa` dạng *internal distribution* đã ký, rồi cài qua liên kết EAS trên iPhone dùng trong buổi báo cáo. Đây là bản ứng dụng iOS được đóng gói để demo, chưa đưa lên TestFlight. Khi chạy, iPhone vẫn gọi REST API của backend Spring Boot; backend kết nối MariaDB. Nếu trình diễn thanh toán PayOS, backend còn cần HTTPS endpoint công khai để nhận webhook.

Nếu file `.ipa` đã được cài và chạy trên iPhone cùng backend/database, nhóm có thể dùng kết quả đó làm **bằng chứng chạy thử trên thiết bị iOS**. Trong báo cáo này, mốc phân phối beta qua TestFlight vẫn được ghi riêng là định hướng sau buổi báo cáo.

```text
iPhone cài bản iOS demo ── REST API ── Spring Boot ── MariaDB
                                       │
                                       └── HTTPS webhook PayOS (nếu demo thanh toán)
```

Điều kiện cần kiểm tra trước khi hứa demo trên iPhone: tài khoản Expo, quyền dùng **Apple Developer Program** để ký và cài bản iOS nội bộ, iPhone đã đăng ký cho ad hoc provisioning, và backend có địa chỉ mà iPhone truy cập được. Repo hiện có `frontend/app.json` với iOS bundle identifier `com.skygo.app`, nhưng **chưa có `frontend/eas.json`**; cấu hình EAS và bản build vẫn là việc cần làm. Expo Go trên App Store không hỗ trợ dự án SDK 58 này, nên kế hoạch demo trên iPhone dùng bản build iOS của dự án. Xem [hướng dẫn tương thích Expo Go](https://docs.expo.dev/troubleshooting/expo-go-version-mismatch/) và [hướng dẫn EAS internal distribution](https://docs.expo.dev/build/internal-distribution/).

### Các bước dự kiến để chuẩn bị bản demo

1. Chốt commit dùng cho buổi báo cáo, kiểm tra `git status --short`, chạy các test liên quan và chuẩn bị tài khoản/dữ liệu demo giả.
2. Chuẩn bị MariaDB và backend trên máy demo theo [hướng dẫn chạy source](IMPLEMENTATION-RUN.md), giữ backend hoạt động. Kiểm tra `http://localhost:8080/api/v1/regions` trả JSON.
3. Chọn `EXPO_PUBLIC_API_URL` là địa chỉ backend **iPhone truy cập được**: IP LAN của máy demo khi cùng Wi-Fi, hoặc HTTPS URL công khai phù hợp. Không dùng `localhost` của máy tính trong app iPhone. Sau khi tạo `eas.json` ở bước 4, đặt biến này trong `build.preview.env` hoặc môi trường EAS tương ứng, rồi kiểm tra giá trị thực tế trong bản build. Ví dụ phần cấu hình cần có trong profile preview (ghép vào file được tạo, không ghi đè toàn bộ file):

   ```json
   "preview": {
     "distribution": "internal",
     "env": {
       "EXPO_PUBLIC_API_URL": "http://IP-LAN-CUA-MAY-BACKEND:8080"
     }
   }
   ```

   `EXPO_PUBLIC_*` được nhúng vào ứng dụng khi build, nên chỉ đặt địa chỉ API công khai ở đây, không đặt mật khẩu hay API key. Xem [cấu hình biến môi trường của Expo](https://docs.expo.dev/guides/environment-variables/).
4. Tại `frontend/`, cài dependency bằng `npm ci`, đăng nhập Expo, chạy `npx eas-cli@latest build:configure` để tạo `eas.json`; kiểm tra profile `preview` có `distribution: internal` và điền URL ở bước 3. Đăng ký iPhone bằng `npx eas-cli@latest device:create`, rồi chạy `npx eas-cli@latest build --platform ios --profile preview`. Làm theo các bước ký iOS do EAS hướng dẫn. [Expo hướng dẫn build iOS nội bộ](https://docs.expo.dev/build/eas-json/) và [đăng ký thiết bị](https://docs.expo.dev/get-started/set-up-your-environment/?device=physical&mode=development-build&platform=ios) mô tả các điều kiện này.
5. Cài bản build từ liên kết EAS trên **đúng iPhone đã đăng ký**, mở app và thử trang chủ, tìm chuyến, tài khoản, đặt chỗ. Nếu có PayOS, chạy [hướng dẫn webhook demo](payos-demo.md) và kiểm tra trạng thái đơn qua backend. Lưu ảnh màn hình, phiên bản build, commit, URL API và kết quả thử.

**Lưu ý về tiến độ:** tạo chứng chỉ, đăng ký thiết bị và build iOS cần được thử **trước ngày 21/11**, để còn thời gian xử lý lỗi ký/cài đặt hoặc kết nối backend. Không điền “đã hoàn thành” vào báo cáo cho đến khi build thật được cài và chạy trên iPhone.

## 3. Định hướng Deployment: phân phối qua TestFlight sau buổi báo cáo

Sau demo, nhóm dự kiến phát hành **bản beta nội bộ qua TestFlight** để người thử nghiệm cài SkyGo trên iPhone từ ứng dụng TestFlight. Đây là kênh thử nghiệm của Apple, **không phải phát hành công khai trên App Store**. Theo [tài liệu Expo về TestFlight](https://docs.expo.dev/submit/testflight/) và [Apple Developer](https://developer.apple.com/testflight/), quy trình dự kiến là:

Bản `.ipa` dùng cho demo theo profile `preview` là bản ký để cài nội bộ. Để gửi lên TestFlight, nhóm sẽ tạo **bản build riêng theo profile `production`/`distribution: store`**, thay vì giả định có thể tải trực tiếp bản demo nội bộ lên App Store Connect.

1. Hoàn thiện tài khoản Apple Developer Program, quyền App Store Connect, thông tin ứng dụng và cấu hình ký iOS. Xác nhận bundle identifier/version của SkyGo và tạo app record nếu cần.
2. Chuẩn bị backend test có **HTTPS URL ổn định** mà iPhone cài qua TestFlight truy cập được; chuẩn bị MariaDB, cấu hình môi trường, dữ liệu thử và webhook PayOS nếu bật thanh toán. Không dùng IP LAN hoặc Cloudflare Quick Tunnel đổi URL liên tục làm địa chỉ API của bản beta dài hạn.
3. Cấu hình profile EAS `production` với `distribution: store`; đặt `EXPO_PUBLIC_API_URL` của môi trường test trước khi build. Tạo bản iOS cho App Store Connect bằng `npx eas-cli@latest build --platform ios --profile production`.
4. Gửi bản build lên App Store Connect bằng `npx eas-cli@latest submit --platform ios`, đợi Apple xử lý, rồi tạo nhóm thử nghiệm TestFlight và mời người thử. Nếu mời người ngoài nhóm App Store Connect, cần làm thêm quy trình duyệt beta của Apple.
5. Cài app từ TestFlight trên iPhone thử nghiệm; kiểm tra kết nối API, đặt chỗ/thanh toán theo phạm vi đã bật, ghi phản hồi và lỗi để cải tiến.

Các lệnh ở mục này chỉ là **kế hoạch**; chưa có `eas.json`, bản `.ipa`, lượt submit hay lời mời TestFlight được xác nhận trong repo/báo cáo hiện tại. Khi thực hiện, cập nhật biên bản và bằng chứng tại mục 5 của tài liệu này.

## 4. Hướng dẫn cài đặt môi trường backend cho người thử nghiệm

TestFlight chỉ phân phối **ứng dụng iOS**; để app hoạt động, backend và database vẫn phải chạy. Người vận hành môi trường test chuẩn bị Windows có JDK 21, MariaDB 11.8, Node.js/npm và source SkyGo. Tạo database rỗng; sao chép `backend/.env.example` thành `backend/.env`, điền `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` và hai khóa mã hóa riêng theo [hướng dẫn cài trên máy Windows mới](SETUP-NEW-MACHINE-WINDOWS.md). Giữ khóa ở backend, không đưa vào Git hoặc bản iOS.

Chạy từ gốc repo để Flyway tạo schema và nạp dữ liệu chuyến bay mẫu:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1 -DisableAi
```

Đợi `Started BackendApplication`, sau đó từ terminal khác:

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/regions
Invoke-RestMethod http://localhost:8080/api/v1/home
```

Hai lệnh cần trả JSON. Trên iPhone, thử cùng API qua **URL đã cấu hình trong bản build** trước khi mở luồng đặt chỗ. Nếu backend chỉ chạy trong LAN, iPhone phải cùng mạng và firewall cho phép cổng 8080; với TestFlight về sau, dùng HTTPS URL ổn định. AI/Gemini và PayOS chỉ bật khi đã có cấu hình tương ứng; thiếu PayOS thì không thử tạo QR thanh toán thật. Xem thêm [hướng dẫn backend/frontend](../frontend/docs/frontend-backend.md).

## 5. Bằng chứng cần bổ sung sau khi thực hiện

| Mốc | Bằng chứng cần ghi | Kết quả hiện tại |
| --- | --- | --- |
| Build file `.ipa` cho báo cáo 21/11 | Commit, profile EAS, build ID/link, ảnh app cài và mở trên iPhone | Chưa thực hiện |
| Kết nối môi trường demo | Phiên bản Java/MariaDB, kết quả Flyway/API, URL API đã che thông tin nhạy cảm, ảnh app tải dữ liệu | Chưa ghi nhận |
| Luồng demo | Các bước đã chạy, kết quả thực tế, lỗi còn lại; nếu dùng PayOS ghi kết quả webhook/trạng thái đơn | Chưa ghi nhận |
| TestFlight sau báo cáo | App Store Connect build, nhóm thử nghiệm, lời mời/cài đặt, phản hồi thử nghiệm | Định hướng tương lai |

Khi hoàn thành mốc nào, thay “Chưa thực hiện” bằng thời điểm, người thực hiện và minh chứng của chính mốc đó. Không dùng kết quả test tự động của [mục 4 – Testing](testing/README.md) để thay cho bằng chứng cài đặt và chạy bản iOS trên iPhone.
