# Chuyển vector SkyGo từ Qdrant sang MariaDB

Code hiện dùng `MariaDBVectorStore` (Spring AI 2.0.2). Runtime không kết nối Qdrant. Bảng `ai_document` và bảng thông tin tương thích `ai_vector_schema` do Flyway **V12** quản lý; cấu hình hiện tại là `gemini-embedding-001`, **768 chiều**, **cosine**. MariaDB cần hỗ trợ vector (11.7.1+; môi trường hiện tại 11.8.6).

Các bước dưới đây dành cho máy đã có vector Qdrant. Máy mới dùng [hướng dẫn Windows](../SETUP-NEW-MACHINE-WINDOWS.md) để lập index từ dữ liệu migration của riêng mình.

## 1. Tạo bảng bằng migration

Dừng backend cũ và mọi tiến trình index. Giữ MariaDB chạy. Từ thư mục SkyGo:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1 -DisableAi
```

Script nạp cấu hình từ `backend/.env` và chạy backend khi AI/index tắt. Flyway áp dụng V12 sau các migration trước đó. Đợi `Started BackendApplication`, rồi Ctrl+C và đợi backend dừng. Bước này không tạo embedding. Không chạy song song backend cũ ở IntelliJ.

V12 chỉ thêm hai bảng AI; không chuyển hoặc sửa dữ liệu chuyến/booking hiện có. Không chỉnh migration đã được áp dụng.

## 2. Kiểm tra bản chuyển, chưa ghi dữ liệu

Giữ Docker/Qdrant cũ chạy trong giai đoạn chuyển. Chạy:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\migrate-vectors.ps1
```

Mặc định là **DRY RUN**, không ghi document vào MariaDB. Lệnh biên dịch tiện ích riêng, đọc toàn bộ document thuộc `managed_by=skygo-db-v1` và đúng model từ Qdrant, kiểm tra số chiều/cosine, metadata, quyền sở hữu, ID/hash và vector. Cấu hình mặc định:

- Source URL: `http://127.0.0.1:6333` (REST, không phải gRPC 6334).
- Collection: `skygo_knowledge_v1_gemini-embedding-001_768`.
- Đích: MariaDB theo cấu hình DB của backend, bảng `ai_document` đã có V12.

Nếu collection cũ có tên khác:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\migrate-vectors.ps1 `
    -SourceUrl 'http://127.0.0.1:6333' `
    -SourceCollection 'TEN_COLLECTION_CU'
```

Nếu source có API key, đặt `SKYGO_AI_QDRANT_API_KEY` trong môi trường hoặc `.env` cho tiện ích chuyển; runtime MariaDB không cần biến này. Biến môi trường có sẵn được ưu tiên. Tiện ích không in secret hay nội dung document.

Mong đợi `VECTOR_TRANSFER_OK` với `applied=false`. `source` là số document đọc được, `toInsert` là số cần chép, `unchanged` là số đã có giống nhau. Không mặc định số lượng là 875 vì source có thể thay đổi theo dữ liệu.

## 3. Chuyển thật bằng `-Apply`

Sau khi kiểm tra dry run, dùng cùng cấu hình nguồn và thêm `-Apply`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\migrate-vectors.ps1 -Apply
```

Tiện ích chỉ INSERT các document còn thiếu; không ghi đè row đã tồn tại. Nếu row đích có nội dung, metadata hoặc vector khác, hoặc có row ngoài tập nguồn, lệnh dừng để tránh làm mất dữ liệu mới hơn. Source rỗng, sai model/số chiều, đọc thiếu trang, lệch số lượng hoặc metadata sai cũng làm lệnh thất bại.

Toàn bộ source được đọc/kiểm tra trước khi mở transaction ghi SQL. Sau khi ghi, công cụ kiểm tra lại số lượng, nội dung, metadata và từng thành phần vector (sai số float tối đa 1e-6). Lỗi trong transaction sẽ rollback toàn bộ lần nhập. Chạy lại trên tập nguồn/đích giống nhau sẽ báo `toInsert=0`, `unchanged=source`.

**Không gọi Gemini, không embed lại, không sửa/xóa Qdrant và không tự chạy migration.** Cần tạm dừng các writer ở cả nguồn và đích trong suốt bước chuyển vì Qdrant scroll không phải snapshot giao dịch. Mã nguồn công cụ dùng REST/JDBC, không khởi động Spring hay index tự động.

Payload Qdrant cũ có thể lưu `owner_id` dạng chuỗi số thay vì JSON number. Công cụ kiểm tra đây là ID nguyên dương hợp lệ và giữ nguyên metadata/hash; không chuyển dữ liệu riêng tư thành công khai để nhập cho qua lỗi.

## 4. Kiểm tra và chạy bằng MariaDB

Trong HeidiSQL:

```sql
SELECT * FROM ai_vector_schema;

SELECT JSON_VALUE(metadata, '$.document_type') AS document_type,
       JSON_VALUE(metadata, '$.scope') AS scope,
       COUNT(*) AS documents
FROM ai_document
GROUP BY document_type, scope;

SELECT COUNT(*) AS total_documents FROM ai_document;
```

Chạy backend bình thường:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1
```

Script tắt index nền. Runtime dùng MariaDB và Gemini; không cần Docker/Qdrant. Frontend giữ nguyên endpoint và hợp đồng dữ liệu; nguồn tài liệu vector là `MARIADB_VECTOR`, nguồn chuyến/giá/booking hiện tại vẫn là `MARIADB`.

Thử tuyến/ngày có thật trong DB, chính sách và địa danh chưa khớp tên. Giữ các bộ lọc `scope`, `managed_by`, `embedding_model`, `document_type`; document booking riêng tư không được đưa qua retriever công khai. Giá và ghế vẫn được tra bằng SQL nghiệp vụ, không dùng snapshot vector làm giá hiện tại.

Sau khi kiểm tra, có thể dừng Qdrant:

```powershell
docker compose -f .\compose.rag.yaml stop
```

Giữ volume Qdrant để có bản nguồn đối chiếu. Không dùng `down -v` và không xóa source để xử lý lỗi nhập. File Compose được giữ lại chỉ để đọc dữ liệu cũ khi cần.

## 5. Những lần đồng bộ sau

Khi cần cập nhật tài liệu từ dữ liệu nghiệp vụ mới/thay đổi, chạy:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\index-ai.ps1
```

Script giờ ghi vào MariaDB, tự nạp cấu hình DB/AI từ `.env`. Các document giữ nguyên ID/hash sẽ được bỏ qua; document thay đổi mới cần gọi Gemini. Thời gian trôi qua hoặc dữ liệu đã thay đổi kể từ lúc lập index có thể làm một số document cần cập nhật; sao chép vector cũ không bảo đảm lần đồng bộ kế tiếp luôn có `upserted=0`.

Các biến `SKYGO_AI_QDRANT_HOST/PORT/TLS`, `SKYGO_AI_COLLECTION_PREFIX` và `SKYGO_AI_INITIALIZE_SCHEMA` không còn điều khiển runtime. Có thể bỏ chúng khỏi `.env` sau chuyển. Model, số chiều và cosine phải khớp schema V12; đổi embedding model hoặc dimensions cần kế hoạch migration/table mới, backend không tự phá bảng để đổi cấu hình.

## Kiểm thử phát triển

Unit/MVC tests dùng mock. Integration test dùng MariaDB thật, embedding giả và HTTP source giả lập trên loopback:

```powershell
cd backend
$env:SKYGO_VECTOR_DB_TEST = 'true'
$env:SKYGO_TEST_DB_URL = 'jdbc:mariadb://localhost:3306/skygo_test'
# Nếu cần, đặt SKYGO_TEST_DB_USERNAME/PASSWORD cho tài khoản có quyền CREATE/DROP DB test.
.\mvnw.cmd '-Dtest=MariaDbVectorIntegrationTest' test
```

Test yêu cầu URL có tên `_test`, tự tạo database duy nhất `skygo_vector_test_<UUID>`, chạy V1–V12 và chỉ xóa đúng database do lần test tạo. Không dùng database `skygo`, không gọi Gemini thật. Khi chưa bật `SKYGO_VECTOR_DB_TEST=true`, test này được bỏ qua.
