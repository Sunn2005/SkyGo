# Công cụ sơ đồ và kiểm tra Use Case SkyGo

Bộ công cụ này hỗ trợ nguồn Use Case SkyGo trong `docs/usecase_spec/`. Mã UC có tiền tố module, ví dụ `AUTH-UC-001`, `FLT-UC-001`, `BKG-UC-001` và `PAY-UC-001`.

## Dùng nhanh

Chạy từ thư mục gốc `SkyGo`:

```powershell
python docs/tools/usecase_flow/build.py list
python docs/tools/usecase_flow/build.py check
python docs/tools/usecase_flow/build.py build BKG-UC-001
python docs/tools/usecase_flow/build.py build payment
python docs/tools/usecase_flow/build.py build
```

- `list`: liệt kê Use Case và trạng thái file Markdown/Activity/Sequence đã có.
- `check`: kiểm tra format Use Case, mã tham chiếu và cú pháp các file sơ đồ; không ghi vào thư mục đầu ra.
- `build`: sinh sơ đồ và DOCX cho một/một số UC, một module, hoặc toàn bộ nguồn.
- `--no-png`: chỉ sinh draw.io và SVG, không cần Playwright/Chromium.
- `--only diagrams`: chỉ sinh sơ đồ; `--only docx`: chỉ xuất DOCX từ Markdown và ảnh đã sinh trước đó.

Đầu ra được đặt tại `docs/build/usecase-flow/<module>/<UC-ID>/`: sơ đồ draw.io/SVG/PNG và DOCX cùng mã UC. Không sửa tay các file sinh ra; chỉnh nguồn Markdown, `.act` hoặc `.seq` rồi build lại.

## Cấu trúc nguồn được hỗ trợ

```text
docs/usecase_spec/
  <module>/
    <feature>/README.md
    <feature>/rules.md
    <feature>/use-cases/<MODULE>-UC-<NNN>_<slug>.md
    <feature>/diagrams/<MODULE>-UC-<NNN>_activity.act
    <feature>/diagrams/<MODULE>-UC-<NNN>_sequence.seq
```

Tìm file theo tên và mã Use Case, không cố định độ sâu thư mục. Mỗi UC chỉ có tối đa một nguồn `.md`, `.act` và `.seq` trong một module. Prefix module và thư mục sở hữu phải khớp quy ước trong `docs/usecase_spec/CONVENTIONS.md`.

## Kiểm tra nội dung

`check` kiểm tra cấu trúc Markdown, thứ tự bước chính, nhãn các mục bắt buộc, mã tham chiếu BR/UC, mã trùng và cú pháp sơ đồ. Nó không xác nhận rằng sơ đồ diễn đạt đúng nghiệp vụ trong UC; phần đó cần người rà soát.

Nguồn sơ đồ dùng cú pháp tại [diagram_gen/SYNTAX.md](diagram_gen/SYNTAX.md). Có thể chạy riêng `actgen.py` hoặc `seqgen.py`; xem hướng dẫn đầu file và cú pháp tương ứng.

## Cài đặt

- Python 3.9 trở lên; công cụ kiểm tra và sinh draw.io/SVG dùng thư viện chuẩn.
- Xuất DOCX cần `python-docx`: `pip install python-docx`.
- Xuất PNG cần `playwright` và Chromium. Nếu thiếu, lệnh build vẫn sinh draw.io/SVG và thông báo bỏ qua PNG.

## Giới hạn

- Không sinh sơ đồ tự động từ business rule hoặc nội dung Use Case; người viết tạo nguồn `.act`/`.seq`.
- Không kiểm tra ngữ nghĩa hoặc tính nhất quán nghiệp vụ giữa rule, UC và sơ đồ.
- DOCX theo template hiện tại là đầu ra từng UC riêng lẻ; chưa gộp nhiều UC vào một tài liệu.
