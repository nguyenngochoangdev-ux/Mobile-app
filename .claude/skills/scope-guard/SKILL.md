---
name: scope-guard
description: Kiểm tra một tính năng/ý tưởng có nằm trong phạm vi đã chốt của đề tài NCKH không, trước khi viết code. Dùng khi người dùng đề xuất thêm tính năng, đổi kiến trúc, thêm dependency, hoặc hỏi "có nên làm X không". Cũng dùng khi tự thấy mình sắp viết code cho thứ không có trong PROJECT.md.
---

# Scope guard

Đề tài này có 8 tuần, một người. Rủi ro lớn nhất **không phải** thiếu năng lực kỹ thuật — mà là phạm vi phình ra rồi phải cắt vội ở tuần 6. Skill này là cái phanh.

## Quy trình

### 1. Đọc phạm vi đã chốt
Đọc `docs/scope.md` (bản đã ký ở tuần 0) và §3 của `PROJECT.md`. Nếu `docs/scope.md` chưa tồn tại, nói rõ với người dùng là phạm vi chưa được chốt — đó là việc phải làm trước.

### 2. Đối chiếu danh sách cấm tuyệt đối
Nếu đề xuất chạm vào bất kỳ mục nào dưới đây, **từ chối thẳng** và nêu lý do:

- Hyperledger Fabric hoặc bất kỳ permissioned chain nào (dựng CA + orderer + chaincode = ~3 tuần)
- Tự viết cơ chế đồng thuận / tự dựng chain riêng
- Ví phi tập trung, seed phrase cho sinh viên, account abstraction, relayer, EIP-2771, gas station
- IPFS cluster, tokenomics, token thưởng, NFT chứng chỉ
- Microservice, message queue, Kubernetes, Redis, Kafka
- React Native / Flutter (học song song Solidity trong 8 tuần một mình = thất bại)
- Contract upgradeable / proxy pattern
- DSL riêng cho rule engine (đã chốt SpEL)
- Tự viết CSS thay shadcn/ui

### 3. Đối chiếu khối chức năng

**Lõi — không đụng tới, không cắt:**
điểm danh nhiều lớp kiểm tra · neo Merkle + AnchorRegistry · verifier độc lập · rule engine + `evidence_hash` · IssuerRegistry

**Đã cắt ở tuần 0 — không được thêm lại:**
courses + enrollments · appeals + rewards · MinIO · HD wallet per-student

**Cắt có điều kiện — kiểm tra cổng đã qua chưa:**
StatusList (cổng cuối tuần 3) · credential VC đầy đủ (cổng cuối tuần 4)

### 4. Nếu đề xuất nằm ngoài mọi mục trên
Hỏi ba câu, theo đúng thứ tự:

1. **Nó phục vụ luận điểm nào trong ba luận điểm blockchain (§10 PROJECT.md)?**
   Không phục vụ luận điểm nào → không làm.
2. **Nó tạo ra số liệu đo được cho chương 11 không?**
   Không → giá trị nghiên cứu thấp, ưu tiên thấp.
3. **Chi phí bao nhiêu ngày, và lấy từ đâu ra?**
   Ngân sách đã hết. Thêm X ngày nghĩa là **cắt X ngày ở chỗ khác** — bắt người dùng chỉ ra chỗ cắt. Không có chỗ cắt = không làm.

### 5. Trả lời
Đưa ra kết luận rõ ràng, một trong ba:

- **Trong phạm vi** — làm luôn.
- **Ngoài phạm vi, đề xuất hoãn** — ghi vào phần "hướng phát triển" của báo cáo. Nêu chính xác câu để viết.
- **Ngoài phạm vi, đề xuất đánh đổi** — nêu rõ phải cắt gì để đổi lấy, để người dùng tự quyết.

Không tâng bốc ý tưởng. Nếu một đề xuất nghe hay nhưng làm vỡ tiến độ, nói thẳng là nó làm vỡ tiến độ.

## Nhắc

Hội đồng chấm báo cáo và buổi bảo vệ, không chấm số dòng code. Thêm tính năng gần như luôn làm giảm điểm, vì nó ăn vào thời gian đo đạc và viết báo cáo — thứ thật sự được chấm.
