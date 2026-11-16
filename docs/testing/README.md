# Kế hoạch kiểm thử SkyGo

Tài liệu này đáp ứng phần **Testing** trong [`MotaDetai_SDLC.txt`](../../../MotaDetai_SDLC.txt): thiết kế test case, thực hiện unit/integration test và ghi lại kết quả. Đọc [test-cases.md](test-cases.md) để thực hiện từng ca và [test-report-2026-11-11.md](test-report-2026-11-11.md) để xem bằng chứng hiện có. Phạm vi là source tại thời điểm 11/11/2026; khi đổi code hoặc dữ liệu, tạo một lần chạy báo cáo mới.

## Phạm vi và cách kiểm thử

| Tầng | Mục tiêu | Công cụ/bằng chứng |
| --- | --- | --- |
| Unit | Kiểm tra quy tắc giá, ngày bay, hành khách, giữ chỗ, phân quyền, lập chỉ mục và xử lý AI | JUnit 5/Mockito ở `backend/src/test`, Node test ở `frontend/tests` |
| Integration | Kiểm tra HTTP + Spring + MariaDB, migration, giao dịch/rollback, hợp đồng PayOS với gateway giả, MariaDB Vector | JUnit/Spring Test, MockMvc; báo cáo Surefire XML |
| Hệ thống trên điện thoại | Kiểm tra chuỗi thao tác thực trên Expo Go với backend, khả năng hiển thị và thông báo lỗi | Test case thủ công; ảnh/video và log đã che dữ liệu nhạy cảm |
| Kết nối bên ngoài | Gemini thật, PayOS thật, tunnel, mạng điện thoại | Chạy riêng có kiểm soát; ghi mã trạng thái, thời điểm và môi trường, không ghi API key/token |

Các test Node dùng `fetch` hoặc dữ liệu giả lập; chúng **không** chứng minh kết nối mạng điện thoại, giao dịch PayOS thật hay nội dung Gemini thật. Integration test PayOS dùng gateway giả, còn integration test MariaDB Vector dùng embedding giả; không tính chúng là kiểm thử provider trực tiếp. `LiveRagSmokeTest` chỉ chạy khi chủ động bật, có thể tiêu tốn quota Gemini.

## Môi trường và dữ liệu

- Backend: JDK 21, Spring Boot, MariaDB 11.8, migration Flyway V1–V12. Frontend: Node và Expo SDK theo `frontend/package.json`, điện thoại có Expo Go tương thích.
- Chạy unit test không cần dùng dữ liệu sản xuất. Các integration test cần `SKYGO_TEST_DB_URL` trỏ **đúng database riêng có `_test` trong tên**, không dùng database `skygo` đang chứa đơn thật/demo. Một số test có fixture rollback, một số test migration tạo/xóa database tạm tên UUID; đọc cấu hình test trước khi chạy trên máy mới.
- Dữ liệu chuyến bay mẫu phụ thuộc ngày nạp migration; hãy chọn một chuyến **đang có trong DB ở thời điểm chạy**. Lưu IATA, ngày giờ địa phương, mã chuyến, hạng và giá trước khi so sánh; không cố định ngày bay cũ trong kết luận kiểm thử.
- Tạo ít nhất hai tài khoản thử A/B để kiểm tra quyền truy cập chéo; dùng email/số điện thoại thử nghiệm và không đưa thông tin giấy tờ thật vào ảnh hoặc báo cáo.
- Với PayOS, phân biệt `PENDING_PAYMENT`, `PAID` và giao dịch cần đối soát. Chỉ dùng tài khoản/môi trường thanh toán thử nghiệm theo cấu hình của nhóm; không tạo giao dịch tiền thật chỉ để điền bảng kết quả.
- AI công khai tra cứu chính sách/chuyến bay; tra cứu lịch sử cần đăng nhập. Giá/chỗ lấy từ SQL hiện tại, tài liệu chính sách từ vector. Nếu không có chuyến phù hợp, kết quả đúng là không có dữ liệu; không lấy một chuyến khác để minh họa như thật.

Để chọn **chuyến thực sự có trong database** cho FLT-01/AI-01, chạy câu SQL chỉ đọc này trong schema kiểm thử (hoặc schema demo của nhóm). Thời gian trong cột `departure_at_utc` là UTC; đổi sang ngày địa phương của sân bay đi theo `origin_time_zone` trước khi nhập vào app:

```sql
SELECT f.public_id, f.flight_number, o.code AS origin_code,
       d.code AS destination_code, o.time_zone AS origin_time_zone,
       f.departure_at_utc, ff.cabin_class, ff.available_seats,
       ff.adult_base_fare,
       ROUND(ff.adult_base_fare * (1 + p.vat_rate), 0) AS adult_price_with_vat
FROM flight AS f
JOIN air_route AS r ON r.id = f.route_id
JOIN airport AS o ON o.id = r.origin_airport_id
JOIN airport AS d ON d.id = r.destination_airport_id
JOIN flight_fare AS ff ON ff.flight_id = f.id
JOIN pricing_policy AS p ON p.id = ff.pricing_policy_id
WHERE f.sale_status = 'ON_SALE' AND ff.is_on_sale = 1
  AND r.is_active = 1 AND o.is_active = 1 AND d.is_active = 1
  AND ff.available_seats > 0 AND f.departure_at_utc > UTC_TIMESTAMP()
ORDER BY f.departure_at_utc, f.id, ff.cabin_class
LIMIT 20;
```

Giá trên chỉ để đối chiếu **một người lớn, một chiều, chưa có hành lý mua thêm**. Với nhiều hành khách/hành lý, dùng báo giá từ backend để đối chiếu tổng tiền. Một chuyến có thể không hiện trong app nếu đã qua hạn đóng bán hoặc vi phạm quy tắc bán khác.

## Cách chạy tự động

Từ thư mục `frontend`:

```powershell
node --test tests/account.test.cjs tests/ai-chat.test.cjs tests/api.test.cjs tests/booking.test.cjs tests/favorite.test.cjs tests/payment.test.cjs
npm run typecheck
npm run lint
```

Từ thư mục `backend` (unit test chọn lọc có thể chạy mà không mở kết nối Gemini/PayOS):

```powershell
.\mvnw.cmd '-Dtest=AuthServiceTest,FlightSearchServiceTest,BookingPricingServiceTest,PassengerValidationServiceTest,SeatHoldServiceTest,AiChatServiceTest,AiAirportResolverTest,RagServiceTest,KnowledgeIndexerTest,PayosHttpGatewayTest,HomeServiceTest' test
```

Muốn chạy integration test trên **database riêng**, đặt `SKYGO_TEST_DB_URL`, `SKYGO_TEST_DB_USERNAME`, `SKYGO_TEST_DB_PASSWORD` theo [backend/README.md](../../backend/README.md#kiểm-thử), rồi chạy `.\mvnw.cmd test` trong PowerShell. Biến `SKYGO_VECTOR_DB_TEST=true` bật bộ MariaDB Vector tạo/xóa DB thử nghiệm UUID. Không bật `SKYGO_AI_LIVE_TEST` khi chỉ kiểm thử cục bộ vì test này gọi Gemini thật.

Máy đang dùng JDK/Mockito khác có thể cần cấu hình Maven riêng; ghi **lệnh thực tế đã chạy** và trạng thái skipped/fail trong báo cáo. Báo cáo XML ở `backend/target/surefire-reports`; vì thư mục này giữ kết quả nhiều lần chạy, không cộng tất cả XML để tuyên bố một lần chạy thành công. Dùng phần `Tests run` và `BUILD SUCCESS` của **một lần chạy**.

## Tiêu chí hoàn tất trước khi nộp

1. Tất cả ca ưu tiên P0 trong [test-cases.md](test-cases.md) có kết quả thực tế trên bản build sẽ nộp; lưu bằng chứng cho mỗi lỗi đã sửa và chạy lại.
2. Unit test và integration test có tổng số run/pass/fail/skip, môi trường, lệnh và bằng chứng. Ca bị skip không được tính là đã đạt.
3. Luồng Expo Go với backend thật được quay/chụp; trường hợp PayOS/Gemini bên ngoài chưa thử thì ghi rõ giới hạn, không suy ra thành công từ test giả lập.
4. Các lỗi còn mở ghi mã lỗi, cách tái hiện, mức độ, người phụ trách và quyết định xử lý; kết quả kiểm thử cập nhật theo cùng commit với bản nộp.

Mẫu dòng ghi kết quả thủ công: `Mã ca | phiên bản/commit | thời gian | thiết bị/OS | dữ liệu thử | kết quả mong đợi | kết quả thực tế | Pass/Fail/Blocked | ảnh/log | mã lỗi`. Che token, số giấy tờ, API key, QR và dữ liệu cá nhân trước khi chia sẻ.
