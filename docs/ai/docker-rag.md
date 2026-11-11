# Qdrant cũ: chỉ dùng làm nguồn chuyển dữ liệu

SkyGo đã chuyển runtime vector sang MariaDB. Máy mới không cần Docker cho phần AI; làm theo [hướng dẫn Windows](../SETUP-NEW-MACHINE-WINDOWS.md).

`compose.rag.yaml` được giữ để có thể đọc dữ liệu Qdrant đã lập index trước đây. Khi cần chuyển:

```powershell
docker compose -f .\compose.rag.yaml up -d
(Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:6333/readyz').StatusCode
```

Thực hiện [hướng dẫn chuyển vector](mariadb-vector-migration.md), kiểm tra kết quả, rồi dừng dịch vụ:

```powershell
docker compose -f .\compose.rag.yaml stop
```

Giữ volume làm bản nguồn đối chiếu; không dùng `down -v`. Backend mới và script index không kết nối Qdrant. Chỉ tiện ích chuyển một lần dùng REST cổng 6333; adapter gRPC Qdrant đã được gỡ khỏi dependency của ứng dụng.
