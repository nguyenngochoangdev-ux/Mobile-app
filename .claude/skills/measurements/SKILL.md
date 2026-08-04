---
name: measurements
description: Ghi lại số liệu đo cho chương 11 báo cáo NCKH vào docs/measurements.md. Dùng khi vừa chạy xong một phép đo (gas, benchmark thời gian, threat model, khảo sát SUS), khi deploy contract xong, khi chạy chấm điểm hàng loạt, hoặc khi người dùng hỏi "còn thiếu số liệu gì".
---

# Đo lường — ranh giới NCKH và đồ án môn học

Đây là thứ phân biệt một đề tài nghiên cứu khoa học với một bản demo chạy được. Hội đồng chấm báo cáo, không chấm số dòng code.

**Nguyên tắc: ghi ngay khi đo được, không dồn về tuần 7.** Số liệu đo lúc chạy thì mất 5 phút ghi; dựng lại môi trường để đo lại ở tuần 7 mất nửa ngày.

## Năm phép đo cần có

| # | Phép đo | Đầu ra | Làm được từ tuần |
|---|---|---|---|
| 1 | Gas theo kích thước lô Merkle (N = 10/100/1000/5000) | Đồ thị gas/bản ghi → chi phí/sinh viên/kỳ | 3 |
| 2 | Gas thu hồi: bitmap vs mapping-per-credential | Đồ thị so sánh | 4 (hoặc Hardhat local) |
| 3 | Bảng threat model, 7 dòng | Bảng đối chiếu CSDL truyền thống vs thiết kế đề xuất | 2 |
| 4 | Thời gian tổng hợp điểm: tự động (giây) vs thủ công | So sánh định lượng | 5 |
| 5 | Khảo sát SUS, 20–30 sinh viên | Điểm SUS + phân tích | 7, nếu kịp |

## Quy trình khi được gọi

1. Đọc `docs/measurements.md`, xác định phép đo nào đang có số liệu mới
2. Ghi vào đúng khung bảng đã có sẵn — **đừng đổi cấu trúc bảng**, nó được thiết kế để copy thẳng vào báo cáo
3. Mỗi số liệu phải kèm: **ngày đo**, **điều kiện đo** (testnet hay Hardhat local, cấu hình máy, cỡ mẫu), **cách tái lập**
4. Nếu là ước lượng chứ không phải đo có kiểm soát → **đánh dấu rõ ràng**
5. Nếu phát hiện một phép đo chưa có số liệu và tuần hiện tại đã qua mốc của nó → cảnh báo

## Kỷ luật trung thực

Đây là chỗ dễ mất điểm nhất khi bảo vệ, theo đúng hai chiều:

**Không thổi phồng.** Phép đo #4 lấy con số thủ công từ phỏng vấn 1–2 cán bộ CTSV — đó là bằng chứng yếu. Phải ghi rõ là ước lượng và nêu cỡ mẫu. Đừng trình bày như kết quả đo có kiểm soát. Hội đồng bắt được chỗ thổi phồng rất nhanh, và mất một chỗ là mất niềm tin vào cả bảng.

**Không giấu chỗ thua.** Dòng cuối bảng threat model — *"cán bộ nhập liệu sai từ đầu → cả hai thiết kế đều không chặn (vấn đề oracle)"* — là dòng quan trọng nhất của cả chương. Giữ nguyên. Sự trung thực này **tăng** điểm bảo vệ: nó chứng minh hiểu công cụ mình dùng thay vì tin mù quáng. Hội đồng phân biệt được hai thứ đó.

Tương tự với các hạn chế đã biết: mô hình custodial, PWA không có push thật, contract không upgradeable, camera iOS. Ghi hết vào phần hạn chế. Mỗi hạn chế được nêu chủ động là một câu hỏi khó bị vô hiệu hóa trước khi được hỏi.

## Ghi chú kỹ thuật khi đo gas

- Namespace `erigon_*` và `trace_*` đã bị bỏ trên Amoy từ 01/07/2026 — dùng `eth_getTransactionReceipt` đọc `gasUsed`
- Đo trên Hardhat local cho kết quả tái lập tốt hơn và không tốn POL từ faucet; đo trên Amoy để có tx hash thật đưa vào báo cáo. **Làm cả hai**, ghi rõ cái nào là cái nào
- Phép đo #2 hoàn toàn làm được trên Hardhat local — nếu phải cắt StatusList khỏi phạm vi deploy, vẫn giữ được số liệu này
