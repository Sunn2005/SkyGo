# API chat SkyGo

Giao diện Expo: [Trợ lý SkyGo trên mobile](./mobile-chat.md).

API `POST /api/v1/ai/chat` dùng Gemini để chọn các chức năng tra cứu có kiểm soát, rồi tổng hợp câu trả lời từ dữ liệu tìm được. Backend dùng vòng gọi công cụ do ứng dụng quản lý với Spring AI 2.x; không có công cụ SQL tùy ý, đặt vé, giữ chỗ hoặc thanh toán.

## Bật API

Giữ MariaDB và Internet hoạt động. Cần migration V12; xem [hướng dẫn chuyển từ Qdrant](mariadb-vector-migration.md). Dữ liệu đã lập chỉ mục trong MariaDB vẫn dùng được; không chạy lại script index chỉ để bật chat.

Trong `backend/.env`, đặt:

```dotenv
SKYGO_AI_ENABLED=true
SKYGO_AI_INDEX_ENABLED=false
SKYGO_AI_CHAT_MODEL=gemini-3.1-flash-lite
SKYGO_AI_CHAT_MAX_TOOL_ROUNDS=2
SKYGO_AI_CHAT_MAX_TOOL_CALLS=4
SKYGO_AI_CHAT_MODEL_CALLS_PER_MINUTE=4
```

Giữ `GEMINI_API_KEY` hiện có. Ví dụ này chọn `gemini-3.1-flash-lite`, đã thử được ở môi trường hiện tại; cần quyền truy cập tương ứng trên project Google của máy mới. Có thể đổi bằng `SKYGO_AI_CHAT_MODEL` sang model mà project có quyền sử dụng. Thay model chat không yêu cầu tạo lại vector. Không đổi `SKYGO_AI_EMBEDDING_MODEL`, dimensions của chỉ mục đã có; thay model/số chiều cần schema tương ứng.

Khởi động backend theo cách đang dùng. Nếu chạy Maven trong PowerShell:

```powershell
cd Q:\Ki_07\Mobile\Flight\SkyGo\backend
.\mvnw.cmd spring-boot:run
```

Để đối chiếu với tiến trình chẩn đoán Java đã chạy thành công, có thể chạy backend qua script nạp `.env` và chọn JDK 21:

```powershell
cd Q:\Ki_07\Mobile\Flight\SkyGo
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1
```

Dừng backend cũ trong IntelliJ trước để giải phóng cổng 8080. Script từ chối chạy nếu cổng đã được dùng, không tự kết thúc tiến trình khác. Nó nạp cấu hình DB, mã hóa, payOS và AI từ `.env`, giữ biến môi trường đã có ưu tiên cao hơn; không in secret và không sửa file `.env`. Index và chế độ index một lần đều bị tắt cho tiến trình này. MariaDB cần hoạt động; Flyway quản lý bảng vector qua V12. Log ứng dụng được lưu tại `backend/target/backend-powershell.log`; giữ terminal mở trong khi sử dụng app. `-CheckOnly` chỉ kiểm tra cấu hình và khôi phục môi trường sau khi kết thúc, không khởi động backend.

Lưu ý cấu hình DB vẫn theo cơ chế chạy backend hiện tại: bộ nạp AI chỉ đọc `GEMINI_API_KEY` và `SKYGO_AI_*` từ `.env`. Nếu DB được cấu hình qua biến môi trường trong IDE, cần cung cấp tương tự khi chạy bằng PowerShell. API trả `AI_DISABLED` (503) khi module AI chưa bật.

## Request

```http
POST /api/v1/ai/chat
Content-Type: application/json
Authorization: Bearer <access-token nếu đã đăng nhập>
```

```json
{
  "message": "Tìm vé SGN đến HAN ngày 2026-11-20 cho 2 người lớn và 1 trẻ em, so sánh phổ thông với thương gia",
  "history": []
}
```

`message` bắt buộc, tối đa 2000 ký tự. `history` không bắt buộc, tối đa 6 tin nhắn, mỗi tin có `role` (`USER` hoặc `ASSISTANT`) và `content` tối đa 2000 ký tự. Client có thể gửi các lượt gần nhất để hỏi tiếp như “còn hạng thương gia thì sao?”. Backend không lưu hội thoại vào database. Lịch sử do client gửi chỉ là ngữ cảnh không đáng tin cậy, không cấp quyền và không thay thế việc đọc lại dữ liệu. Client nên xóa lịch sử cục bộ khi đăng xuất/đổi tài khoản.

Client không gửi `userId`, câu SQL, tên công cụ hoặc dữ liệu kết quả. Máy chủ lấy chủ tài khoản từ `AuthSession`. Không có cookie phiên chat hoặc conversation ID dùng chung giữa người dùng.

### Gọi bằng PowerShell

Tra cứu công khai:

```powershell
$body = @{ message = 'Quy định hành lý ký gửi của SkyGo như thế nào?' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/ai/chat' `
    -ContentType 'application/json; charset=utf-8' `
    -Body ([Text.Encoding]::UTF8.GetBytes($body)) | ConvertTo-Json -Depth 15
```

Lịch sử cá nhân, dùng access token nhận từ API đăng nhập:

```powershell
$accessToken = 'ACCESS_TOKEN_CUA_BAN'
$body = @{ message = 'Cho tôi xem 3 đơn đặt vé gần nhất trong 15 ngày qua' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/ai/chat' `
    -Headers @{ Authorization = "Bearer $accessToken" } `
    -ContentType 'application/json; charset=utf-8' `
    -Body ([Text.Encoding]::UTF8.GetBytes($body)) | ConvertTo-Json -Depth 15
```

## Response

Kết quả nghiệp vụ trả HTTP 200 theo envelope `data` hiện có:

```json
{
  "data": {
    "status": "ANSWERED",
    "answer": "Câu trả lời được tổng hợp từ dữ liệu tra cứu.",
    "sources": [],
    "flightSearches": [],
    "bookingSearches": [],
    "clarifications": []
  }
}
```

| Trường / trạng thái | Ý nghĩa |
| --- | --- |
| `ANSWERED` | Có bằng chứng tra cứu và câu trả lời |
| `NEEDS_CLARIFICATION` | Cần bổ sung thông tin; xem `clarifications` và các lựa chọn sân bay |
| `LOGIN_REQUIRED` | Khách cần đăng nhập để xem lịch sử; frontend hiện dialog, không tự chuyển trang |
| `NO_DATA` | Không có kết quả phù hợp trong phạm vi truy vấn |
| `sources` | Nguồn do backend tạo từ bản ghi thật, gồm `type`, `id`, `title`, `asOf` |
| `flightSearches` | Kết quả có cấu trúc để hiển thị thẻ chuyến/hạng vé |
| `bookingSearches` | Đơn của đúng tài khoản, sắp mới nhất trước |
| `clarifications` | Trường cần bổ sung và các sân bay phù hợp khi tên mơ hồ |

`flightSearches[].options` là từng lựa chọn chuyến **và hạng vé**, không phải mỗi phần tử là một chuyến khác nhau. `totalFareOptions` đếm toàn bộ tập phù hợp; `hasMore` cho biết danh sách trả về đã giới hạn. `adultPrice` là giá một người lớn một chiều, gồm VAT. `partyTotal` là tổng đúng nhóm hành khách cho một chiều, làm tròn tiền cơ bản từng loại khách rồi tính VAT một lần theo nghiệp vụ booking, chưa gồm hành lý mua thêm. `asOf` là thời điểm đọc dữ liệu; không có giữ chỗ/khóa giá.

## Các chức năng mà Gemini được phép chọn

| Công cụ | Xử lý |
| --- | --- |
| `searchFlights` | SQL chỉ đọc, lọc tuyến/ngày/ghế/hạng; mặc định top 5 theo tổng giá nhóm, tối đa 10 |
| `searchRecentBookings` | SQL chỉ đọc theo chủ tài khoản; mặc định 15 ngày, 3 đơn; tối đa 365 ngày và 10 đơn |
| `searchKnowledge` | RAG công khai: sân bay, tuyến, quyền lợi vé, quy tắc giá và hành lý; không lấy snapshot giá hoặc booking riêng tư |
| `askForDetails` | Yêu cầu bổ sung điểm đi/đến, ngày, số khách hoặc nội dung câu hỏi |

Tên thành phố/sân bay được chuẩn hóa dấu và đối chiếu danh mục đang hoạt động. Có nhiều kết quả thì trả lựa chọn, không tự chọn một sân bay. Ngày bay dùng múi giờ sân bay đi, kể cả DST; giới hạn từ hôm nay đến 365 ngày tới. Mặc định giá cho 1 người lớn; tối đa 9 người có ghế và số em bé không vượt số người lớn. Tra khứ hồi bằng hai chiều riêng, không thay thế quote/giữ chỗ chính thức.

Bộ đối chiếu chấp nhận tiền tố `thành phố`, `TP.`, `sân bay`, `sân bay quốc tế`, cách viết có/không dấu và các tên gọi TP.HCM/HCMC/Sài Gòn. Ví dụ `thành phố Hồ Chí Minh` và `sân bay Phú Quốc` đối chiếu với bản ghi thành phố/sân bay tương ứng, không bắt khách cung cấp IATA. Bí danh được ánh xạ về thành phố rồi lọc danh mục, không tự tạo sân bay hoặc mặc định chọn SGN khi một thành phố có nhiều sân bay đang hoạt động. Thay đổi cách đối chiếu này không yêu cầu migration hay lập lại vector.

SQL lọc và sắp xếp toàn bộ tập phù hợp trước `LIMIT`; kết luận “rẻ nhất” không dựa vào top-k vector hoặc trang đầu theo giờ bay. Các điều kiện giá kiểm tra VND, VAT và công thức pricing hiện có. Không tự chạy job dọn giữ chỗ hoặc thay đổi booking trong công cụ AI.

### Xác định sân bay bằng danh mục và vector

`searchFlights` nhận nguyên tên thành phố/sân bay khách nói. Luồng xử lý:

1. Đối chiếu mã IATA, tên đã chuẩn hóa và bí danh với danh mục sân bay đang hoạt động trong SQL. Một kết quả chính xác được dùng ngay, không gọi embedding; nhiều sân bay cùng khớp thì yêu cầu khách chọn.
2. Nếu tên chưa khớp, tạo embedding cho **địa danh chưa xác định**, rồi tìm vector sân bay trong MariaDB Vector. Truy vấn bắt buộc lọc `scope=public`, `document_type=airport`, `managed_by=skygo-db-v1` và đúng `embedding_model` hiện tại.
3. Kiểm tra lại metadata và điểm tương đồng ở backend, loại bản trùng, rồi đối chiếu `airport_code`/`source_id` với danh mục SQL hiện tại. Không lấy tên, giá hoặc chỗ trống từ nội dung vector để thay thế dữ liệu SQL. Sân bay đứng đầu đã ngừng hoạt động không được tự thay bằng sân bay hạng dưới.
4. Chỉ tự chọn khi điểm đủ cao và cách biệt đủ lớn. Nếu kết quả chưa chắc chắn, có các ứng viên gần nhau hoặc thành phố có nhiều sân bay, API trả `NEEDS_CLARIFICATION` cùng `airportOptions` để khách xác nhận/chọn.
5. Khi đã xác định mã sân bay, SQL kiểm tra lại danh mục và tra chuyến, giá, ghế hiện tại. Gemini tổng hợp kết quả có cấu trúc này.

Thiếu ngày bay thì vẫn hỏi ngày trước khi tra cứu. Mã IATA viết hoa nhưng không tồn tại cũng được hỏi lại, không tự chọn mã gần nhất. Không có vector phù hợp được phân biệt với lỗi kết nối MariaDB Vector/embedding; lỗi dịch vụ giữ mã lỗi tương ứng thay vì báo không có sân bay.

Các kết quả tìm địa danh được dùng lại trong cùng request; không có cache chung giữa người dùng. Lệnh đọc danh mục SQL kết thúc trước khi gọi embedding/tìm vector; tra chuyến được thực hiện trong một lần đọc SQL riêng sau đó. Việc này tránh giữ transaction SQL trong lúc chờ dịch vụ AI.

Các ngưỡng ban đầu có thể cấu hình qua `.env`:

| Biến | Mặc định | Vai trò |
| --- | --- | --- |
| `SKYGO_AI_AIRPORT_TOP_K` | `5` | Số document sân bay lấy tối đa, từ 2 đến 10 |
| `SKYGO_AI_AIRPORT_MIN_SIMILARITY` | `0.65` | Điểm tối thiểu để đưa vào tập ứng viên |
| `SKYGO_AI_AIRPORT_AUTO_SELECT_SIMILARITY` | `0.85` | Điểm tối thiểu để có thể tự chọn |
| `SKYGO_AI_AIRPORT_MIN_SCORE_GAP` | `0.10` | Khoảng cách điểm tối thiểu với ứng viên kế tiếp để có thể tự chọn |

Đây là ngưỡng khởi đầu, **chưa được hiệu chỉnh bằng embedding thực tế** của các cách gọi địa danh. Điểm tương đồng không phải xác suất đúng. Với cách gọi như “đảo ngọc”, kết quả thực tế có thể cần xác nhận hoặc yêu cầu tên rõ hơn tùy dữ liệu và điểm tìm kiếm.

Document sân bay hiện có đã chứa metadata cần thiết; khi chuyển backend lưu trữ cần chép dữ liệu Qdrant sang MariaDB theo [hướng dẫn chuyển](mariadb-vector-migration.md), hoặc lập index trên máy mới. Mỗi tên chưa khớp chỉ phát sinh embedding truy vấn; không embed lại toàn bộ 875 document. Response có thể chứa cả nguồn `MARIADB_VECTOR` cho việc xác định sân bay và `MARIADB` cho chuyến/giá. Cấu trúc API và lựa chọn sân bay trên frontend được giữ nguyên.

Phạm vi index 30/15 ngày chỉ giới hạn tài liệu RAG. Các truy vấn SQL đọc dữ liệu thực tế trong phạm vi hợp lệ của câu hỏi. Các vector `booking_summary` đang có không được đưa qua retriever công khai; lịch sử gần đây dùng SQL để đảm bảo thứ tự và quyền sở hữu. Không gửi tên/giấy tờ hành khách, email, điện thoại, mật khẩu, token hoặc QR vào ngữ cảnh AI từ database.

## Quota, lỗi và bảo vệ dữ liệu

- Tối đa 2 vòng công cụ (3 lần gọi model logic) và tổng 4 lời gọi công cụ mỗi câu hỏi; tham số được kiểm tra lại bằng DTO/validation, không tin JSON của model.
- Mỗi tiến trình xử lý một câu hỏi tại một thời điểm và mặc định cho phép 4 lần gọi model logic/phút. Giới hạn này không tính các ứng dụng khác dùng chung project. Nếu chạy nhiều backend, cần cơ chế hạn mức dùng chung trước khi mở rộng.
- SDK chat chỉ thử một lần để không tự tiêu thêm quota. Client/embedding cho index vẫn giữ retry độc lập. Lỗi 429 được trả rõ, không lặp vô hạn.
- Adapter Spring AI cũng đặt `maxRetries(0)` riêng, tương ứng một lần thử, để không có thêm lượt HTTP ẩn bên ngoài chính sách SDK và bộ đếm model.
- Truy vấn RAG mới tạo embedding cho câu truy vấn; SQL không tạo embedding. Không có bước nạp lại 875 document trong API chat. Những lời gọi công cụ trùng tham số trong cùng request được dùng lại kết quả.
- Source và dữ liệu thẻ do máy chủ tạo, không nhận từ lời văn của Gemini. Câu trả lời không có bằng chứng truy xuất không được chuyển thẳng thành khẳng định thực tế.
- Mỗi request có tập công cụ và chủ sở hữu riêng; SQL lọc `b.user_id` cả khi đọc header đơn lẫn chặng. Response/error có `Cache-Control: no-store`.
- `Authorization` không hợp lệ được bộ lọc hiện tại coi như chưa đăng nhập; tra công khai vẫn dùng được, tra lịch sử trả `LOGIN_REQUIRED`.

| HTTP | Ví dụ mã lỗi |
| --- | --- |
| 422 | `VALIDATION_ERROR` |
| 429 | `AI_BUSY`, `AI_RATE_LIMITED`, `AI_UPSTREAM_RATE_LIMITED`, `EMBEDDING_RATE_LIMITED` |
| 502 | `AI_CONFIGURATION_ERROR`, `AI_INVALID_TOOL`, `AI_TOOL_LIMIT`, `AI_EMPTY_RESPONSE` |
| 503 | `AI_DISABLED`, `AI_DATA_UNAVAILABLE`, `AI_NETWORK_ERROR`, `VECTOR_STORE_UNAVAILABLE` |
| 504 | `AI_TIMEOUT`, `EMBEDDING_TIMEOUT` |

Lỗi có envelope `error: {code, message, details, requestId}`. HTTP 429 kèm `Retry-After: 60` là thời gian chờ đề nghị, không bảo đảm quota ngày đã phục hồi. Nội dung phản hồi/cause thô từ nhà cung cấp không được trả về client.

## Kiểm thử để chạy thủ công

Biên dịch (không chạy test hay ứng dụng):

```powershell
cd Q:\Ki_07\Mobile\Flight\SkyGo\backend
.\mvnw.cmd '-DskipTests' test-compile
```

Các unit/MVC tests sau dùng mock, không gọi Gemini/MariaDB:

```powershell
.\mvnw.cmd '-Dtest=AiAirportResolverTest,AiChatServiceTest,AiChatGateTest,AiChatControllerTest,AiKnowledgeReaderTest,AiReadRepositoryTest,GeminiChatAdapterTest' test
```

`AiReadRepositoryIntegrationTest` chỉ chạy khi đặt `SKYGO_TEST_DB_URL` trỏ tới database tên chứa `_test`, đã migrate sẵn. Bài test chỉ đọc, không seed/migrate và không gọi model. Các trường hợp kiểm tra gồm guest/login, chống truyền owner giả, RAG không lộ private, ngày DST, SQL sắp xếp trước LIMIT, vòng công cụ, quota và lỗi provider đã làm sạch.

Tham khảo nền tảng: [Spring AI 2.x Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html).

## Lỗi options của adapter Gemini

Với adapter `spring-ai-google-genai` 2.0.2 hiện tại, mỗi `Prompt` trong vòng tra cứu phải dùng `GoogleGenAiChatOptions` (kể cả lượt tổng hợp cuối), đặt giới hạn bằng `maxOutputTokens`. Truyền `DefaultToolCallingChatOptions` từ builder chung gây `ClassCastException` tại `GoogleGenAiChatModel.internalCall`, trước khi có request đến Gemini. Do lớp xử lý lỗi che chi tiết provider, phiên bản cũ trả `AI_UPSTREAM_UNAVAILABLE` cho lỗi này.

`GeminiChatAdapterTest` kiểm tra cả adapter Spring AI và Google SDK bằng HTTP server giả lập cục bộ, không dùng API key thật và không kết nối Gemini hoặc MariaDB. Chạy cùng các test chat:

```powershell
.\mvnw.cmd '-Dtest=GeminiChatAdapterTest,AiChatServiceTest,AiChatControllerTest,AiChatGateTest' test
```

Sau khi cập nhật code, khởi động lại tiến trình backend đang chạy trong IDE để nạp class mới. Không cần chạy lại index.

## Kiểm tra lỗi kết nối Gemini

Phép thử metadata bằng key giả trả `SDK_NETWORK_OK HTTP 400` chỉ xác nhận Java SDK nhận được phản hồi HTTPS từ Google trong tiến trình PowerShell đó. Nó chưa xác nhận API key thật, quyền dùng model, request sinh nội dung hoặc cấu hình môi trường của tiến trình IDE.

Khi request chat thất bại, tìm dòng `AI chat model call failed` trong Run console. Log ghi model, timeout, mã HTTP nếu có, các tên lớp exception và nhóm nguyên nhân; không ghi nội dung exception, URL, API key hoặc hội thoại.

Log thất bại còn ghi `round` (bắt đầu từ 0) và `elapsedMs`. `round=0` là request model đầu tiên; các vòng sau đã bao gồm phản hồi công cụ, nên cần phân biệt hai trường hợp khi mọi phép thử request đầu tiên đều đạt.

| `diagnostic.transport` | Ý nghĩa |
| --- | --- |
| `HTTP` | Đã có phản hồi HTTP; xem `httpStatus` để phân biệt quota, quyền truy cập và lỗi yêu cầu |
| `TIMEOUT` | Quá thời gian chờ; bao gồm deadline toàn request của OkHttp |
| `TLS` | Lỗi bắt tay/chứng chỉ HTTPS; xem tên lớp nguyên nhân lồng bên trong |
| `DNS` | Không phân giải được hostname trong tiến trình backend |
| `CONNECTION_BLOCKED` | Hệ thống từ chối thao tác kết nối socket |
| `CONNECTION_RESET` | Kết nối bị đóng/reset giữa chừng |
| `CONNECTION_REFUSED` | Đích kết nối từ chối TCP |
| `NETWORK` / `UNKNOWN` | Cần đối chiếu `causeTypes` để khoanh vùng tiếp |

Không dùng thành công của phép thử key giả để kết luận request chat đã hoạt động. Không tăng timeout hoặc đổi proxy/chứng chỉ chỉ dựa trên thông báo mạng chung; lấy chẩn đoán của đúng request thất bại trước.

### Nếu nhiều model cùng trả 503

Chạy phép thử sinh nội dung tối thiểu trực tiếp bằng REST, từ thư mục gốc SkyGo:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\diagnose-gemini.ps1
```

Script đọc `GEMINI_API_KEY` và model từ môi trường hoặc `.env` như cơ chế fallback hiện tại. Nó gửi **một** câu `Reply with exactly OK.`, không kèm công cụ, không dùng dữ liệu SkyGo, không embedding, không retry. Lệnh này có dùng quota chat. `-CheckOnly` chỉ kiểm tra cấu hình, không gửi request. Có thể chọn model cụ thể bằng `-Model gemini-3.1-flash-lite`.

- `DIRECT_GENERATION_OK`: yêu cầu tối thiểu với key thật đã được Google chấp nhận; tiếp tục đối chiếu request ứng dụng, SDK và môi trường IDE.
- HTTP 503 cùng `DIRECT_GENERATION_FAILED`: lỗi cũng xảy ra khi không có request của SkyGo; xem `Google status` và `Google message` đã che key, thay vì chỉ suy luận từ mã HTTP. Chưa đủ để khẳng định có sự cố toàn hệ thống Google.
- HTTP 400/403/429: xem thông báo Google để phân biệt key, quyền model, tham số và quota.
- `TRANSPORT_ERROR`: tiến trình PowerShell chưa nhận được phản hồi HTTP hoàn chỉnh; chưa so sánh được khả năng sinh nội dung.

Phép thử này không khởi động hoặc yêu cầu backend, MariaDB hay Metro đang chạy. Nó không đổi cấu hình và không in API key. Kết quả PowerShell vẫn có thể khác IDE nếu hai tiến trình có biến môi trường hoặc proxy khác nhau.

### REST chạy được nhưng ứng dụng vẫn lỗi

So sánh các lớp trong cùng một tiến trình Java, với JDK 21, key thật, model và timeout đang cấu hình:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\diagnose-gemini.ps1 -Mode Java
```

Chế độ này gửi tối đa **ba** request chat và dừng ngay khi có bước lỗi. Cả Google SDK lẫn Spring AI trong phép thử đều tắt retry. Không khởi động backend và không thực thi công cụ; do đó không gọi SQL, vector hoặc embedding.

| Bước | Nội dung |
| --- | --- |
| `1_JAVA_SDK_PLAIN` | Google Java SDK sinh nội dung ngắn, không qua adapter Spring AI |
| `2_SPRING_AI_PLAIN` | Adapter Spring AI với câu hỏi ngắn và không có công cụ |
| `3_SPRING_AI_SKYGO_TOOLS` | Adapter với system prompt và định nghĩa bốn công cụ SkyGo; chỉ nhận yêu cầu gọi công cụ, không thực thi |

Khi lỗi, script in nhóm nguyên nhân, mã HTTP, `Google status` và `Google message` đã che key. Nếu cả ba bước đạt, so sánh môi trường IDE và các vòng gọi model tiếp theo của request thật; thành công của phép thử không thay thế kiểm tra toàn bộ workflow.

`-Mode Java -CompileOnly` chỉ biên dịch chương trình kiểm tra, không gửi request. Classpath được lấy từ dependencies đã cài của backend; script không thêm dependency hoặc thay đổi cấu hình ứng dụng. Bản kiểm tra ngoại tuyến nội bộ dùng `--offline-check` chỉ gửi ba request tới HTTP stub trên loopback, với key giả.
