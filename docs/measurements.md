# Số liệu đo — chương 11 báo cáo

> **Nguyên tắc: ghi ngay khi đo được, không dồn về tuần 7.**
> Đo lúc chạy thì mất 5 phút ghi; dựng lại môi trường để đo lại ở tuần 7 mất nửa ngày.
>
> Mỗi số liệu phải kèm: ngày đo · điều kiện đo · cách tái lập.
> Nếu là **ước lượng** chứ không phải đo có kiểm soát → đánh dấu rõ `[ƯỚC LƯỢNG]`.

Cập nhật bằng `/measurements`.

---

## 11.1. Chi phí gas theo kích thước lô Merkle

**Trạng thái:** ☐ chưa đo — làm được từ tuần 3
**Cách đo:** neo N bản ghi, đọc `gasUsed` từ `eth_getTransactionReceipt`.
**Lưu ý:** namespace `erigon_*` và `trace_*` đã bị bỏ trên Amoy từ 01/07/2026 — không dùng.

| N (số leaf) | Gas tổng | Gas/bản ghi | Môi trường | Ngày đo | Tx hash |
|---|---|---|---|---|---|
| 1 (ghi từng bản) | | | | | |
| 10 | | | | | |
| 100 | | | | | |
| 1000 | | | | | |
| 5000 | | | | | |

**Quy đổi:** chi phí/sinh viên/học kỳ = ______ POL ≈ ______ VNĐ (tỷ giá ngày ____)

**Đồ thị cần vẽ:** gas/bản ghi theo N (trục N log scale).

---

## 11.2. Bảng mô hình đe dọa

**Trạng thái:** ◐ đã kiểm chứng bằng API, **chưa** kiểm chứng bằng người thật
**Nguồn dữ liệu cột 4:** hiện là test tự động qua HTTP (2026-08-05). Buổi demo điểm danh
thật với 5 người ở cuối tuần 2 sẽ thay thế/bổ sung.

| Mối đe dọa | CSDL truyền thống | Thiết kế đề xuất | Kiểm chứng |
|---|---|---|---|
| Quản trị viên sửa dữ liệu quá khứ | Không phát hiện được | Phát hiện qua neo + hash chain | ☐ tuần 4 |
| Điểm danh hộ bằng tài khoản mượn | Không chặn | Chặn bằng device binding | ✅ API |
| Chia sẻ ảnh chụp mã QR | Không chặn | Chặn bằng QR động 10s | ✅ API |
| Giả mạo chứng chỉ khi xin việc | Phải xin xác nhận từ trường | Verify độc lập, không cần trường | ☐ tuần 6 |
| Chối bỏ dữ liệu khi khiếu nại | Phụ thuộc nhật ký nội bộ | Có bằng chứng thời điểm on-chain | ☐ tuần 4 |
| Máy chủ trường ngừng hoạt động | Mất khả năng xác minh | Verifier vẫn chạy | ☐ tuần 6 |
| **Cán bộ nhập liệu sai từ đầu** | **Không chặn** | **Không chặn (vấn đề oracle)** | — |

### Kết quả kiểm chứng cơ chế điểm danh — 2026-08-05

Đo qua HTTP trên máy dev, MySQL 8.4 container, `qr-slot-seconds=10`, `tolerance=1`.

| # | Kịch bản tấn công | Kết quả | Bước chặn |
|---|---|---|---|
| 1 | Gửi ảnh chụp QR (slot cũ 5 nhịp = 50 giây) | 🛡️ chặn | Bước 1 — HMAC/slot |
| 2 | Mượn tài khoản, quét bằng máy chưa duyệt | 🛡️ chặn | Bước 2 — device binding |
| 3 | Bịa token | 🛡️ chặn | Bước 1 |
| 4 | Dùng token của sự kiện khác cùng thời điểm | 🛡️ chặn | Bước 1 — token gắn `eventId` |
| 5 | Sinh viên chưa đăng ký thiết bị nào | 🛡️ chặn | Bước 2 |
| 6 | Token + thiết bị đúng, nhưng ở cách 1.100 km | ✅ **cho qua**, `geofenceOk=false` | Bước 4 — cảnh báo mềm, đúng thiết kế |
| 7 | Đồng bộ offline: 1 bản hợp lệ + 1 bản token sai | 1 nhận / 1 từ chối | Xử lý từng bản độc lập |
| 8 | Cán bộ điểm danh tay | ✅ cho qua, `verified=false` | **Vấn đề oracle — không chặn được** |

**Kịch bản 6 và 8 là hai dòng quan trọng nhất khi trình bày.** Chúng cho thấy hệ thống
phân biệt được "chặn" và "đánh dấu để xem lại", và thừa nhận thẳng giới hạn của tầng
thu thập dữ liệu. Số bản ghi `verified=false` và `geofenceOk=false` truy được qua
`GET /api/attendance/event/{id}/stats` — đưa vào báo cáo như một chỉ số chất lượng dữ liệu.

**Unit test:** `QrTokenServiceTest`, 8/8 đạt — phủ dung sai slot, slot tương lai, secret
riêng từng sự kiện, token gắn `eventId`, cửa sổ offline 24 giờ.

> ⚠️ **Dòng cuối là dòng quan trọng nhất của cả chương — giữ nguyên, không xóa.**
> Trung thực ở đây **tăng** điểm bảo vệ: nó chứng minh hiểu công cụ mình dùng thay vì
> tin mù quáng. Hội đồng phân biệt được hai thứ đó rất nhanh.

---

## 11.3. Thời gian quy trình chấm điểm

**Trạng thái:** ☐ chưa đo — làm được từ tuần 5

| Chỉ số | Giá trị | Ghi chú |
|---|---|---|
| Số sinh viên | 500 (dữ liệu giả) | |
| Thời gian chấm tự động | ______ giây | Cấu hình máy: ______ |
| Thời gian làm thủ công | ______ giờ | `[ƯỚC LƯỢNG]` |
| Nguồn con số thủ công | Phỏng vấn ____ cán bộ CTSV | Cỡ mẫu: ____ |

> ⚠️ Con số thủ công lấy từ phỏng vấn 1–2 người là **bằng chứng yếu**. Phải ghi rõ là
> ước lượng và nêu cỡ mẫu. Đừng trình bày như kết quả đo có kiểm soát — hội đồng bắt
> được chỗ thổi phồng rất nhanh, và mất một chỗ là mất niềm tin vào cả bảng.

---

## 11.4. Chi phí thu hồi credential: bitmap vs mapping

**Trạng thái:** ☐ chưa đo — làm được từ tuần 4
**Quan trọng:** phép đo này chạy được **thuần trên Hardhat local**, không cần deploy lên
Amoy. Nếu `StatusList` bị cắt khỏi phạm vi deploy ở cổng tuần 3, **vẫn giữ được số liệu này**.

| Số credential | Gas — bitmap (StatusList) | Gas — mapping/credential | Tỷ lệ |
|---|---|---|---|
| 100 | | | |
| 1.000 | | | |
| 10.000 | | | |
| 100.000 | | | |

**Kỳ vọng:** gas bitmap **không đổi** theo quy mô (lật 1 bit trong 1 slot uint256 chứa 256
credential); gas mapping tăng tuyến tính theo số lần ghi. Đây là số liệu đo đẹp nhất của
đề tài — định lượng hoàn toàn, không cần tranh luận định tính.

---

## 11.5. Khảo sát khả dụng (SUS)

**Trạng thái:** ☐ chưa làm — tuần 7, **chỉ nếu còn thời gian**

| Chỉ số | Giá trị |
|---|---|
| Số người tham gia | ____ /20–30 |
| Điểm SUS trung bình | ____ /100 |
| Độ lệch chuẩn | ____ |
| Ngày khảo sát | ____ |

Diễn giải chuẩn: >68 là trên trung bình, >80 là tốt.

---

## Nhật ký đo

| Ngày | Phép đo | Kết quả tóm tắt | Người đo |
|---|---|---|---|
| | | | |
