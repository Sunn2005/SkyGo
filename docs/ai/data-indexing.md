# Đồng bộ dữ liệu SkyGo cho AI vào MariaDB

Bộ đồng bộ đọc dữ liệu nghiệp vụ hiện có, tạo document tiếng Việt, gọi Gemini Embedding và lưu vào **`ai_document` trong MariaDB**. Không biến toàn bộ bảng nghiệp vụ thành vector và không tạo đơn giả. Nếu đã có vector ở Qdrant, nên [chuyển dữ liệu cũ trước](mariadb-vector-migration.md) để tránh embed lại document không đổi.

## Các loại document

| Loại | Nội dung | Phạm vi |
| --- | --- | --- |
| `airport` | Sân bay hoạt động, thành phố, quốc gia, nhóm hiển thị, múi giờ | Công khai |
| `route` | Tuyến hoạt động và khoảng cách | Công khai |
| `flight_fare` | Snapshot chuyến/hạng đang bán, còn ghế, giá 1 người lớn gồm VAT, giờ địa phương | Công khai |
| `fare_benefit` | Quyền lợi đúng chuyến, hạng, loại hành khách | Công khai |
| `passenger_fare_rule` | Hệ số giá theo loại khách và phạm vi tuyến | Công khai |
| `baggage_policy` | Chính sách hành lý, giá kiện mua thêm chưa VAT | Công khai |
| `booking_summary` | Mỗi chặng của đơn, số khách, tổng tiền toàn đơn và trạng thái | Riêng tư, `owner_id` |

Mặc định: chuyến bay **30 ngày tới**, booking tạo **15 ngày qua**, tính từ thời điểm bắt đầu đồng bộ UTC. Lịch sử rỗng không tạo document booking. Không đọc tên hành khách, email, điện thoại, giấy tờ, mật khẩu, token, QR hoặc dữ liệu chuyển khoản. `owner_id` nằm trong metadata, không ghép vào văn bản tạo embedding.

Số document được đếm khi chạy, không cố định là 875. Ngày seed và dữ liệu trên mỗi máy có thể khác. `region_name` là nhóm UI, không mặc định mọi nhóm đều là châu lục.

## Chạy một lần

1. MariaDB đang chạy, database đã được migrate tới V12. Máy mới làm theo [hướng dẫn cấu hình](../SETUP-NEW-MACHINE-WINDOWS.md); máy đang dùng Qdrant làm theo [hướng dẫn chuyển](mariadb-vector-migration.md).
2. Có Java 21, Internet, `GEMINI_API_KEY` và cấu hình DB đúng trong `backend/.env`. Biến môi trường đã có được ưu tiên.
3. Dừng các tiến trình index khác; chỉ dùng **một writer cho một database**.
4. Tại thư mục SkyGo:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\index-ai.ps1
```

Script đọc `.env` root rồi `backend/.env`, nạp DB/khóa mã hóa/AI mà không in secrets, khôi phục biến sau khi kết thúc. Nó bật index một lần, tắt Flyway và cleanup giữ chỗ, dùng cổng loopback ngẫu nhiên, rồi đóng backend sau khi hoàn tất. Không tự tạo bảng; V12 phải có trước.

Mặc định batch 8 document, nghỉ 10 giây giữa hai batch có thay đổi và timeout 90 giây mỗi HTTP attempt. Nhịp lý thuyết khoảng 48 document/phút chưa tính thời gian API; không bảo đảm bằng quota thực tế của Google. Có thể điều chỉnh:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\index-ai.ps1 `
    -FlightDays 30 -BookingDays 15 -BatchSize 8 -BatchDelaySeconds 15 -TimeoutSeconds 90
```

Chỉ xác nhận thành công khi có `AI index completed: total=..., upserted=..., unchanged=..., deleted=...` và `BUILD SUCCESS`. `total` là số document, không phải số chuyến hoặc số bảng.

Kiểm tra bằng HeidiSQL:

```sql
SELECT JSON_VALUE(metadata,'$.document_type') AS document_type,
       JSON_VALUE(metadata,'$.scope') AS scope, COUNT(*) AS documents
FROM ai_document
GROUP BY document_type,scope;
```

## Đồng bộ tăng dần

- Đọc snapshot nghiệp vụ trong transaction read-only REPEATABLE_READ, kết thúc trước khi gọi Gemini.
- ID UUID ổn định theo loại/khóa nguồn; hash SHA-256 bao gồm nội dung và metadata có ý nghĩa, không bao gồm thời gian quan sát.
- `MariaDbKnowledgeIndexStore` chỉ đọc/xóa row thuộc `managed_by=skygo-db-v1` và model hiện tại. Row không có hash hợp lệ làm lượt đồng bộ thất bại, tránh xóa nhầm sau khi đọc chỉ mục không đầy đủ.
- Chỉ document mới/thay đổi mới gọi embedding. Dữ liệu không đổi thì lần chạy sau có `upserted=0`.
- Chỉ xóa document hết phạm vi sau khi toàn bộ upsert thành công. Nếu batch/API/SQL lỗi, dừng lượt đó; batch đã lưu được tiếp tục dùng theo ID/hash khi chạy lại.
- Không giữ transaction ghi SQL trong lúc chờ API embedding. Việc nghiệp vụ và vector nằm chung MariaDB không biến cả lượt đồng bộ dài thành một transaction duy nhất.
- Snapshot vượt `max-documents` bị từ chối trước khi ghi, không tự cắt bớt rồi xóa document ngoài phần cắt.
- SDK embedding tối đa 3 attempts có backoff cho lỗi tạm thời; retry không giải quyết quota ngày đã hết. Log chỉ ghi tiến độ, số lượng và lỗi đã làm sạch.

## Tự cập nhật nền (tùy chọn)

```dotenv
SKYGO_AI_ENABLED=true
SKYGO_AI_INDEX_ENABLED=true
SKYGO_AI_INDEX_FIXED_DELAY=5m
SKYGO_AI_INDEX_BATCH_SIZE=8
SKYGO_AI_INDEX_BATCH_DELAY=10s
SKYGO_AI_INDEX_FLIGHT_DAYS=30
SKYGO_AI_INDEX_BOOKING_DAYS=15
```

Chạy backend bằng IDE/cách riêng có nạp đúng biến môi trường nếu muốn bật nền. `start-backend.ps1` **luôn tắt index nền** để tránh tiêu quota ngoài ý muốn. Scheduler chạy lượt đầu khoảng 5 giây sau khi sẵn sàng và chờ fixed-delay sau khi mỗi lượt kết thúc. Không chạy đồng thời script index một lần và writer nền.

## Phạm vi sử dụng trong chat

RAG công khai chỉ truy xuất sân bay, tuyến và chính sách/quyền lợi phù hợp. `flight_fare` và `booking_summary` vẫn có thể được lập index theo thiết kế nguồn, nhưng API chat không dùng chúng để xác nhận giá/ghế/lịch sử hiện tại. Công cụ SQL tra giá, sắp xếp giá toàn tập và đọc lịch sử đúng chủ sở hữu. Đổi nơi lưu vector không thay đổi các quy tắc này.

Model/số chiều của bảng bị ràng buộc bởi V12 và `ai_vector_schema`; không dùng collection prefix để tách dữ liệu nữa. Database riêng có bảng vector riêng. Đổi model hoặc kích thước cần migration/table mới có chủ đích, không bật tự xóa/tạo bảng của Spring AI.

Kiểm thử quan trọng: `KnowledgeIndexerTest`, `MariaDbVectorIntegrationTest`, `AiKnowledgeReaderTest`, `AiAirportResolverTest`. Xem [hướng dẫn kiểm thử database tạm](mariadb-vector-migration.md#kiểm-thử-phát-triển).
