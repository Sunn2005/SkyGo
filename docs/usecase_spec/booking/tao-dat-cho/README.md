# Feature mẫu — Tạo đặt chỗ

Feature này được tạo để thử bộ công cụ Use Case SkyGo. **Các luật có nhãn giả định kiểm thử chỉ nhằm tạo một luồng đủ nhánh; chưa phải quyết định nghiệp vụ chính thức.** Trước khi dùng làm tài liệu dự án, cần thay/chốt các giả định với nhóm.

## Use Case

| Mã | Tên | Actor | Trạng thái |
|---|---|---|---|
| `BKG-UC-001` | Tạo đặt chỗ | User | Mẫu kiểm thử |

## Phạm vi mẫu

User đã đăng nhập, chọn chuyến một chiều phù hợp, nhập thông tin 1–4 hành khách người lớn, chọn hành lý, xem lại giá và xác nhận tạo booking. Thanh toán PayOS là luồng riêng; UC này chỉ trả mã booking để tiếp tục thanh toán.

## File nguồn

- `rules.md`: rule đã nêu trong kế hoạch và giả định kiểm thử.
- `use-cases/BKG-UC-001_tao-dat-cho.md`: đặc tả theo `STANDARD.md`.
- `diagrams/BKG-UC-001_activity.act`: nguồn Activity.
- `diagrams/BKG-UC-001_sequence.seq`: nguồn Sequence.

## Build thử

Chạy từ thư mục gốc `SkyGo`:

```powershell
python docs/tools/usecase_flow/build.py list
python docs/tools/usecase_flow/build.py check BKG-UC-001
python docs/tools/usecase_flow/build.py build BKG-UC-001
```

Kết quả dự kiến nằm trong `docs/build/usecase-flow/booking/BKG-UC-001/`. Xuất DOCX cần `python-docx`; chèn ảnh sơ đồ vào DOCX cần Playwright và Chromium.
