# Mục 6 – Maintenance & Improvement: ghi nhận lỗi và đề xuất cải tiến SkyGo

Tài liệu này đáp ứng mục **6. Maintenance & Improvement** trong file `MotaDetai_SDLC.txt` ở folder Flight. Tính đến **12/11/2026**, nhóm **chưa ghi nhận lỗi chức năng nào khi sử dụng app**. Đây là trạng thái ghi nhận hiện tại, không phải kết luận rằng mọi môi trường và luồng tích hợp bên ngoài đã được kiểm tra hết. Các đề xuất bên dưới là công việc **dự kiến**, chưa triển khai trong source code.

## 1. Tình trạng hiện tại và nguồn đối chiếu

| Nội dung | Ghi nhận |
| --- | --- |
| Lỗi còn mở đã xác nhận | Chưa có mục nào |
| Kết quả tự động gần nhất | [Báo cáo kiểm thử 11/11/2026](testing/test-report-2026-11-11.md) ghi backend 278 pass, 0 fail, 2 skip; kết quả này chỉ áp dụng cho lần chạy và phạm vi đã ghi trong báo cáo |
| Kiểm thử thủ công trên Expo Go | Báo cáo trên chưa có kết quả thực thi đầy đủ; bổ sung khi nhóm chạy trên thiết bị demo |
| Kiểm thử PayOS/Gemini thật | Phụ thuộc môi trường/khóa dịch vụ; chỉ ghi là đã xác minh khi có kết quả thực tế, không suy ra từ test dùng gateway hoặc dữ liệu giả |

**Hạn chế đã biết, không ghi thành bug:** lịch sử booking trong frontend hiện nhớ các ID đơn đã tạo/mở trên **thiết bị đó** theo tài khoản; chưa có API danh sách để đồng bộ toàn bộ lịch sử giữa các thiết bị. Ghi nhận này giúp phân biệt phạm vi sản phẩm với lỗi phát sinh.

## 2. Sổ ghi nhận lỗi khi phát sinh

Hiện sổ lỗi chưa có dòng lỗi đã xác nhận. Khi phát hiện lỗi, thêm một dòng và giữ lịch sử trạng thái; không điền lỗi giả chỉ để đủ mục báo cáo.

| Mã | Ngày phát hiện | Môi trường/commit | Mức độ | Bước tái hiện | Mong đợi / thực tế | Trạng thái | Người xử lý / bản sửa |
| --- | --- | --- | --- | --- | --- | --- | --- |
| *Chưa có lỗi được xác nhận* | — | — | — | — | — | — | — |

Quy trình xử lý: ghi bước tái hiện và dữ liệu thử đã che thông tin cá nhân → đối chiếu log frontend/backend và trạng thái DB → xác định nguyên nhân → sửa ở nhánh code → chạy lại test liên quan và luồng trên thiết bị → ghi commit, kết quả và đóng lỗi. Với lỗi thanh toán/giữ chỗ, kiểm tra cả trạng thái booking, payment và seat hold trước khi chạy lại yêu cầu; không tự sửa dữ liệu DB để làm kết quả trông đúng. Mật khẩu, token, khóa PayOS/Gemini và số giấy tờ đầy đủ không đưa vào sổ lỗi.

## 3. Đề xuất cải tiến

Các việc dưới đây được ưu tiên sau khi hoàn thành demo và đo số liệu gốc. Thứ tự có thể đổi theo kết quả đo; không coi các mục này là lỗi hiện có.

| Mã | Ưu tiên dự kiến | Cải tiến | Mục tiêu |
| --- | --- | --- | --- |
| IMP-01 | Trung bình | Cache dữ liệu phù hợp ở backend | Giảm truy vấn DB và thời gian tải danh mục ít thay đổi |
| IMP-02 | Trung bình | Cache có kiểm soát ở frontend | Giảm tải lại danh mục, cải thiện cảm giác phản hồi khi quay lại màn hình |
| IMP-03 | Cao khi lưu lượng tăng | Rate limit thao tác giữ chỗ | Hạn chế tạo/sửa giữ chỗ quá dày và bảo vệ tài nguyên ghế |

### IMP-01 – Cache backend

- Bắt đầu từ dữ liệu công khai, ít thay đổi như khu vực và danh mục sân bay (`GET /api/v1/regions`, danh sách sân bay). Đo thời gian phản hồi và số truy vấn DB trước khi thêm cache; sau đó chọn thời gian sống và cơ chế làm mới/xóa cache khi dữ liệu danh mục thay đổi.
- Khóa cache phải gồm mọi tham số làm thay đổi kết quả, ví dụ từ khóa, trang và kích thước trang của danh sách sân bay. Không dùng một bản kết quả cho các truy vấn khác nhau.
- Chưa cache toàn bộ kết quả tìm chuyến chỉ vì API được gọi nhiều: số ghế, trạng thái bán và giá có thể thay đổi. Nếu muốn cache một phần tìm kiếm, cần tách dữ liệu tuyến/hiển thị ít đổi khỏi số ghế/giá chốt và luôn kiểm tra lại trong giao dịch giữ chỗ.
- Không cache chung dữ liệu theo tài khoản: phiên đăng nhập, hold, review, booking, payment, vé và lịch sử cá nhân. Các API đang trả `Cache-Control: no-store` cần giữ nguyên ý nghĩa đó. Quy tắc HTTP `no-store` được mô tả trong [RFC 9111](https://www.rfc-editor.org/rfc/rfc9111.html); Spring có cơ chế khai báo cache và xóa cache trong [tài liệu chính thức](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html).

**Hoàn thành khi:** kết quả danh mục đúng sau cập nhật/invalidation, không rò dữ liệu giữa tài khoản, độ trễ và số truy vấn DB được so sánh trước/sau trên cùng bộ dữ liệu, không thay đổi kết quả nghiệp vụ giữ chỗ.

### IMP-02 – Cache frontend

- Cache ngắn hạn trong bộ nhớ cho danh mục khu vực/sân bay đã tải, dùng cùng khóa với tham số truy vấn. Gộp các request giống nhau đang chờ để tránh gọi lặp khi người dùng quay lại màn hình hoặc gõ/tải nhanh.
- Khi dữ liệu hết hạn, ứng dụng có thể hiển thị danh mục đã có trong lúc tải lại; nếu tải lại thất bại, thông báo rõ thời điểm dữ liệu cũ và cho phép thử lại. Xóa cache liên quan khi đổi môi trường API; dữ liệu gắn tài khoản phải được xóa khi đăng xuất.
- Không dùng cache frontend làm nguồn quyết định còn ghế hoặc tổng tiền thanh toán. Trước khi tạo/đổi hold và tạo booking, tiếp tục dùng phản hồi mới của backend. Không lưu token, hành khách, giấy tờ, QR hoặc dữ liệu thanh toán vào cache danh mục.

**Hoàn thành khi:** quay lại màn danh mục giảm request lặp; đổi từ khóa/trang vẫn ra đúng kết quả; khởi động lại app, đăng xuất hoặc đổi API URL không hiển thị dữ liệu sai tài khoản/môi trường; luồng đặt chỗ vẫn lấy chỗ và giá từ backend.

### IMP-03 – Rate limit thao tác giữ chỗ

- Phạm vi đầu tiên: các thao tác ghi trên `/api/v1/seat-holds` gồm `POST` tạo hold và các `PUT` đổi selection/leg/số hành khách. Đánh giá thêm `switch-hold`, `open-for-payment`, `resume-payment` vì chúng cũng có thể tạo/đổi giữ chỗ. `GET /seat-holds/current` nên có chính sách đọc riêng nếu cần, không dùng cùng ngưỡng với thao tác ghi.
- Giới hạn chủ yếu theo **tài khoản đã xác thực**; cân nhắc thêm tầng IP để giảm lưu lượng bất thường trước xác thực. Ngưỡng và khoảng thời gian phải lấy từ số liệu sử dụng bình thường, có cấu hình để điều chỉnh. Nếu backend chạy nhiều instance, bộ đếm cần nhất quán giữa các instance.
- Yêu cầu vượt ngưỡng trả HTTP `429`, mã lỗi có cấu trúc và header `Retry-After`; frontend đã có xử lý thông điệp 429 và đọc `Retry-After` trong API client. Theo [RFC 6585](https://www.rfc-editor.org/rfc/rfc6585.html#section-4), 429 biểu thị quá nhiều yêu cầu và có thể kèm thời gian thử lại. Giao diện không tự gửi lại ngay hoặc tạo hold khác trong lúc chờ.
- Chặn **trước** khi gọi service làm thay đổi inventory. Request bị chặn không được trừ ghế, gia hạn hold hoặc tạo booking. Rate limit không thay thế xác thực, khóa giao dịch, kiểm tra số ghế, version hay cơ chế chống gửi lặp đã có.

**Hoàn thành khi:** thao tác bình thường không bị chặn; chuỗi gọi dày vượt ngưỡng trả 429 và thời gian chờ đúng; request 429 không thay đổi DB; sau thời gian chờ người dùng thao tác tiếp được; các test đồng thời vẫn giữ đúng số ghế và hạn hold 15 phút.

## 4. Cách đo và xác nhận cải tiến

Trước khi sửa, ghi cùng một bộ dữ liệu, máy test và kịch bản tải: thời gian phản hồi trung vị/p95, số request và truy vấn DB cho danh mục, tỷ lệ request lặp trên frontend, số lần tạo/đổi hold và số lỗi. Sau mỗi cải tiến, chạy lại cùng kịch bản và ghi kết quả so sánh. Với cache, thêm ca dữ liệu cập nhật và dữ liệu theo tài khoản. Với rate limit, thêm ca nhiều request song song, hết thời gian chờ, hai tài khoản độc lập và nhiều backend instance nếu triển khai kiểu đó.

Kết quả kiểm thử được cập nhật vào [tài liệu Testing](testing/README.md); bản mã được đưa lên máy demo và bằng chứng thực tế cập nhật vào [tài liệu Deployment](DEPLOYMENT-INSTALLATION.md). Chỉ đổi trạng thái mục cải tiến từ **đề xuất** sang **đã thực hiện** sau khi có commit, kết quả test và đo trước/sau.
