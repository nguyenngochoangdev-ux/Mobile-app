# Phạm vi đã chốt — KHÔNG SỬA SAU NGÀY NÀY

**Chốt ngày:** 2026-08-04
**Cơ sở:** `PROJECT.md` §3

> Đây là văn bản khóa phạm vi. Mọi đề xuất thêm tính năng phải đối chiếu với file này trước
> (`/scope-guard`). Sửa file này = thừa nhận phạm vi đang phình. Nếu buộc phải sửa, ghi
> **ngày sửa + lý do + thứ đã cắt để bù** vào mục Nhật ký thay đổi cuối file.

---

## Giữ — lõi, không cắt trong mọi tình huống

| Khối | Luận điểm phục vụ | Ghi chú |
|---|---|---|
| Điểm danh chống gian lận (QR động HMAC, device binding, offline queue) | Tầng oracle — làm cho phần chuỗi có nghĩa | 80% giá trị thực tiễn |
| Neo Merkle + `AnchorRegistry` | 2.2a — chống sửa hồi tố | Lý do tồn tại của đề tài |
| Verifier độc lập | 2.2b — xác minh sau tốt nghiệp | Bằng chứng trực quan nhất khi demo |
| Rule engine SpEL + `evidence_hash` | Đóng góp học thuật rõ nhất | Verifiable computation bản nhẹ |
| `IssuerRegistry` | 2.2c — nhiều bên cấp phát | ~40 dòng Solidity, rẻ |

## Đã cắt ở tuần 0 — không được thêm lại

| Khối | Tiết kiệm | Câu ghi vào báo cáo |
|---|---|---|
| `courses` + `enrollments` | ~4 ngày | "Khóa học nội bộ được mô hình hóa như một loại sự kiện; tách riêng là hướng phát triển." |
| `appeals` + `rewards` | ~3 ngày | "Khiếu nại và khen thưởng xử lý ngoài hệ thống ở phiên bản hiện tại." |
| MinIO | ~1 ngày | Dùng thư mục cục bộ. MinIO không đóng góp cho luận điểm nghiên cứu nào. |
| HD wallet per-student (BIP32, `m/44'/60'/0'/0/{index}`) | ~2 ngày | "Mô hình custodial một khóa tổ chức. Trong chuẩn W3C VC, `issuer` là tổ chức và sinh viên là `subject` — không cần khóa riêng cho sinh viên. DID per-student và account abstraction là hướng phát triển." |

**Tổng tiết kiệm: ~10 ngày.**

## Cắt có điều kiện — quyết tại cổng kiểm soát

| Khối | Cổng | Nếu chưa đạt |
|---|---|---|
| `StatusList` + thu hồi credential | Cuối tuần 3 | Cắt khỏi phạm vi deploy. **Số liệu gas bitmap vs mapping vẫn đo được thuần trên Hardhat local** — không mất chương 11.4 |
| Credential VC đầy đủ | Cuối tuần 4 | Rút về credential ký bằng một khóa issuer, bỏ bundle phức tạp |
| Ruleset 5 tiêu chí | Cuối tuần 5 | Rút xuống 3 tiêu chí, ghi vào giới hạn phạm vi |

---

## Danh sách cấm tuyệt đối

Không thảo luận lại, không có ngoại lệ:

- Hyperledger Fabric hoặc bất kỳ permissioned chain nào
- Tự viết cơ chế đồng thuận / tự dựng chain riêng
- Ví phi tập trung, seed phrase cho sinh viên, account abstraction, relayer, EIP-2771, gas station
- IPFS cluster, tokenomics, token thưởng, NFT chứng chỉ
- Microservice, message queue, Kubernetes, Redis, Kafka
- React Native / Flutter
- Contract upgradeable / proxy pattern
- DSL riêng cho rule engine (đã chốt SpEL)
- Tự viết CSS thay shadcn/ui

---

## Ba câu hỏi bắt buộc cho mọi đề xuất mới

1. Phục vụ luận điểm blockchain nào trong ba luận điểm (`PROJECT.md` §10)? *Không có → không làm.*
2. Tạo ra số liệu đo được cho chương 11 không? *Không → ưu tiên thấp.*
3. Tốn mấy ngày, và **cắt ở đâu để bù**? *Không chỉ ra được chỗ cắt → không làm.*

---

## Nhật ký thay đổi

| Ngày | Thay đổi | Lý do | Cắt gì để bù |
|---|---|---|---|
| 2026-08-04 | Chốt lần đầu | — | — |
