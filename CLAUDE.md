# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Dự án

Đề tài NCKH: sổ tay hoạt động sinh viên + tự động hóa chấm điểm rèn luyện, neo dữ liệu lên blockchain.
**Ràng buộc cứng: 1 người, 8 tuần, nghiệm thu.**

- `PROJECT.md` — kế hoạch triển khai, phạm vi, cổng kiểm soát, đánh giá khả thi. **Đọc trước khi làm bất cứ việc gì.**
- `Xay-dung-mobile-app-blockchain-Huong-dan-trien-khai.docx` — tài liệu gốc. `PROJECT.md` §2 liệt kê các điểm trong tài liệu này đã lỗi thời hoặc cần sửa.

## Cách làm việc

### Tiếng Việt phải đọc trôi

Trả lời bằng tiếng Việt. Viết sao cho người đọc hiểu ngay lần đầu, không phải đọc lại.

- **Câu ngắn, mỗi câu một ý.** Câu nào dài quá hai dòng thì tách đôi.
- **Đừng bê cấu trúc tiếng Anh sang.** Tránh "thứ mà", "cái mà", "điều đó có nghĩa là",
  "nó là thứ duy nhất...". Viết như người Việt nói chuyện.
- **Nói rõ ai làm gì.** Tránh câu trống chủ ngữ.
- **Hạn chế dấu gạch ngang và ngoặc đơn chèn giữa câu.** Chúng cắt mạch đọc. Tách thành câu
  riêng thì dễ hiểu hơn.
- **Thuật ngữ kỹ thuật giữ nguyên tiếng Anh** nếu dịch ra khó hiểu hơn: commit, deploy, hash,
  proof, bundle. Đừng dịch nửa vời.
- **Đọc lại trước khi gửi.** Chỗ nào phải đọc hai lần mới hiểu thì viết lại.

Quy tắc này áp dụng cho câu trả lời trong chat. Chú thích trong mã nguồn và tài liệu cũng nên
theo, nhưng ở đó được phép dài hơn khi cần giải thích lý do.

### Trung thực kỹ thuật
- Dựa vào nguồn tin cậy trên internet, không trả lời từ trí nhớ với các câu hỏi về version, EOL, tình trạng bảo trì thư viện, hay quy định pháp luật. Tra rồi mới trả lời.
- **Không tâng bốc.** Nếu một ý tưởng làm vỡ tiến độ, nói thẳng là nó làm vỡ tiến độ. Nếu một ước lượng thời gian phi thực tế, đưa ra ước lượng của mình kèm lý do.
- Khi tài liệu gốc sai hoặc lạc quan quá mức, chỉ ra — kể cả khi nó là tài liệu người dùng đã viết.
- Báo cáo kết quả đúng như nó là: test fail thì nói fail kèm output, bỏ qua bước nào thì nói rõ.

### Kiểm tra khả thi trước khi code
Mỗi khi người dùng đề xuất tính năng mới, đổi kiến trúc, hoặc thêm dependency → **gọi `/scope-guard` trước.** Ngân sách 8 tuần đã hết; thêm gì cũng phải cắt gì.

Ba câu hỏi mặc định cho mọi đề xuất:
1. Phục vụ luận điểm blockchain nào trong ba luận điểm (`PROJECT.md` §10)?
2. Tạo ra số liệu đo được cho chương 11 không?
3. Tốn mấy ngày, và cắt ở đâu để bù?

## Skills

| Skill | Khi nào |
|---|---|
| `/scope-guard` | Trước khi thêm tính năng, đổi kiến trúc, thêm dependency |
| `/canonical-hash` | Khi động vào bất cứ gì ảnh hưởng leaf hash — bắt buộc, không ngoại lệ |
| `/measurements` | Khi có số liệu đo mới, khi deploy contract, khi chạy chấm điểm hàng loạt |

## Ba thứ dễ làm hỏng đề tài nhất

1. **Phạm vi phình ra rồi cắt vội ở tuần 6.** Tôn trọng cổng kiểm soát cuối tuần 2/3/5/6 (`PROJECT.md` §7). Cắt sớm, không cắt muộn.
2. **Lệch canonicalization Java↔JS.** Mọi Merkle proof fail, và fail im lặng. Test vector là bắt buộc từ tuần 3.
3. **Dồn báo cáo vào cuối.** Tuần 7 khóa cứng cho viết báo cáo, không viết code tính năng kể cả khi còn bug.

## Danh sách cấm tuyệt đối

Hyperledger Fabric · tự dựng chain / cơ chế đồng thuận · seed phrase cho sinh viên · IPFS cluster · tokenomics / NFT chứng chỉ · microservice / Kafka / Redis / Kubernetes · React Native / Flutter · contract upgradeable · DSL riêng cho rule engine

## Stack (chốt — xem `PROJECT.md` §4)

Java 21 + Spring Boot 3.5.16 + MySQL 8 + Flyway · web3j 5.0.3 · Solidity + Hardhat + OpenZeppelin v5 trên Polygon Amoy (chainId 80002, **RPC bên thứ ba, không dùng RPC công cộng Polygon**) · Vite + React 18 + TS + shadcn/ui PWA · verifier tĩnh chỉ `ethers` + `merkletreejs`

**Ranh giới cứng:** module `anchor` không import gì từ nghiệp vụ. Verifier không gọi backend một dòng nào.
