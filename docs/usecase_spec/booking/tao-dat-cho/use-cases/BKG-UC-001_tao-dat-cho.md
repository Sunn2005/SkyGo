### BKG-UC-001_Tạo đặt chỗ

### Mô tả use case BKG-UC-001

| Trường | Nội dung |
|---|---|
| Use case: | BKG-UC-001_Tạo đặt chỗ |
| Mục đích: | Cho phép User tạo booking cho chuyến bay đã chọn và các hành khách người lớn đi cùng. |
| Mô tả: | User nhập thông tin hành khách, chọn hành lý, xem lại tổng giá và xác nhận tạo booking để tiếp tục thanh toán PayOS. |
| Actor: | User |
| Tiền điều kiện: | User đã đăng nhập {{ref: BR-BKG-001}}<br>User đã chọn chuyến một chiều trong dữ liệu mẫu và số lượng hành khách từ 1 đến 4 người lớn {{ref: BR-BKG-002, BR-BKG-004}} |
| Hậu điều kiện: | Booking cùng thông tin hành khách và lựa chọn hành lý được lưu, hệ thống trả mã booking để tiếp tục thanh toán |

### Luồng sự kiện chính

| User | Hệ thống |
|---|---|
| 1. Chọn “Bắt đầu đặt chỗ” tại chuyến bay đã chọn. | 2. Hiển thị biểu mẫu thông tin cho từng hành khách người lớn. |
| 3. Nhập thông tin hành khách và chọn gói hành lý cho từng người. | 4. Kiểm tra số lượng hành khách, dữ liệu bắt buộc, lựa chọn hành lý và chuyến bay. {{ref: BR-BKG-002, BR-BKG-004, BR-BKG-101, BR-BKG-102}} |
| 5. Xem lại chuyến bay, hành khách, hành lý và tổng giá. | 6. Hiển thị thông tin tổng hợp để User kiểm tra trước khi xác nhận. {{ref: BR-BKG-003, BR-BKG-103}} |
| 7. Xác nhận tạo booking. | 8. Lưu booking cùng hành khách và lựa chọn hành lý; trả mã booking để tiếp tục thanh toán. |
|  | 9. Hiển thị mã booking và hướng dẫn tiếp tục sang luồng thanh toán PayOS. |

### Luồng sự kiện phụ

| User | Hệ thống |
|---|---|
|  | 2.1. Nếu chuyến bay không còn phù hợp hoặc không tồn tại trong dữ liệu mẫu, thông báo cho User và yêu cầu chọn lại chuyến. {{ref: BR-BKG-004, BR-BKG-105}} |
|  | 4.1. Nếu số lượng hành khách hoặc dữ liệu nhập không hợp lệ, đánh dấu thông tin cần sửa và không cho tiếp tục tới bước xem lại. {{ref: BR-BKG-002, BR-BKG-101, BR-BKG-105}} |
|  | 7.1. Nếu yêu cầu xác nhận bị gửi lặp, trả lại mã booking đã tạo trước đó và không tạo booking thứ hai. {{ref: BR-BKG-005, BR-BKG-104}} |
|  | 8.1. Nếu không lưu được booking, thông báo lỗi và cho phép User thử xác nhận lại; không hiển thị mã booking thành công. {{ref: BR-BKG-105}} |

### Biểu đồ

Activity: `../diagrams/BKG-UC-001_activity.act`

Sequence: `../diagrams/BKG-UC-001_sequence.seq`
