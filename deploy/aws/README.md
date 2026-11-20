# Triển khai SkyGo backend lên AWS (Lightsail, không cần tên miền riêng)

Một máy chủ chạy `docker compose` gồm: backend Spring Boot, MariaDB 11.8
(cần ≥ 11.7 cho kiểu `VECTOR`), Redis và Caddy (tự cấp HTTPS). Địa chỉ công khai
dùng `sslip.io`, ví dụ IP `13.229.10.5` → `https://13-229-10-5.sslip.io`.

## 1. Tạo máy chủ trên AWS Lightsail
1. Vào Lightsail → **Create instance** → Linux/Unix → **OS Only → Ubuntu 24.04 LTS**.
2. Chọn gói **tối thiểu 2 GB RAM** (4 GB thoải mái hơn). Xem giá và hạn dùng
   credit trong AWS Billing trước khi tạo.
3. Tab **Networking** của instance:
   - **Attach static IP** (tạo Static IP và gắn vào máy). Việc này giữ IP cố định.
   - **IPv4 Firewall**: mở **TCP 80** và **TCP 443**. Giữ **TCP 22** (SSH).
     Không mở cổng 3306, 6379 hay 8080.
4. Đặt **AWS Budgets** cảnh báo (ví dụ $20) để không vượt ngân sách.

## 2. Cài Docker trên máy chủ
SSH vào máy (nút "Connect using SSH" trên Lightsail), rồi chạy:
```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
```
Thoát SSH và vào lại để nhóm `docker` có hiệu lực. Nếu máy 2 GB, thêm swap:
```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

## 3. Lấy mã nguồn và cấu hình
```bash
git clone https://github.com/Sunn2005/SkyGo.git
cd SkyGo/deploy/aws
cp .env.example .env
nano .env
```
Điền `.env` (không gửi file này cho ai, không commit):
- `SITE_HOST`: IP tĩnh với dấu `.` đổi thành `-`, thêm `.sslip.io`.
- `MARIADB_ROOT_PASSWORD`, `MARIADB_PASSWORD`: tạo bằng `openssl rand -base64 24`.
- `SKYGO_REVIEW_ENCRYPTION_KEY`, `SKYGO_BOOKING_DOCUMENT_ENCRYPTION_KEY`: tạo bằng
  `openssl rand -base64 32`. **Giữ nguyên vĩnh viễn**, mất khoá là dữ liệu đã mã
  hoá không đọc lại được. Hãy sao lưu chúng ở nơi an toàn.
- `SKYGO_AI_ENABLED=false` nếu chưa có `GEMINI_API_KEY`, nếu không backend không khởi động.
- Khoá PayOS: điền khi cần demo thanh toán.

## 4. Chạy
```bash
docker compose up -d --build
docker compose logs -f backend
```
Lần đầu build mất vài phút. Flyway tự chạy 14 migration trên database mới.
Khi log có `Started ... in ... seconds`, kiểm tra:
```bash
curl https://<SITE_HOST>/api/v1/regions
```
Phải trả về JSON danh sách vùng. Chứng chỉ HTTPS có thể mất vài chục giây ở lần đầu.

## 5. Trỏ app tới backend
Trong `frontend/.env.local`:
```
EXPO_PUBLIC_API_URL=https://<SITE_HOST>
```
Biến này nhúng vào bản build, nên phải **build lại app**.

## 6. PayOS
Đăng ký webhook với PayOS thành `https://<SITE_HOST>/api/v1/payos/webhook`.

## Vận hành
- Cập nhật: `git pull && docker compose up -d --build`.
- Xem log: `docker compose logs -f backend` (hoặc `mariadb`, `caddy`).
- **Sao lưu database** (không có sao lưu tự động):
  ```bash
  docker compose exec mariadb sh -c 'mariadb-dump -uroot -p"$MARIADB_ROOT_PASSWORD" --single-transaction skygo' > skygo-$(date +%F).sql
  ```
  Bật thêm snapshot của Lightsail và chép file `.sql` ra ngoài máy.
- Dữ liệu chuyến bay demo (migration V13) cố định ngày 23–28/11/2026 nên sẽ hết hạn.
- Dừng khi không dùng để tiết kiệm credit: `docker compose stop`. Tắt hẳn máy trên
  Lightsail vẫn tính tiền, chỉ xoá instance mới ngừng.
