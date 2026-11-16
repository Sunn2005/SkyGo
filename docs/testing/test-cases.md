# Test case SkyGo

**Quy ước:** P0 = luồng chính/tiền/quyền riêng tư; P1 = trường hợp lỗi, hồi phục hoặc tiện ích. `Tự động` chỉ ra bài test tương ứng trong mã nguồn, **không phải kết quả chạy**. Kết quả thực tế được ghi ở [báo cáo kiểm thử](test-report-2026-11-11.md). Các bước thủ công cần thử trên đúng bản app/backend sẽ nộp.

## Tài khoản, sân bay và tìm chuyến

| Mã/ưu tiên | Tiền điều kiện và thao tác | Kết quả mong đợi | Tự động |
| --- | --- | --- | --- |
| ACC-01 P0 | Đăng ký tài khoản thử A bằng số điện thoại hợp lệ, rồi đăng nhập, mở hồ sơ và đăng xuất. | Tạo tài khoản một lần; hồ sơ đúng A; đăng xuất làm token cũ mất hiệu lực. | `AuthServiceTest`, `AuthIntegrationTest`, `account.test.cjs` |
| ACC-02 P1 | Đăng ký lại cùng số ở dạng `0...` và `+84...`; thử mật khẩu thiếu tiêu chí, rồi đăng nhập sai liên tiếp. | Không trùng tài khoản; lỗi nhập rõ ràng; sau ngưỡng thất bại bị khóa tạm thời. | `AuthServiceTest`, `AuthIntegrationTest`, `account.test.cjs` |
| AIR-01 P0 | Chọn điểm đi/đến từ danh mục khu vực, tìm bằng tên có/không dấu, chuyển tab và cuộn hết các trang. | Mã/tên sân bay đúng, kết quả không bị mất do phân trang; không chọn cùng sân bay cho hai đầu hành trình. | `AirportIntegrationTest`, `api.test.cjs` |
| FLT-01 P0 | Chọn một đường bay/ngày/hạng có chuyến trong DB, tìm một chiều và so mã chuyến, giờ, giá, số ghế với dữ liệu hiện tại. | Trả đúng chuyến và giá đã gồm VAT theo hợp đồng API; tìm kiếm không giữ ghế. | `FlightSearchServiceTest`, `FlightSearchIntegrationTest`, `api.test.cjs` |
| FLT-02 P0 | Tìm khứ hồi, ngày về sau ngày đi; chọn hạng cho từng chiều. | Chuyến về đảo đúng chiều, ngày theo múi giờ sân bay đi; giá/hạng mỗi chiều riêng. | `FlightSearchServiceTest`, `FlightSearchIntegrationTest`, `api.test.cjs` |
| FLT-03 P1 | Chọn ngày/tuyến không có chuyến; thử ngày sai, sân bay trùng, số khách sai; xem các ngày lân cận và tải thêm. | Kết quả trống hợp lệ không bị trình bày thành lỗi mạng; dữ liệu sai bị từ chối; tab ngày và phân trang nhất quán. | `FlightSearchIntegrationTest`, `FlightSearchServiceTest`, `booking.test.cjs` |
| FAV-01 P1 | A lưu/bỏ lưu chuyến; B đăng nhập cùng máy xem mục đã lưu. | Thao tác lặp không sinh bản ghi trùng; B không thấy dữ liệu của A. | `FavoriteFlightIntegrationTest`, `favorite.test.cjs` |

## Giữ chỗ và tạo đơn

| Mã/ưu tiên | Tiền điều kiện và thao tác | Kết quả mong đợi | Tự động |
| --- | --- | --- | --- |
| BKG-01 P0 | Khách chưa đăng nhập chọn chuyến/hạng rồi thử đặt; bấm tab Lịch sử. | App yêu cầu đăng nhập và giữ ngữ cảnh hợp lệ; trước khi chuyển từ Lịch sử hiển thị thông báo. | `SeatHoldIntegrationTest`, `booking.test.cjs`; dialog cần thử thủ công |
| BKG-02 P0 | A chọn chuyến, hạng, số người; tạo giữ chỗ rồi mở lại app trước hạn. | Chỉ giữ đúng số ghế, có hạn 15 phút từ backend; mở lại không tự gia hạn hoặc giữ ghế lần nữa. | `SeatHoldIntegrationTest`, `SeatHoldConcurrencyTest`, `booking.test.cjs` |
| BKG-03 P0 | Khi còn hold, thay chuyến/hạng/số khách, rồi gửi lại phiên bản hold cũ hoặc cùng lúc tranh ghế cuối. | Thay đổi nhất quán, hạn không đổi; phiên bản cũ bị chặn; không bán quá ghế. | `SeatHoldSelectionIntegrationTest`, `SeatHoldConcurrencyTest` |
| BKG-04 P0 | Nhập người lớn/trẻ em/em bé, giấy tờ và ngày sinh; thử tuổi/giấy tờ sai. | Kiểm tra tuổi theo ngày bay, giấy tờ đúng loại và số khách; không lưu dữ liệu không hợp lệ. | `PassengerValidationServiceTest`, `BookingReviewIntegrationTest`, `booking.test.cjs` |
| BKG-05 P0 | Tạo review, chọn hành lý thêm theo người/chiều, xem giá, VAT và tổng; sửa trên bản cũ. | Giá/thuế do backend tính; snapshot giấy tờ chỉ hiển thị che số; xung đột version không ghi đè. | `BookingQuoteIntegrationTest`, `BookingBaggageSaveIntegrationTest`, `BookingPricingServiceTest` |
| BKG-06 P0 | Xác nhận review để tạo đơn; lặp lại cùng Idempotency-Key rồi thử dùng key đó cho nội dung khác. | Một booking duy nhất, `PENDING_PAYMENT`; lặp đúng trả cùng kết quả, lặp khác bị từ chối. | `BookingCreationIntegrationTest`, `booking.test.cjs` |
| BKG-07 P0 | Hết hạn giữ chỗ hoặc dùng review cũ; thử xác nhận đơn. | Không tạo đơn/thanh toán dở dang; số ghế được trả đúng một lần. | `BookingCreationIntegrationTest`, `SeatHoldIntegrationTest` |
| BKG-08 P1 | Mở danh sách/chi tiết đặt vé của A, đăng nhập B thử truy cập ID của A; lọc trạng thái và phân trang. | A thấy đơn của mình; B không xem được thông tin A; danh sách không tiết lộ giấy tờ. | `BookingListIntegrationTest`, `BookingCreationIntegrationTest`, `payment.test.cjs` |

## Thanh toán và vé

| Mã/ưu tiên | Tiền điều kiện và thao tác | Kết quả mong đợi | Tự động |
| --- | --- | --- | --- |
| PAY-01 P0 | A tạo link/QR PayOS cho booking còn hạn, gọi lại cùng booking, mở app lần nữa. | Cùng link đang hợp lệ; số tiền lấy từ booking trên server; QR khớp đơn/hold/link và hạn. | `PaymentLinkIntegrationTest`, `payment.test.cjs` |
| PAY-02 P0 | Chỉ mở callback URL thành công hoặc bấm kiểm tra trạng thái khi PayOS chưa xác nhận. | Không tự chuyển booking thành `PAID` và không xuất vé. | `PaymentLinkIntegrationTest`, `payment.test.cjs` |
| PAY-03 P0 | Gửi webhook PayOS đã xác thực đúng chữ ký, số tiền, link và còn hạn; gửi lặp webhook. | Đúng một lần chuyển `PAID`, xuất vé/hóa đơn; sự kiện lặp không nhân đôi. | `PaymentLinkIntegrationTest` (gateway giả), `payment.test.cjs`; webhook thật cần thử riêng |
| PAY-04 P0 | Mô phỏng sai chữ ký, sai tiền, link cũ, đến sau hạn hoặc kết quả chưa xác minh. | Không cấp vé; ghi trạng thái cần đối soát theo nghiệp vụ; không tự sinh QR mới cho đơn chưa đối soát. | `PaymentLinkIntegrationTest`, `ResumePaymentIntegrationTest`, `payment.test.cjs` |
| PAY-05 P1 | Đơn hết hold nhưng đủ điều kiện tiếp tục: mở lại, xác nhận giữ lại ghế; thử hết ghế hoặc qua cutoff. | Giữ lại tối đa theo backend, giá/hành khách/hành lý booking không đổi; thiếu ghế/đóng đơn không thanh toán tiếp. | `ResumePaymentIntegrationTest`, `payment.test.cjs` |
| PAY-06 P1 | Trên iPhone/Android thử lưu ảnh QR; từ chối quyền ảnh rồi thử lại. | Ảnh PNG hợp lệ nếu được cấp quyền; từ chối quyền báo lỗi đúng, không báo đã lưu. | `payment.test.cjs`; quyền/hiển thị cần thử trên thiết bị |

## AI/RAG, bảo mật và thiết bị

| Mã/ưu tiên | Tiền điều kiện và thao tác | Kết quả mong đợi | Tự động |
| --- | --- | --- | --- |
| AI-01 P0 | Hỏi chuyến bay bằng tên thành phố đang có trong DB, ngày và số người; kiểm tra kết quả với API tìm chuyến/SQL tại cùng thời điểm. | AI hiểu sân bay hợp lệ; chuyến, giá, chỗ dựa trên SQL hiện tại; không bịa chuyến khác khi không có kết quả. | `AiAirportResolverTest`, `AiReadRepositoryTest`, `AiChatServiceTest` |
| AI-02 P0 | Hỏi chính sách hành lý và một câu hỏi ngoài phạm vi tài liệu; kiểm tra `sources`. | Trả lời có căn cứ từ tài liệu public đúng model; khi thiếu bằng chứng nói rõ không đủ dữ liệu. | `RagServiceTest`, `AiKnowledgeReaderTest`, `MariaDbVectorIntegrationTest` |
| AI-03 P0 | Khách hỏi lịch sử đặt vé; A đăng nhập hỏi đơn gần đây, rồi B hỏi cùng ID hoặc đưa user ID của A trong prompt/history. | Khách được nhắc đăng nhập; A chỉ thấy đơn A; B không truy cập được A dù tự khai danh tính. | `AiChatControllerTest`, `AiChatServiceTest`, `AiReadRepositoryTest` |
| AI-04 P1 | Hỏi tên địa điểm mơ hồ, thiếu ngày; thử IATA không tồn tại. | Yêu cầu làm rõ và gợi ý sân bay thực tế; không chọn sân bay gần vector nhất một cách tùy tiện. | `AiAirportResolverTest`, `AiChatServiceTest` |
| AI-05 P0 | Khi Gemini trả 429/503/timeout, gửi prompt không hợp lệ/quá dài hoặc AI bị tắt. | UI báo lỗi/thử lại hợp lý; không lộ key, stack trace hoặc giả vờ có kết quả; không tự gọi lại quá quota. | `AiChatControllerTest`, `GeminiEmbeddingFailureTest`, `ai-chat.test.cjs` |
| AI-06 P0 | Index lại dữ liệu không đổi; thay dữ liệu một document; thử lỗi embedding giữa batch; tìm tài liệu private qua truy vấn public. | Không embed lại dữ liệu cũ; lỗi không xóa nhầm dữ liệu; lọc quyền và model khi truy vấn. | `KnowledgeIndexerTest`, `MariaDbVectorIntegrationTest`, `RagServiceTest` |
| SYS-01 P0 | Trên điện thoại thật, mở app khi backend/tunnel đang chạy; sau đó tắt mạng/backend rồi tải lại. | Trang chủ hiển thị dữ liệu thật; khi mất kết nối báo lỗi và cho tải lại, không coi dữ liệu lỗi là rỗng. | `HomeServiceTest`, `api.test.cjs`; mạng thật cần thử thủ công |
| SYS-02 P1 | Thử luồng chính trên kích thước điện thoại, bàn phím mở/đóng, danh sách dài, quay lại màn trước. | Nội dung và nút thao tác không bị che, không tràn chiều ngang; thông tin đã nhập không mất ngoài ý muốn. | Chưa có E2E UI tự động; kiểm thử thủ công |

## Mẫu ghi nhận từng ca thủ công

Sao chép mỗi dòng sau khi chạy, không đánh dấu Pass chỉ vì tên test tự động tương ứng có trong bảng:

| Mã ca | Dữ liệu thử cụ thể | Kết quả thực tế | Trạng thái | Bằng chứng/bug |
| --- | --- | --- | --- | --- |
| FLT-01 | `IATA đi/đến`, ngày, mã chuyến, hạng, số khách; snapshot DB tại thời điểm thử | _Chưa điền_ | Chưa chạy | _Chưa điền_ |

