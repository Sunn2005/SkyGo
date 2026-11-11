# Nền tảng AI: Gemini và MariaDB Vector

SkyGo dùng Java 21, Spring Boot 4.1.2, Spring AI 2.0.2 và MariaDB hỗ trợ vector (11.7.1+; môi trường hiện tại 11.8.6). Model chat và embedding là Gemini; document và vector được lưu trong MariaDB cùng hệ quản trị với dữ liệu nghiệp vụ. Không cần Docker/Qdrant để chạy ứng dụng.

- Máy mới: [hướng dẫn Windows](../SETUP-NEW-MACHINE-WINDOWS.md).
- Đã có vector Qdrant: [chuyển sang MariaDB, không embed lại](mariadb-vector-migration.md).
- Đồng bộ tài liệu: [data-indexing.md](data-indexing.md).
- API và luồng chat: [chat-api.md](chat-api.md).

## Cấu hình và quản lý schema

AI tắt mặc định qua `SKYGO_AI_ENABLED=false`. `AiEnvironmentPostProcessor` nạp cấu hình AI từ `.env` và mặc định từ `skygo-ai.properties`; OS/IDE/application properties có ưu tiên cao hơn. Script `start-backend.ps1`, `index-ai.ps1` và `migrate-vectors.ps1` nạp thêm cấu hình DB, khóa mã hóa và payOS từ `.env` mà không thực thi/in nội dung file.

Các dependency model được wiring tường minh; không có starter tự tạo dịch vụ ngoài khi AI tắt. Vector store sử dụng `spring-ai-mariadb-store`, cùng JDBC datasource hiện có. Gemini chat/embedding giữ hai client có chính sách retry riêng; model embedding phân biệt `RETRIEVAL_DOCUMENT` với `RETRIEVAL_QUERY`.

Flyway V12 tạo:

| Bảng | Vai trò |
| --- | --- |
| `ai_vector_schema` | Khai báo model `gemini-embedding-001`, 768 chiều, cosine |
| `ai_document` | UUID, content, JSON metadata và `VECTOR(768)` với cosine index |

Bean vector store phụ thuộc khởi tạo database. Trước khi dùng, backend đối chiếu model, kích thước cột và cosine index với cấu hình. Schema store không tự tạo/xóa bảng; `.initializeSchema(false)` và `.removeExistingVectorStoreTable(false)` được đặt tường minh. Sai cấu hình thì báo lỗi, không tự phá hoặc ghi đè dữ liệu. Đổi model/số chiều cần migration/table và kế hoạch chuyển tương ứng.

## Bảo vệ dữ liệu và tính đúng đắn

Các truy vấn chat dùng `VectorStore` để tìm tài liệu công khai. `AiKnowledgeReader` lọc scope, model, manager và loại document cho phép rồi kiểm tra lại metadata phía backend. `AiAirportResolver` chỉ tìm document sân bay, kiểm tra mã sân bay với SQL và yêu cầu xác nhận khi mơ hồ. Source tài liệu là `MARIADB_VECTOR`; source giá/chuyến/booking trực tiếp là `MARIADB`.

Giá/ghế hiện tại và lịch sử cá nhân vẫn đọc từ SQL nghiệp vụ. Các document booking riêng tư không được đưa qua retriever công khai. Snapshot SQL kết thúc trước lúc gọi embedding; việc dùng chung MariaDB không có nghĩa là giữ transaction mở trong khi chờ Gemini.

`RagService` là nền tảng trả lời có nguồn cho tài liệu công khai. Khi không có bằng chứng, trả thông báo thiếu dữ liệu và không gọi model sinh câu trả lời. API mobile thực tế đi qua `AiChatService` và các công cụ đọc có kiểm soát.

## Kiểm thử

Unit/MVC tests dùng mock; `GeminiChatAdapterTest` dùng HTTP stub local, không gọi Google thật. Integration test tạo database UUID `_test` riêng, chạy V1–V12 và dùng embedding giả:

```powershell
cd backend
$env:SKYGO_VECTOR_DB_TEST='true'
$env:SKYGO_TEST_DB_URL='jdbc:mariadb://localhost:3306/skygo_test'
.\mvnw.cmd '-Dtest=MariaDbVectorIntegrationTest' test
```

Tài khoản cần quyền CREATE/DROP database test. Test chỉ dọn database nó vừa tạo, không sửa database `skygo`. Test chuyển từ Qdrant thật sang database tạm chỉ bật thêm khi `SKYGO_VECTOR_SOURCE_TEST=true`; nguồn Qdrant được đọc, không bị sửa/xóa. Các phép thử này không dùng Gemini thật.

`LiveRagSmokeTest` chỉ chạy khi `SKYGO_AI_LIVE_TEST=true`; cần `SKYGO_TEST_DB_URL` trỏ vào database `_test` đã có V12. Nó gọi Gemini thật, tạo đúng một document smoke với ID riêng và chỉ xóa document đó sau test. Không bật biến này cho các kiểm thử không muốn tiêu quota.

## Tài liệu chính thức

- [Spring AI MariaDB Vector Store](https://docs.spring.io/spring-ai/reference/api/vectordbs/mariadb.html).
- [MariaDB VECTOR](https://mariadb.com/docs/server/reference/sql-structure/vectors/vector).
- [MariaDB Vector Overview](https://mariadb.com/docs/server/reference/sql-structure/vectors/vector-overview).
