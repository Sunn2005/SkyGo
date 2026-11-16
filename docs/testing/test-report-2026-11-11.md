# Báo cáo kiểm thử SkyGo — 11/11/2026

Tài liệu đi kèm: [kế hoạch](README.md), [test case](test-cases.md). Đây là **kết quả đã quan sát**, không phải xác nhận tất cả test case thủ công đều đạt. Múi giờ của log backend: UTC+07.

## Lần chạy tự động mới nhất — 11/11/2026

Chạy full suite Maven trên MariaDB riêng `skygo_codex_test`, Flyway V1–V12, không bật Gemini live hoặc gọi PayOS thật. Kết quả lúc 21:50: **280 run, 278 pass, 0 fail, 0 error, 2 skip; BUILD SUCCESS**. Tách theo lớp test của đúng lần chạy: 119/119 unit/component pass; integration 160 run, 159 pass và 1 skip; Gemini live 1 skip. Hai bài skip là `LiveRagSmokeTest.geminiEmbeddingMariaDbRetrievalAndGroundedChatRoundTrip` (chỉ chạy khi bật `SKYGO_AI_LIVE_TEST`) và `MariaDbVectorIntegrationTest.existingQdrantVectorsCanBeCopiedIntoOnlyTheIsolatedDbWithoutEmbedding` (cần Qdrant tùy chọn). Không tính các báo cáo XML cũ của lớp test không chạy trong suite này.

Frontend chạy lại lúc 21:43–21:44: **Node 70/70 pass**, `npm run lint` và `npm run typecheck` đều exit code 0. Kết quả Node dùng fetch giả, không xác nhận giao diện trên điện thoại. Bằng chứng: `target/automated-backend-full-final-2026-11-11.log` và các file `target/automated-frontend-*-2026-11-11.log`. Các log nằm trong thư mục `target`, cần lưu bản sao khi nhóm chuẩn bị bộ hồ sơ nộp.

Lần thử full suite đầu tiên báo 1 fail/2 error: `AuthIntegrationTest` đếm cứng 26 bảng dù schema V12 có 29; hai bài AI đọc DB trước khi schema `_test` được migrate. Đã sửa assertion để kiểm tra ba bảng auth thực sự cần, khởi tạo schema test rồi chạy lại ba bài lỗi và toàn bộ suite thành công. Không thay đổi nghiệp vụ runtime. **Các ca thủ công trên Expo Go vẫn chưa có kết quả.** Bảng bên dưới giữ lịch sử các lần chạy trước.

## Kết quả tự động đã kiểm chứng

| Lần chạy | Phạm vi | Run / Pass / Fail / Error / Skip | Bằng chứng | Giới hạn |
| --- | --- | --- | --- | --- |
| 11/11/2026 ~15:48 | Backend: 11 lớp unit/component chọn lọc cho auth, flight, booking, PayOS gateway và AI | 70 / 70 / 0 / 0 / 0 | Maven in `Tests run: 70 ... BUILD SUCCESS`; lệnh bên dưới | Mockito/gateway giả, không gọi PayOS/Gemini thật; chưa chạy toàn bộ integration booking |
| 11/11/2026 ~15:47–15:48 | Frontend: 6 file Node test về account, API, booking, yêu thích, payment, AI | 70 / 70 / 0 / 0 / 0 | Node test in `tests 70`, `pass 70`, `fail 0`, `skipped 0` | HTTP/fetch giả lập; chưa xác nhận giao diện trên điện thoại |
| 11/11/2026 ~15:47–15:48 | Frontend lint và TypeScript | 2 lệnh hoàn tất exit code 0 | `npm run lint`; `npm run typecheck` | Kiểm tra mã, không phải test nghiệp vụ/E2E |
| 11/11/2026 14:19 | Bộ AI/MariaDB Vector trên DB test riêng | 85 / 85 / 0 / 0 / 0, **lần chạy trước** | `target/mariadb-final-tests.log` có `Tests run: 85` và `BUILD SUCCESS`; `backend/target/surefire-reports/com.skygo.backend.ai.MariaDbVectorIntegrationTest.txt` ghi 11/11 pass | Có 11 integration test MariaDB thật nhưng embedding giả, không chứng minh Gemini live; 85 test có thể trùng với 70 backend hiện tại, không cộng gộp thành 155 |

Lệnh backend đã chạy ở lần ~15:48, từ thư mục `backend`:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:MAVEN_OPTS='"-javaagent:C:\Users\biduq\.m2\repository\org\mockito\mockito-core\5.23.0\mockito-core-5.23.0.jar"'
.\mvnw.cmd -o '-Daether.syncContext.named.factory=noop' '-DforkCount=0' '-Dtest=AuthServiceTest,FlightSearchServiceTest,BookingPricingServiceTest,PassengerValidationServiceTest,SeatHoldServiceTest,AiChatServiceTest,AiAirportResolverTest,RagServiceTest,KnowledgeIndexerTest,PayosHttpGatewayTest,HomeServiceTest' test
```

Lệnh frontend đã chạy từ thư mục `frontend`:

```powershell
node --test tests/account.test.cjs tests/ai-chat.test.cjs tests/api.test.cjs tests/booking.test.cjs tests/favorite.test.cjs tests/payment.test.cjs
npm run lint
npm run typecheck
```

`MAVEN_OPTS`, chế độ offline, không fork và đường dẫn JDK/Mockito ở trên là cấu hình **máy đã chạy**, không phải bước bắt buộc trên mọi máy. Báo cáo XML trong `backend/target/surefire-reports` gồm nhiều lần chạy cũ (có test skipped và lỗi cũ đã qua); không dùng tổng XML lẫn thời điểm để báo cáo kết quả hiện tại. Log `WARN` mô phỏng 429 trong `AiChatServiceTest` là dữ liệu test; run kết thúc thành công.

## Chưa được xác nhận trong lần báo cáo này

| Phần | Trạng thái | Cần làm để chốt |
| --- | --- | --- |
| Tích hợp auth/search/booking/PayOS trên MariaDB test mới nhất | Đã chạy lại trong full suite lúc 21:50: 160 integration run, 159 pass, 1 skip Qdrant tùy chọn | Xem log full suite; PayOS gateway là giả lập, webhook PayOS thật vẫn chưa được xác nhận |
| E2E Expo Go trên thiết bị thật | Chưa ghi kết quả | Chạy các ca P0 ở [test-cases.md](test-cases.md), quay luồng và lưu ảnh lỗi nếu có |
| Webhook PayOS từ dịch vụ thật | Chưa ghi kết quả | Dùng môi trường thử nghiệm và tunnel, kiểm tra idempotency/trạng thái trên backend; không suy từ MockMvc/gateway giả |
| Gemini thật qua app, nhất là lỗi 503/429 | Chưa ghi kết quả cho bản nộp | Hỏi một chuyến đang có trong DB và một chính sách, so nguồn/giá; kiểm tra cách hiện thông báo khi provider lỗi. Không re-index chỉ để thử chat |
| Tính dễ dùng trên iOS/Android và mất mạng | Chưa ghi kết quả | Thử trên thiết bị và kích thước màn hình được nhóm sử dụng; lưu kết quả từng ca |

## Ghi lỗi và chốt vòng kiểm thử

Tạo bảng theo mẫu dưới đây khi nhóm bắt đầu chạy thiết bị. Không điền “đạt” cho các ca chưa thao tác:

| Mã lỗi | Test case | Các bước tái hiện | Mong đợi / thực tế | Mức độ | Trạng thái / người sửa | Bằng chứng / lần chạy lại |
| --- | --- | --- | --- | --- | --- | --- |
| _Chưa ghi nhận trong đợt tài liệu này_ | — | — | — | — | — | — |

Trước khi nộp, ghi commit/hash của phiên bản kiểm thử, số ca P0/P1 đã chạy, số Pass/Fail/Blocked, lỗi còn mở và người xác nhận. Các kết quả cũ chỉ là mốc tham chiếu; chạy lại sau mỗi thay đổi có tác động.
