# Cú pháp file sơ đồ (.act và .seq)

Tài liệu này mô tả cú pháp mà `actgen.py` (activity diagram) và `seqgen.py` (sequence diagram) đọc. Nội dung rút ra từ code của hai tool, nên nếu sửa tool thì sửa file này theo. Hai ví dụ chạy được nằm ở `examples/`.

Hai tool dùng chung một số quy tắc, còn từ vựng riêng thì khác nhau.

## Quy tắc chung

- File UTF-8. Nhãn có dấu tiếng Việt vẫn vẽ đúng (đã thử với font DejaVu Sans khi xuất PNG).
- Dòng trống và dòng bắt đầu bằng `#` bị bỏ qua.
- `title: <tên sơ đồ>` đặt tên sơ đồ, tuỳ chọn.
- Chuỗi có khoảng trắng phải đặt trong dấu nháy kép: `"Người học"`.
- Trong nhãn, `\n` là xuống dòng.
- `<id>` chỉ gồm chữ, số và `_`, không trùng nhau trong một file.
- Lỗi cú pháp in dạng `LOI: dong N: <lý do>` kèm dòng gốc, và tool dừng, không ghi file nào. Các thông báo lỗi của tool viết không dấu.

## Activity diagram swimlane (`.act`)

### Khai báo

| Dòng | Ý nghĩa |
| --- | --- |
| `lane <id> "<tên>"` | Một làn. Khai báo theo thứ tự từ trái sang phải. Cần ít nhất một làn |
| `start <id> <lane> ["<nhãn>"]` | Điểm bắt đầu |
| `end <id> <lane> ["<nhãn>"]` | Điểm kết thúc |
| `action <id> <lane> "<nhãn>"` | Hoạt động. Nhãn bắt buộc |
| `decision <id> <lane> "<nhãn>"` | Rẽ nhánh. Nhãn bắt buộc |
| `fork <id>` | Thanh ngang tách luồng song song. Không cần lane và nhãn |
| `join <id>` | Thanh ngang gộp luồng |

Với `start`, `end`, `action`, `decision` có thể thêm `down=k` ở cuối để đẩy node xuống thêm k hàng.

### Cạnh

```
a1 -> a2                    # cạnh thường
d1 -> a3 : Đúng             # cạnh có nhãn (nhãn bắt đầu sau dấu hai chấm)
d1 -> a1 : Thử lại [back]   # cạnh quay ngược, đi vòng ra lề phải
d1 -> a4 : Sai [side]       # đích nằm cùng hàng với nguồn
```

### Luật tool kiểm tra

- `start` không có cạnh đi vào; `end` không có cạnh đi ra.
- Mọi node còn lại phải có cạnh đi vào, và phải có cạnh đi ra nếu không phải `end`.
- `decision` có ít nhất 2 cạnh ra; `fork` ít nhất 2 cạnh ra; `join` ít nhất 2 cạnh vào.
- Vòng lặp phải có cạnh đánh dấu `[back]`. Cạnh đi ngược lên mà thiếu `[back]` bị báo lỗi.
- Hàng của node tự tính theo độ dài đường đi. Hai node cùng hàng và cùng làn tự xếp trái phải theo thứ tự khai báo.

Ví dụ đầy đủ: [examples/sample_activity.act](examples/sample_activity.act).

## Sequence diagram kiểu robustness (`.seq`)

### Participant

```
actor    u "Người học"
boundary b "Màn hình Quiz" "Giao diện"
control  c "QuizService" "Xử lý nghiệp vụ"
entity   q "QuizDAO" "Truy cập dữ liệu"
```

Cú pháp: `<loại> <id> "<tên>" ["<chữ trong dải xám>"] [created]`

- Loại là `actor`, `boundary`, `control` hoặc `entity`, vẽ bằng biểu tượng tương ứng.
- Quy ước SkyGo: `actor` là User/hệ thống ngoài; `boundary` là giao diện; `control` là service nghiệp vụ; `entity` là DAO/repository truy cập dữ liệu. Participant `entity` đại diện tầng truy cập dữ liệu, không phải bảng MySQL hoặc entity nghiệp vụ.
- Tên hiện dưới biểu tượng. Chữ trong dải xám ở đầu cột mặc định bằng tên, ghi thêm chuỗi thứ ba nếu muốn khác.
- `created`: participant này được tạo giữa chừng. Phải có message `[create]` tới nó.

### Message

```
u -> b : chọn đáp án          # gọi (nét liền)
q --> c : đúng / sai          # trả về (nét đứt)
c -> q [create] : tạo bản ghi
c -> q [destroy] : xoá        # vẽ dấu X, kết thúc đường sống
```

Cờ `[create]` hoặc `[destroy]` đứng trước dấu hai chấm. Không hỗ trợ message gửi cho chính mình.

### Khối alt và loop

```
alt: đáp án đúng
c --> b : giải thích
else: đáp án sai
c --> b : báo chưa đúng
end

loop: còn câu hỏi
...
end
```

`else:` chỉ dùng bên trong `alt`. Khối lồng nhau được. Mỗi `alt` hoặc `loop` phải đóng bằng `end`.

### Luật tool kiểm tra

- Mọi id trong message phải đã khai báo.
- Participant đánh dấu `created` phải có message `[create]` tới nó, và ngược lại.
- `end` thừa, hoặc khối chưa đóng, đều báo lỗi.

Ví dụ đầy đủ: [examples/sample_sequence.seq](examples/sample_sequence.seq).

## Đầu ra

Mỗi tool nhận một file và ghi ba file cùng tên vào thư mục `-o` (mặc định là thư mục của file nguồn):

| File | Dùng để |
| --- | --- |
| `<tên>.drawio` | Mở bằng draw.io để xem hoặc chỉnh |
| `<tên>.svg` | Bản vector |
| `<tên>.png` | Chèn vào Word. Cần `playwright` và chromium, thiếu thì tool bỏ qua PNG và in hướng dẫn |

Tuỳ chọn: `-o <thư mục>`, `--scale <số>` (độ phân giải PNG, mặc định 1.5), `--no-png`.

Khi chạy qua `tools/build.py`, `-o` luôn là `dist/<module>/<UCxx>/`. Đừng chỉnh tay file trong `dist/` vì lần build sau sẽ ghi đè. Muốn đổi sơ đồ thì sửa file `.act` hoặc `.seq`.
