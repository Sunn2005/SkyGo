# doc_gen — xuất Use Case Markdown thành DOCX

`md2docx.py` chuyển từng file Use Case theo [STANDARD.md](../../../usecase_spec/STANDARD.md) thành một file Word. Nội dung Markdown vẫn là nguồn chính; DOCX là bản kết xuất để đọc hoặc bàn giao.

## Cách dùng

Thông thường chạy qua bộ lệnh tổng:

```powershell
python docs/tools/usecase_flow/build.py build BKG-UC-001
```

Lệnh này sinh sơ đồ trước, sau đó xuất DOCX và chèn PNG nếu có. Có thể chỉ xuất DOCX từ nguồn đã có:

```powershell
python docs/tools/usecase_flow/build.py build BKG-UC-001 --only docx
```

Chạy riêng:

```powershell
python docs/tools/usecase_flow/doc_gen/md2docx.py `
  docs/usecase_spec/booking/<feature>/use-cases/BKG-UC-001_tao-dat-cho.md `
  --out docs/build/usecase-flow/booking/BKG-UC-001 `
  --assets docs/build/usecase-flow/booking/BKG-UC-001
```

Cài dependency xuất Word:

```powershell
pip install python-docx
```

Template `uc-template.docx` nằm cạnh script. Có thể truyền template khác bằng `--template` nếu nhóm thống nhất mẫu mới.

## Chuyển đổi

- Kiểm tra sáu trường thông tin, bảng hai cột, số bước chính, nhánh thay thế và tham chiếu BR/UC.
- Bỏ chú thích `{{ref: ...}}` khỏi DOCX để tài liệu bàn giao gọn; các mã tham chiếu vẫn còn trong nguồn Markdown.
- Gộp thông tin và các luồng chính/phụ thành bảng; giữ phần Biểu đồ bên dưới.
- Tự chèn `<MODULE>-UC-NNN_activity.png` và `<MODULE>-UC-NNN_sequence.png` từ `--assets` nếu ảnh đã được sinh.
- Xuất từng UC thành một DOCX cùng tên; chưa gộp nhiều UC thành một file.

Thiếu ảnh thì DOCX vẫn được tạo với nhãn sơ đồ. Lệnh build tổng thông báo loại ảnh còn thiếu để người viết biết cần sinh PNG.
