# Quy ước quản lý Use Case — SkyGo

Tài liệu này quy định cách chia module, cấp mã và quản lý business rule, câu hỏi mở và Use Case trong bộ `docs/usecase_spec/`. Format nội dung từng Use Case được quy định tại [STANDARD.md](./STANDARD.md).

## 1. Nguồn sự thật và phạm vi

- Phạm vi nghiệp vụ MVP theo [kế hoạch triển khai trong folder Flight](../../../Ke-hoach-trien-khai.md).
- Bộ Markdown trong `docs/usecase_spec/` là nguồn đặc tả Use Case và business rule.
- README giao diện và prototype hiện có chỉ dùng để hiểu bối cảnh UI; không dùng để mở rộng phạm vi nghiệp vụ MVP.
- Chưa có quyết định thì ghi thành câu hỏi mở (OQ). Không tự điền quy tắc thanh toán, giá, thời hạn giữ booking hoặc chính sách khác.

## 2. Ranh giới hệ thống và actor

- **User:** người dùng ứng dụng; actor chính trong các luồng đăng ký, đăng nhập, tìm chuyến, đặt chỗ, thanh toán và xem lịch sử.
- **PayOS:** hệ thống ngoài tham gia luồng thanh toán và gửi webhook để backend xác minh kết quả.
- Backend, cơ sở dữ liệu, frontend và các service nội bộ là thành phần của hệ thống SkyGo; không tạo actor hoặc Use Case độc lập chỉ vì một bước được xử lý ở thành phần đó.
- Webhook là một phần trong luồng thanh toán, không phải Use Case riêng nếu không có mục tiêu nghiệp vụ độc lập của actor.

## 3. Chia module

Module được chia theo năng lực nghiệp vụ ổn định, không chia theo frontend/backend/API/database hoặc theo từng màn hình.

| Tiền tố | Module | Phạm vi dự kiến |
|---|---|---|
| `AUTH` | Tài khoản | Đăng ký, đăng nhập, đăng xuất; xác thực các thao tác được bảo vệ. |
| `FLT` | Tìm chuyến bay | Tìm chuyến một chiều, xem chi tiết chuyến bay. |
| `BKG` | Đặt chỗ | Tạo booking, thông tin hành khách, hành lý, xem lại; xem danh sách và chi tiết booking. |
| `PAY` | Thanh toán | Tạo yêu cầu PayOS, xử lý webhook đã xác minh và cập nhật trạng thái thanh toán. |

Các feature và Use Case cụ thể được đăng ký trong `00-module.md` và README của module tương ứng. Có thể điều chỉnh ranh giới khi phân tích nghiệp vụ, nhưng phải cập nhật danh mục và tham chiếu liên quan cùng lúc.

## 4. Mã định danh

### 4.1. Use Case

Mã có dạng `<MODULE>-UC-<NNN>`, ví dụ `AUTH-UC-001` hoặc `PAY-UC-001`.

- Tiền tố phải thuộc module đã đăng ký ở mục 3.
- Số có tối thiểu ba chữ số, tăng riêng trong từng module.
- Mã là định danh ổn định, không biểu diễn thứ tự trình bày hoặc thứ tự chạy.
- Chỉ cấp mã chính thức khi Use Case đã được chấp nhận trong danh mục module.
- Không tái sử dụng hoặc đánh lại mã đã cấp, kể cả khi Use Case bị bỏ hoặc gộp.
- Tên file: `<MODULE>-UC-<NNN>_<slug>.md`; tiêu đề trong file phải có cùng mã.

### 4.2. Business rule

Mã có dạng `BR-<MODULE>-<NNN>`, ví dụ `BR-AUTH-001`, `BR-FLT-001`, `BR-BKG-001`, `BR-PAY-001`. Luật xuyên module dùng `BR-SKYGO-NNN`.

- Mã rule phải duy nhất và ổn định.
- Rule được định nghĩa tại đúng một nguồn: rule xuyên module ở `00-module.md`; rule riêng module/feature ở file `rules.md` tương ứng.
- Use Case chỉ mô tả hành vi và tham chiếu rule; không chép lại công thức hoặc nội dung luật để tránh lệch nguồn.

### 4.3. Câu hỏi mở

Mã có dạng `OQ-<MODULE>-<NNN>` hoặc `OQ-SKYGO-NNN` cho câu hỏi chung. Câu hỏi nằm trong README của module/feature sở hữu quyết định. Không dùng OQ như một business rule đã chốt.

## 5. Xác định Use Case

1. Một Use Case mô tả mục tiêu nghiệp vụ của actor bên ngoài khi tương tác với SkyGo.
2. Không tách trạng thái, hàm nội bộ, webhook, tác vụ nền hoặc một bước trong quy trình thành Use Case độc lập nếu không có mục tiêu actor riêng.
3. Việc nhập thông tin hành khách, chọn hành lý và xem lại thường là các bước của mục tiêu tạo booking; chỉ tách khi nhóm xác định có mục tiêu nghiệp vụ độc lập.
4. UC thuộc module sở hữu mục tiêu nghiệp vụ, không nhất thiết thuộc module có nhiều bước kỹ thuật nhất.
5. Khi một UC liên quan module khác, giữ mục tiêu actor làm căn cứ sở hữu; tham chiếu UC/rule liên quan và thể hiện trao đổi xuyên module trong sequence nếu cần.
6. Trước khi gộp hai UC, so sánh mục tiêu actor, kết quả, điều kiện trước/sau và ranh giới hệ thống; không kết luận chỉ dựa vào tên giống nhau.

## 6. Một nguồn cho mỗi loại nội dung

| Nội dung | Nguồn chính |
|---|---|
| Mục tiêu, phạm vi, actor và thuật ngữ dùng chung | `00-module.md` |
| Danh sách feature/UC, trạng thái UC | `00-module.md` và README module |
| Business rule dùng chung | `00-module.md` |
| Business rule riêng module/feature và câu hỏi mở | `rules.md` và README của module/feature sở hữu |
| Điều kiện và luồng của Use Case | File Use Case tương ứng |
| Activity/Sequence source | File `.act` / `.seq` tương ứng với Use Case |

Nếu cùng một rule được dùng ở nhiều module, định nghĩa ở nguồn chung rồi tham chiếu; không sao chép thành nhiều rule độc lập.

## 7. Thay đổi và truy vết

- Khi đổi rule, rà lại mọi Use Case và sơ đồ có tham chiếu tới rule đó.
- Khi đổi mã, tên hoặc module sở hữu UC, cập nhật danh mục, tên file và toàn bộ tham chiếu; không cấp lại mã cũ.
- Khi quyết định một OQ, cập nhật trực tiếp rule/UC và xóa OQ khỏi danh sách; ghi lý do trong commit message nếu nhóm dùng Git để lưu lịch sử quyết định.
- Thay đổi phạm vi MVP phải được đối chiếu với kế hoạch triển khai trước khi cập nhật spec.
