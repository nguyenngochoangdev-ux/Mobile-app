# nckh-drl

Sổ tay hoạt động sinh viên & tự động hóa chấm điểm rèn luyện, neo dữ liệu lên blockchain.
Đề tài NCKH — 1 người, 8 tuần.

📄 **Đọc trước khi code:** [`PROJECT.md`](PROJECT.md) · [`docs/scope.md`](docs/scope.md)

## Cấu trúc

```
contracts/   Hardhat — IssuerRegistry, AnchorRegistry, StatusList (Polygon Amoy)
backend/     Spring Boot 3.5.16 đơn khối, 9 package nghiệp vụ
app/         PWA — student + staff + presenter
verifier/    Static, chỉ ethers + merkletreejs. KHÔNG gọi backend.
docs/        scope, ERD, measurements, báo cáo
scripts/     tiện ích
```

## Chạy lần đầu

```bash
cp .env.example .env      # rồi điền JWT_SECRET, AMOY_RPC_URL
docker compose up -d      # MySQL 8.4
cd backend && ./mvnw spring-boot:run
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

Windows dùng `mvnw.cmd` thay `./mvnw`.

## Ranh giới kiến trúc — không được vi phạm

| Ranh giới | Lý do |
|---|---|
| `anchor` không import gì từ package nghiệp vụ | Nhận `List<leaf>` + `domain`, trả `root` + `proof`. Giữ được thì đo đạc tuần 7 chỉ là gọi hàm với N khác nhau |
| `verifier/` không gọi backend một dòng nào | Là điểm bán hàng chính của đề tài (luận điểm 2.2b). `package.json` chỉ được có `ethers` + `merkletreejs` |
| `ddl-auto: validate`, không bao giờ `update` | Schema do Flyway quản lý, một nguồn sự thật |
| Mọi thời gian UTC, `DATETIME(3)` | Đổi độ chính xác làm lệch leaf hash Java↔JS → mọi proof fail |

## Ba thứ dễ làm hỏng đề tài

1. **Phạm vi phình rồi cắt vội ở tuần 6** — tôn trọng cổng kiểm soát cuối tuần 2/3/5/6
2. **Lệch canonicalization Java↔JS** — fail im lặng. Test vector bắt buộc từ tuần 3
3. **Dồn báo cáo vào cuối** — tuần 7 khóa cứng, không viết code tính năng

## Lưu ý môi trường

- **RPC:** không dùng `rpc-amoy.polygon.technology` — Polygon đã ngừng RPC công cộng 17/07/2026. Dùng Alchemy/Infura/Chainstack.
- **Spring Boot 3.5.16** là lựa chọn có chủ ý dù đã EOL — lý do ghi trong `backend/pom.xml`.
