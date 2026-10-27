# Chuẩn viết Use Case — SkyGo

Tài liệu này định nghĩa format nội dung một file Use Case. Quy ước module, mã UC/BR/OQ và vòng đời mã nằm trong [CONVENTIONS.md](./CONVENTIONS.md).

## 1. Vị trí và tên file

Use Case nằm trong `docs/usecase_spec/<module>/use-cases/`.

`<MODULE>-UC-<NNN>_<slug>.md`

Ví dụ: `AUTH-UC-001_dang-ky.md`.

Sơ đồ nguồn gắn với UC có thể đặt cạnh file hoặc trong thư mục `diagrams/` cùng module:

```text
diagrams/
  AUTH-UC-001_activity.act
  AUTH-UC-001_sequence.seq
```

Tên file sơ đồ phải chứa đúng mã UC và loại sơ đồ để công cụ có thể ghép đầu ra.

## 2. Cấu trúc file bắt buộc

```markdown
### AUTH-UC-001_Đăng ký tài khoản

### Mô tả use case AUTH-UC-001

| Trường | Nội dung |
|---|---|
| Use case: | AUTH-UC-001_Đăng ký tài khoản |
| Mục đích: | ... |
| Mô tả: | ... |
| Actor: | User |
| Tiền điều kiện: | ... |
| Hậu điều kiện: | ... |

### Luồng sự kiện chính

| User | Hệ thống |
|---|---|
| 1. ... | 2. ... |

### Luồng sự kiện phụ

| User | Hệ thống |
|---|---|
| 1.1. ... | 1.1.1. ... |

### Biểu đồ

Activity: `diagrams/AUTH-UC-001_activity.act`

Sequence: `diagrams/AUTH-UC-001_sequence.seq`
```

Các mục, tiêu đề và nhãn trường giữ ổn định để sau này có thể kiểm tra tự động.

## 3. Thông tin Use Case

- **Use case:** mã và tên đã đăng ký trong danh mục.
- **Mục đích:** kết quả actor muốn đạt, viết ngắn gọn.
- **Mô tả:** tóm tắt phạm vi tương tác, không chép toàn bộ rule.
- **Actor:** actor chính; nêu PayOS hoặc hệ thống ngoài khác nếu trực tiếp tham gia luồng.
- **Tiền điều kiện:** điều kiện phải đúng trước khi bắt đầu UC, ví dụ đăng nhập khi bắt đầu đặt chỗ.
- **Hậu điều kiện:** kết quả nghiệp vụ sau khi luồng thành công. Trạng thái thanh toán chỉ ghi theo điều đã được xác minh; không giả định trạng thái còn đang mở OQ.

Nếu tiền điều kiện hoặc hậu điều kiện gồm nhiều ý, đặt mỗi ý trên một dòng riêng và ngăn cách trong Markdown bằng `<br>`. Không thêm ký hiệu bullet hoặc dấu chấm ở cuối dòng. Không để OQ chưa chốt thành điều kiện được khẳng định.

## 4. Luồng sự kiện chính

- Dùng bảng hai cột: actor chính và `Hệ thống`.
- Đánh số các bước chính liên tục bắt đầu từ 1. Số thứ tự bước mô tả diễn tiến, không phải mã UC.
- Mỗi ô nên nêu một hành động hoặc phản hồi rõ ràng, thường tối đa 1–2 câu.
- Hành động của actor bắt đầu bằng động từ: “Chọn…”, “Nhập…”, “Xác nhận…”.
- Hệ thống nêu kết quả actor quan sát được và thay đổi nghiệp vụ cần thiết.
- Luồng chính đi tới hậu điều kiện của UC; không bỏ qua kết quả thành công.
- Nếu một hành động chỉ được phép sau xác thực, thể hiện điều kiện đăng nhập theo rule chung và luồng thay thế phù hợp.

## 5. Luồng thay thế và ngoại lệ

- Dùng cho nhánh phát sinh từ luồng chính, bao gồm dữ liệu không hợp lệ, không có chuyến, lỗi xác thực, PayOS từ chối/hủy/hết hạn, webhook không hợp lệ, lỗi mạng và yêu cầu gửi lặp khi thuộc phạm vi rule đã chốt.
- Đánh số nhánh theo bước rẽ: `N.1`, `N.2`; các bước tiếp theo trong nhánh dùng `N.1.1`, `N.1.2`.
- Bước đầu nhánh ghi rõ điều kiện và hành động/phản hồi; không để một dòng “Nếu…” trơ trọi mà không có kết quả.
- Nêu nhánh kết thúc UC, quay lại bước nào, hoặc giữ trạng thái chờ nếu đã có rule xác nhận.
- Không tự đặt hành vi cho tình huống đang là OQ. Ghi tình huống trong README của module/feature sở hữu dưới dạng câu hỏi mở cho tới khi được quyết định.

## 6. Tham chiếu business rule và UC

- Dùng cú pháp `{{ref: BR-AUTH-001}}`, `{{ref: BR-SKYGO-001}}` hoặc `{{ref: FLT-UC-001}}` ở cuối câu/ô cần truy vết.
- Chỉ tham chiếu mã đã được định nghĩa và đăng ký.
- Tham chiếu là truy vết, không thay thế nội dung bước cần thiết để hiểu luồng.
- Không chép công thức, điều kiện chi tiết hoặc danh sách lựa chọn từ `rules.md` sang UC; diễn đạt hành vi liên quan và gắn tham chiếu.
- Không để mã lịch sử hoặc ghi chú chỉnh sửa trong nội dung chuẩn của Use Case.

## 7. Activity và Sequence

### Activity

- File nguồn `.act` mô tả luồng actor/hệ thống, các quyết định, nhánh lỗi và điểm kết thúc.
- Activity dùng đúng hai swimlane mặc định: actor chính (SkyGo là `User`) và `Hệ thống`. Gộp xử lý ứng dụng, backend, cơ sở dữ liệu và service nội bộ vào lane `Hệ thống`; không tạo lane riêng cho từng tầng kỹ thuật. PayOS vẫn là participant trong Sequence; không tách thành lane Activity.
- Mọi quyết định trong sơ đồ phải tương ứng với một nhánh trong UC hoặc một rule đã chốt.

### Sequence

- File nguồn `.seq` mô tả thông điệp theo thời gian giữa actor và các thành phần cần thiết.
- Dùng đúng ý nghĩa participant của SkyGo: `actor` cho User/hệ thống ngoài; `boundary` cho giao diện người dùng; `control` cho service xử lý nghiệp vụ; `entity` cho DAO/repository truy cập dữ liệu. Không đặt trực tiếp MySQL hoặc entity nghiệp vụ vào vị trí DAO.
- Thể hiện User, giao diện (`boundary`), service (`control`) và DAO/repository (`entity`) có liên quan. PayOS là hệ thống ngoài và có thể xuất hiện như actor. DAO/repository đại diện cho tương tác lưu trữ; không cần thêm MySQL thành một participant riêng trong sequence nghiệp vụ.
- Với thanh toán, thể hiện PayOS gửi webhook đến backend và backend xác minh trước khi cập nhật trạng thái; không mô tả ứng dụng tự xác nhận thanh toán.
- Không đưa chi tiết class, hàm nội bộ hoặc endpoint chưa được thống nhất vào sơ đồ nghiệp vụ. Chi tiết hợp đồng API thuộc tài liệu API ở giai đoạn sau.

## 8. Nguyên tắc nghiệp vụ và phạm vi

- Chỉ mô tả phạm vi MVP theo kế hoạch triển khai SkyGo.
- Khứ hồi, hành trình nhiều chặng, chọn ghế, API hãng bay/thời gian thực, Manager, đăng nhập bên thứ ba, OTP/email xác thực và quên mật khẩu qua email là ngoài phạm vi MVP.
- Chỉ ghi rule đã xác nhận. Các chi tiết chưa rõ như công thức giá/thuế, thời hạn giữ booking, trạng thái PayOS, hoàn tiền và khóa đăng nhập phải tiếp tục là OQ.
- Nội dung Use Case tập trung vào hành vi nghiệp vụ; bố cục UI và chi tiết cài đặt không đưa vào đây.

## 9. Checklist rà soát UC

- [ ] UC có một mục tiêu actor rõ và có trong danh mục module.
- [ ] Tiền điều kiện, luồng chính và hậu điều kiện nhất quán.
- [ ] Bước actor bắt đầu bằng hành động; phản hồi hệ thống dễ hiểu.
- [ ] Các nhánh ngoại lệ có điều kiện, kết quả và điểm tiếp tục/kết thúc.
- [ ] Business rule được định nghĩa ở nguồn chính và mã tham chiếu hợp lệ.
- [ ] Không biến OQ thành hành vi đã chốt.
- [ ] Activity và Sequence phản ánh cùng luồng/nhánh trong UC.
- [ ] Không đưa chức năng ngoài phạm vi MVP vào đặc tả.
