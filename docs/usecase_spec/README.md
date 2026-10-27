# Đặc tả Use Case SkyGo

Bộ tài liệu này đặc tả luồng nghiệp vụ Use Case của SkyGo. Phạm vi chuẩn là [kế hoạch triển khai trong folder Flight](../../../Ke-hoach-trien-khai.md); các thay đổi so với kế hoạch cần được nhóm chốt trước khi sửa đặc tả.

## Bắt đầu từ đâu

1. Đọc [CONVENTIONS.md](./CONVENTIONS.md) để nắm module, actor, mã UC/BR/OQ và cách quản lý nguồn.
2. Đọc [STANDARD.md](./STANDARD.md) trước khi viết một Use Case.
3. Xem `00-module.md` để biết phạm vi, thuật ngữ và danh mục UC đã chốt.
4. Vào README và `rules.md` của feature liên quan; sau đó đọc file UC và sơ đồ của feature đó.

## Cấu trúc dự kiến

```text
usecase_spec/
  README.md
  CONVENTIONS.md
  STANDARD.md
  00-module.md
  auth/
  flight/
  booking/
  payment/
```

Mỗi thư mục module có README, danh sách/feature, nguồn business rule và Use Case. Sơ đồ nguồn Activity/Sequence gắn với Use Case tương ứng. Chưa tạo module hoặc UC nào khi phạm vi và mã chưa được chốt trong `00-module.md`.

## Phạm vi MVP đang theo

- Tài khoản User: đăng ký, đăng nhập, đăng xuất; mật khẩu lưu dạng băm.
- Tìm chuyến một chiều từ dữ liệu mẫu trong MySQL cho 1–4 người lớn; xem chi tiết trước đăng nhập; sắp xếp theo giá.
- Cần đăng nhập để bắt đầu đặt booking hoặc xem lịch sử.
- Nhập thông tin từng hành khách người lớn, chọn hành lý cơ bản, xem lại trước thanh toán.
- Tạo thanh toán PayOS; backend xác minh webhook trước khi cập nhật trạng thái thanh toán.
- Lưu booking, mã giao dịch và trạng thái trong MySQL; xác nhận booking đã thanh toán và chỉ cho User xem booking của chính mình.
- Phạm vi MVP còn có bảo vệ API, kiểm tra dữ liệu, giới hạn đăng nhập sai, chống trùng, xử lý lỗi và đo baseline hiệu năng.

Khứ hồi, nhiều chặng, chọn ghế, tích hợp API hãng bay/thời gian thực và vai trò Manager nằm ngoài phạm vi MVP. Xem chi tiết tại kế hoạch triển khai.

## Quy trình viết và rà soát

1. Ghi mục tiêu, actor và ranh giới module trong `00-module.md`.
2. Ghi business rule đã chốt trong `rules.md`; ghi điều chưa rõ thành OQ tại đúng feature.
3. Viết UC theo format `STANDARD.md`, tham chiếu business rule bằng mã.
4. Viết nguồn `.act` và `.seq` từ các bước/nhánh đã chốt trong UC.
5. Rà tính nhất quán giữa rule, UC, Activity và Sequence; cập nhật cả các tham chiếu chịu ảnh hưởng khi đổi rule.

## Công cụ và đầu ra

Các file Markdown, `.act` và `.seq` là nguồn có thể chỉnh sửa. Dùng bộ công cụ tại `docs/tools/usecase_flow/` để kiểm tra cấu trúc, sinh sơ đồ và xuất từng Use Case ra DOCX. Cài `python-docx` để xuất DOCX; Playwright/Chromium chỉ cần khi muốn sinh ảnh PNG tự động. Đầu ra nằm trong `docs/build/usecase-flow/`; không sửa tay file đầu ra sinh tự động.
