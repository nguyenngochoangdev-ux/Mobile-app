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
| Điểm danh nhiều lớp kiểm tra (QR động HMAC, device binding, offline queue) | Tầng oracle — làm cho phần chuỗi có nghĩa | 80% giá trị thực tiễn |
| Neo Merkle + `AnchorRegistry` | 2.2a — chống sửa hồi tố | Lý do tồn tại của đề tài |
| Verifier độc lập | 2.2b — xác minh sau tốt nghiệp | Bằng chứng trực quan nhất khi demo |
| Rule engine SpEL + `evidence_hash` | Đóng góp học thuật rõ nhất | Verifiable computation bản nhẹ |
| `IssuerRegistry` | 2.2c — nhiều bên cấp phát | ~40 dòng Solidity, rẻ |

> **Không gọi khối này là "điểm danh chống gian lận".** Nó **ngăn** được chia sẻ mật khẩu
> và ảnh chụp QR gửi sau, **tăng chi phí** của quét hộ lâu dài, nhưng **không ngăn** được
> việc đưa thẳng điện thoại đã đăng nhập cho bạn, cũng không ngăn được việc sao chép
> `deviceFp` (chỉ là UUID trong `localStorage`). "Chống gian lận" là lời hứa tuyệt đối và
> sẽ bị hội đồng bắt bẻ. Bảng đầy đủ: `docs/measurements.md` §11.2.

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
| 2026-08-08 | **Thêm đóng gói APK/TWA vào phạm vi** | Người dùng quyết, đảo hai lần từ chối trước | Không cắt gì. Xem ghi chú bên dưới |

### Ghi chú cho thay đổi 2026-08-08 — đóng gói APK

Đây là lần thứ ba đề tài xét việc đóng gói APK. Hai lần trước (2026-08-05, 2026-08-07)
`/scope-guard` đều từ chối. Lần này người dùng quyết định làm, và quyền quyết định phạm vi
thuộc về người dùng.

**Trả lời trung thực ba câu hỏi bắt buộc, không tô hồng:**

1. *Phục vụ luận điểm nào?* **Không luận điểm nào.** TWA là Chrome đang render đúng trang PWA
   đó. Nó không chạm vào Merkle, anchor, verifier, hay rule engine.
2. *Sinh số liệu cho chương 11?* **Không.**
3. *Tốn mấy ngày?* Ước lượng cũ (~2 ngày) **đã sai theo hướng có lợi**: người dùng đã tự cài
   Android SDK, tạo keystore, và build xong APK ký thật trước khi mở phiên này. Phần tốn thời
   gian nhất đã là chi phí chìm. Việc còn lại chỉ là tự động hoá và tài liệu, khoảng nửa ngày.

**Vì vậy không cắt gì để bù.** Nhưng phải ghi rõ cái giá thật, vì nó không nằm ở số ngày:

> APK **nung cứng domain vào trong lúc build**. Dự án dùng quick tunnel
> (`*.trycloudflare.com`), loại domain đổi mỗi lần chạy lại và bị Cloudflare thu hồi sau vài
> giờ. Nên **mỗi lần domain đổi là phải build lại APK và cài lại lên điện thoại**. Người dùng
> đã chọn đường này thay vì mua domain cố định, chấp nhận đánh đổi đó.

**Rủi ro còn lại, không khử được bằng code:** quên bước build lại ngay trước buổi bảo vệ thì
app mở ra báo `ERR_NAME_NOT_RESOLVED`. Đã xảy ra thật ngày 2026-08-08. Giảm rủi ro bằng
`scripts/build-apk.ps1` — nó kiểm domain còn sống trước khi build, nhưng **không thay được
việc phải nhớ chạy nó**.

**Không đổi gì trong lõi.** Backend, contract, canonicalization, verifier, rule engine giữ
nguyên. APK chỉ là lớp đóng gói ngoài cùng của PWA đã có.
