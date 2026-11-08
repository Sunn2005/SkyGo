# Kết nối frontend và backend SkyGo

## Phạm vi đã nối

Tài khoản, trang chủ, danh mục sân bay và tìm chuyến gọi Spring Boot thật.
Luồng chọn chuyến → giữ chỗ → báo giá → xác nhận hành khách → review → hành lý →
tạo đơn → thanh toán PayOS → trạng thái → vé/chi tiết đơn đã nối API.
Lịch sử hiển thị các ID đơn đã tạo hoặc mở trên thiết bị theo tài khoản, lấy chi tiết
và trạng thái thật từ BE; chưa đồng bộ toàn bộ lịch sử trên thiết bị khác vì chưa có
API danh sách đơn. Không lưu payload hành khách, QR hoặc token vào danh sách ID này.

Bộ chọn điểm đến tải toàn bộ danh mục sân bay (có phân trang), bỏ sân bay đang là
điểm đi. Không gửi `origin` để lọc theo tuyến đang mở bán. Tuyến/ngày không có
chuyến sẽ trả kết quả rỗng khi tìm kiếm.

### Luồng booking thật trên Expo Go

- Chọn hạng vé yêu cầu đăng nhập. Khứ hồi chọn chuyến đi trước rồi chuyến về.
- FE đọc giữ chỗ hiện tại trước khi tạo/cập nhật. Giữ nguyên hạn BE, không tự gia hạn.
  Hold chưa gắn đơn dùng PUT `/seat-holds/{id}/selection` để thay toàn bộ hành trình,
  hạng vé và số khách nguyên tử, gửi `expectedVersion` từ GET current. Đổi chuyến đi
  yêu cầu chọn lại chuyến về. Khởi động lại app không xóa hold trên BE.
- Hold đã gắn đơn có QR: FE yêu cầu xác nhận đóng QR rồi POST `.../switch-hold`.
  Cùng thao tác thử lại trong phiên giữ nguyên key/body; đơn cũ được lưu trong lịch sử,
  hold mới giữ hạn cũ. Khi phản hồi chưa rõ, phải thử lại đúng lựa chọn vừa gửi.
  Nếu đơn chưa có QR, mở đơn hiện tại để tạo/kiểm tra QR trước khi chuyển.
- Xung đột hold version yêu cầu đọc lại current và chọn/xác nhận lại; không tự gửi đè.
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

### Thanh toán PayOS

- Sau POST bookings, mở màn QR và GET `/api/v1/bookings/{bookingId}` để đọc trạng thái.
  Đơn còn được thanh toán mới gọi POST `.../payment-link`, không gửi số tiền từ FE.
- QR được tạo cục bộ từ chuỗi VietQR của BE bằng thư viện JavaScript `qrcode-generator`,
  dùng được trong Expo Go. Nút Lưu mã QR chuyển ảnh thành PNG bằng Expo ImageManipulator;
  web tải file `.png`, iOS/Android lưu vào thư viện ảnh. Không còn nút mở PayOS,
  chia sẻ link hoặc kiểm tra trạng thái thủ công; app tiếp tục tự kiểm tra trạng thái.
- Poll GET chi tiết mỗi 5 giây sau request trước, dừng khi rời màn hình/chạy nền,
  đã thanh toán, hết hạn hoặc cần đối soát. Quay lại app đọc lại trạng thái.
- Chỉ mở màn thành công khi bookingStatus PAID và payment.status SUCCEEDED.
  Nút kiểm tra/URL chuyển hướng không tự xác nhận thanh toán. FE không gọi webhook.
- Đồng hồ dùng serverTime + thời gian đã trôi qua, ẩn QR khi hết hạn hoặc link
  không khớp booking/hold/payment/tổng tiền/tiền tệ. Không dùng QR mô phỏng cho đơn thật.
- Mở chi tiết chỉ GET booking. Khi nhấn tiếp tục, FE đọc trạng thái mới nhất rồi
  POST `.../resume-payment` nếu cần giữ lại chỗ; sau đó GET chi tiết và lấy link.
  Có hold khác chưa gắn đơn thì yêu cầu xác nhận, gửi `expectedSeatHoldId` và
  `expectedVersion`. Thiếu ghế giữ đơn/lựa chọn hiện tại; có giao dịch cần đối soát
  hoặc chưa xác minh được thì không yêu cầu thanh toán thêm.
- Booking detail đọc `requiresRehold`, `canResumePayment`, `bookingClosesAt`,
  `cancelledAt`, `cancellationReason`. AWAITING_REHOLD/hold RELEASED hiển thị cần giữ
  chỗ lại; CANCELLED hoặc đã qua cutoff ẩn QR/nút tiếp tục. Không coi hết 15 phút là hủy đơn.
- PAYOS_NOT_CONFIGURED hiển thị lỗi cấu hình; PAYMENT_LINK_UNVERIFIED /
  PAYMENT_STATUS_UNVERIFIED / PAYMENT_RECONCILIATION_REQUIRED không tạo lại link tự động.
  BE cần đủ cấu hình PayOS, return/cancel URL và webhook truy cập được.
- Chi tiết/vé lấy từ snapshot BE, giấy tờ đã che. Hiển thị số vé và invoiceId
  khi đã được cấp, quyền lợi hành lý theo từng người/chiều; không giả định trọng lượng
  mà GET booking không trả về. Lịch sử chỉ lưu tối đa 30 ID gần đây của từng tài khoản.

Kiểm tra tích hợp 07/11/2026: typecheck, lint và 42 test đạt; Expo export web/iOS/
Android đạt. Browser QA với API giả lập đã đi qua guest chọn vé → đăng nhập → hành
khách → review → tạo đơn → QR → cập nhật PAID/SUCCEEDED → số vé/hóa đơn. Reload,
đăng nhập lại và mở đơn từ lịch sử hoạt động. Chưa thực hiện giao dịch ngân hàng
hoặc xác minh webhook PayOS thật trên môi trường của người dùng.

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
- Màn kết quả gọi POST `/api/v1/flights/search/nearby-days`, hiển thị 7 ngày quanh
  ngày tìm với số chuyến từng ngày, khóa tab `selectable=false`. Đổi tab không đổi
  range hoặc gọi batch lại. Tải thêm riêng từng ngày qua `/flights/search` với
  `page >= 1`, `size=10`, nối theo flightId và chỉ tăng trang sau thành công.
  Chọn vé ở tab ngày nào sẽ cập nhật ngày hành trình đó, kể cả sau khi đăng nhập.
  Hiển thị giờ địa phương từ offset và đúng hạng vé BE trả về; giá là một người
  lớn đã VAT, không phải tổng tiền gia đình.
- Giữ chỗ và tạo đơn đã được nối trong luồng hiện tại; xem phần phạm vi ở đầu tài liệu.

Kiểm tra sau lần nối mới: 6 test API adapter đạt, typecheck đạt, lint chỉ còn 2 warning cũ của PayOS. Bản BE đang chạy ở 8080 trả 401 cho endpoint mới; cần restart BE sau pull. Đã chạy tạm code BE mới ở 8082 và xác nhận regions/airports/search trả 200, CORS đúng; chiều RETURN ngày 26/11/2026 trả HAN → SGN lúc 10:30, Economy 3.564.000đ và Business 8.910.000đ. Các ngày không có lịch trả danh sách rỗng. Bản tạm được tắt sau kiểm tra.

## Cập nhật theo BE ngày 08/11/2026

Đã nối selection version (V9), switch-hold và vòng đời đơn (V10), xác nhận thay
hold khi resume-payment, tìm chuyến 7 ngày và tải thêm từng ngày. Test mới kiểm tra
lost response dùng lại key/body, xác nhận hold/version, đổi phiên không phát sinh
mutation, ngày bị khóa, request tải thêm và cutoff thanh toán. Kiểm tra lần này
bằng API giả lập; 50 test đạt, typecheck/lint sạch, Expo export web/iOS/Android đạt.
Chưa chạy giao dịch ngân hàng hoặc xác minh với BE/DB SkyGo thật. Lúc kiểm tra,
cổng 8080 thuộc backend ToeicPracticeWeb; cần chạy đúng BE SkyGo và đặt
EXPO_PUBLIC_API_URL theo địa chỉ/cổng đó trước khi kiểm thử tích hợp.

## Route quay lại từ PayOS

- `skygo://payment/success` → `/payment/success`.
- `skygo://payment/cancel` → `/payment/cancel`.
- Còn phiên và đơn đang mở: đọc lại GET booking, tiếp tục kiểm tra mỗi 5 giây;
  chỉ sang màn thành công khi BE trả PAID + SUCCEEDED. Callback không tạo QR mới,
  giữ chỗ lại, tự đánh dấu thanh toán hoặc tự hủy đơn.
- Chưa đăng nhập: đăng nhập rồi quay lại đúng route. App vừa khởi động lại/chưa có
  đơn đang mở: chọn đơn trong lịch sử để kiểm tra; không suy đoán booking từ
  orderCode/status/cancel trong URL và không dùng callback để xác nhận thanh toán.
- Scheme `skygo://` cần bản app native đã cài có scheme SkyGo (development build
  hoặc bản phát hành). Expo Go dùng URL `exp://<host>:<port>/--/payment/success`
  và `/--/payment/cancel`; phải đặt return/cancel URL tương ứng trong BE rồi restart
  BE nếu muốn test chuyển hướng trong Expo Go. Có thể dùng HTTP(S) `/payment/success`
  và `/payment/cancel` khi chạy web. Việc thêm route không thay thế webhook PayOS.

## Lưu QR dưới dạng PNG

Nút Lưu mã QR chuyển nội dung QR đang hiển thị thành PNG trước khi lưu. iOS dùng
quyền thêm ảnh (NSPhotoLibraryAddUsageDescription); Android 10+ chỉ thêm ảnh của
app, không cần đọc thư viện ảnh. Android cũ xin quyền ghi khi người dùng bấm lưu.
Không lưu QR khi đơn hết hiệu lực/đã thanh toán. Có trạng thái đang lưu và thông báo
thành công/lỗi. Expo Go có sẵn ImageManipulator/MediaLibrary; bản native riêng cần
build lại sau khi thêm hai thư viện. iOS đã thêm mô tả quyền trong Info.plist và
cấu hình plugin tương ứng cho lần prebuild sau.

## Giao diện thanh ngày mở rộng

Thanh ngày được cố định ngay dưới tiêu đề/tuyến bay, không nằm trong phần cuộn
kết quả. Giới hạn chiều cao để tránh ScrollView ngang giãn cao trên điện thoại.
Ngày đang chọn có chữ xanh và gạch chân, tự cuộn vào vùng nhìn thấy; ngày đã qua được ẩn.
Vuốt đến cuối hoặc nhấn mũi tên/Thêm ngày tải thêm batch 7 ngày qua nearby-days.
Mũi tên trái tải cửa sổ trước với phần giao nhau để tránh gửi ngày trung tâm quá khứ.
Các batch được gộp theo ngày và giữ các trang chuyến đã tải, không ghi đè khi trùng.
Giới hạn ngày đi/về vẫn do BE quyết định; không cố định thanh ngày chỉ 7 ngày.

Đã kiểm tra giao diện web với API BE thật qua proxy local tạm: chọn 09/11 hiển thị
SGTEST10K 10.000đ; tải thêm mở rộng dải tới 26/11 mà giữ ngày 09/11 đang chọn.

## Header màn kết quả theo bố cục tham khảo

Màn kết quả dùng header riêng với safe area: nút quay lại, hai thành phố, biểu tượng
máy bay, ngày/số khách, cụm hỗ trợ/trang chủ. Bấm tuyến bay để đổi tìm kiếm. Dùng
màu xanh SkyGo, thanh ngày phẳng có gạch chân ngày được chọn và số chuyến phía trên.
Bỏ khối ngày + số chuyến lặp dưới thanh ngày. Hai nút chọn Khởi hành sớm nhất/Giá thấp nhất nằm trong vùng đầu
cố định, dùng component FlightSortControl; sắp xếp áp dụng cho các chuyến đã tải.
Màn này bỏ Bộ lọc và không còn áp dụng các bộ lọc ẩn từ trạng thái cũ.
Đã kiểm tra web với BE thật: chọn 08/11 chỉ còn thông tin ngày ở header/thanh ngày,
chọn Giá thấp nhất đóng bảng lựa chọn và cập nhật tên nút.

## Chọn ngày sinh bằng cuộn

Bấm biểu tượng lịch tại Ngày sinh mở ba cột Năm – Tháng – Ngày có cuộn và dừng
ở từng dòng. Chỉ lưu khi bấm Chọn ngày này; đóng lịch bỏ bản nháp. Giữ phạm vi
1900 đến hôm nay, tự điều chỉnh 29/30/31 khi đổi tháng/năm và không cho chọn ngày
sinh trong tương lai. Các kiểm tra nhóm tuổi vẫn chạy khi xác nhận hành khách.
Đã kiểm tra UI web: cuộn tháng, đổi 31/01/1990 thành 28/02/1990, xác nhận và mở lại
đúng ngày đã lưu. DateField cho ngày hết hạn vẫn dùng lịch hiện có.

## Xem đặt chỗ và vé sau thanh toán

- Toàn bộ thẻ Chuyến bay sắp tới ở trang chủ bấm được: chọn đúng bookingId từ
  `/home`, mở `/booking` và GET chi tiết bằng token tài khoản. Có CTA Xem chi tiết và vé.
- Lịch sử có các mục Tất cả/Đã xác nhận/Chờ thanh toán; thẻ hành trình hiển thị trạng
  thái, ngày/giờ, sân bay, hạng vé, số khách và tổng tiền. Đọc đồng thời các đơn đã
  lưu và home; thêm chuyến sắp tới của tài khoản nếu chưa có ID trên thiết bị.
  BE chưa có API đầy đủ danh sách đơn nên không tuyên bố đồng bộ toàn bộ lịch sử.
  Lỗi mạng/đơn tải lỗi có nút thử lại và không làm mất các đơn còn đọc được.
- Màn thành công chỉ dùng PAID + SUCCEEDED, có mã đặt chỗ, hành trình, hành khách,
  số vé thật và nút Xem chi tiết vé. Không dùng callback để giả xác nhận thanh toán.
- Chi tiết ưu tiên trạng thái, hành trình, số vé, giấy tờ che và hành lý. Tổng tiền
  nổi bật; giá/giao dịch/hóa đơn và liên hệ có thể mở rộng. Nút cuối màn hình phụ
  thuộc trạng thái: tiếp tục/giữ chỗ lại, liên hệ đối soát hoặc về trang chủ.
- Nhãn tiếng Việt phân biệt chờ thanh toán, cần giữ chỗ lại, đối soát, quá hạn và
  hủy. Giờ bay dùng snapshot giờ địa phương từng sân bay; không suy đoán nhà ga/ghế.
- QA bằng API giả lập: home → đúng đơn, success → chi tiết vé, mở thanh toán,
  lịch sử có đơn xác nhận/chờ trả tiền, chuyển mục chờ thanh toán và mở đúng đơn;
  đơn chưa thanh toán không hiển thị số vé. Không tạo giao dịch ngân hàng trong QA.
