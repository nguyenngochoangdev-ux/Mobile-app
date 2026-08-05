# Số liệu đo — chương 11 báo cáo

> **Nguyên tắc: ghi ngay khi đo được, không dồn về tuần 7.**
> Đo lúc chạy thì mất 5 phút ghi; dựng lại môi trường để đo lại ở tuần 7 mất nửa ngày.
>
> Mỗi số liệu phải kèm: ngày đo · điều kiện đo · cách tái lập.
> Nếu là **ước lượng** chứ không phải đo có kiểm soát → đánh dấu rõ `[ƯỚC LƯỢNG]`.

Cập nhật bằng `/measurements`.

---

## 11.1. Chi phí gas theo kích thước lô Merkle

**Trạng thái:** ◐ đã đo **trên Hardhat local**, chưa đo trên Amoy (chưa deploy — thiếu `AMOY_RPC_URL`)
**Cách đo:** neo N bản ghi, đọc `gasUsed` từ `eth_getTransactionReceipt`.
**Lưu ý:** namespace `erigon_*` và `trace_*` đã bị bỏ trên Amoy từ 01/07/2026 — không dùng.

**Tái lập:** `cd contracts && npm run gas` (`scripts/measure-gas.ts`).
**Điều kiện:** Hardhat 3.12 EDR mô phỏng · solc 0.8.28, optimizer bật, runs = 200 ·
evmVersion `cancun` · Windows 10, Node 24.18. Gas là đại lượng của EVM nên con số này khớp
với Amoy; **chi phí POL thì không** — nó còn phụ thuộc giá gas lúc gửi.

| N (số leaf) | Gas tổng | Gas/bản ghi | Môi trường | Ngày đo | Tx hash |
|---|---|---|---|---|---|
| 1 (ghi từng bản) | 54.752 | 54.752 | Hardhat local | 2026-08-05 | — chưa deploy |
| 10 | 54.752 | 5.475,2 | Hardhat local | 2026-08-05 | — chưa deploy |
| 100 | 54.752 | 547,5 | Hardhat local | 2026-08-05 | — chưa deploy |
| 1000 | 54.752 | 54,8 | Hardhat local | 2026-08-05 | — chưa deploy |
| 5000 | 54.752 | 11,0 | Hardhat local | 2026-08-05 | — chưa deploy |

**Cột "Gas tổng" giống nhau ở mọi dòng, và đó chính là kết quả.** Cây Merkle dựng off-chain;
on-chain chỉ nhận đúng 32 byte root, nên chi phí neo **không phụ thuộc số bản ghi trong lô**.
Dòng N = 1 là đối chứng: nếu ghi từng bản ghi lên chuỗi thay vì gộp lô thì mỗi bản ghi phải
trả trọn một giao dịch. Gộp lô 5.000 làm chi phí mỗi bản ghi giảm **4.977 lần**.

**Hai con số phụ, cùng lần đo:**

| | Gas |
|---|---:|
| Neo lô **đầu tiên** của một miền | 71.852 |
| Neo ở trạng thái ổn định | 54.752 |

Lô đầu tiên đắt hơn vì nó khởi tạo ô đếm `_batchCount` (0 → khác 0). Chuyện này xảy ra đúng
5 lần trong cả đời hệ thống (5 miền neo), nên số dùng cho mọi tính toán là 54.752.

**Gas triển khai — đo cả hai môi trường, 2026-08-05.** Deploy thật lên Amoy lúc
09:41 UTC, ví `0xf32728c5c2D0575ea406Ad37e2467916c89F529F`, gasPrice 30 gwei.

| Contract | Gas (Hardhat local) | **Gas (Amoy thật)** | Địa chỉ | Tx |
|---|---:|---:|---|---|
| `AnchorRegistry` | 513.049 | **519.769** | [`0x4aC296…3fAF`](https://amoy.polygonscan.com/address/0x4aC296Ad010233799bA3B91b8505269213503fAF) | [`0x3d66d9…09c1`](https://amoy.polygonscan.com/tx/0x3d66d99ac04b9065f854931b8a9d53640d9ec0482a7a2b8e04767ec3d67409c1) |
| `IssuerRegistry` | 810.719 | **817.439** | [`0xD32311…5637`](https://amoy.polygonscan.com/address/0xD323118Fa310a730BC4202fADd8dfA7CeA4C5637) | [`0xca68b5…b856`](https://amoy.polygonscan.com/tx/0xca68b5f07775d069000aed1e08ae450276c01dbcb12acc384f7ba3f89da2b856) |
| `StatusList` | 493.647 | **500.367** | [`0xc8538A…2106`](https://amoy.polygonscan.com/address/0xc8538A8741CE428C4A26f3a06678b6Ca10972106) | [`0xb712f2…9790`](https://amoy.polygonscan.com/tx/0xb712f2d00cbda1d174e6adaf8057d75d919dba7104ab05aeec12dde529bb9790) |
| `StatusListMapping` *(đối chứng, không deploy)* | 474.362 | — | — | — |

Tổng deploy thật: **1.837.575 gas ≈ 0,0551 POL**. Cả ba đã verify trên **PolygonScan**
(Etherscan API V2, `chainid=80002`) **và Sourcify** — mã nguồn đọc được công khai, không
phụ thuộc máy chủ của trường. Bản ghi đầy đủ: `contracts/deployments/amoy.json`.

> **Một quan sát cần nêu đúng mức trong báo cáo:** Amoy tốn hơn Hardhat local **đúng 6.720
> gas ở cả ba contract** — một hằng số, không tỷ lệ với kích thước bytecode. Chưa truy được
> nguyên nhân, nên **đừng giải thích bừa** nếu bị hỏi. Điều nói được chắc chắn: sai lệch là
> hằng số và nhỏ (~1,3%), theo chiều **local ước lượng THẤP hơn thật** — tức là mọi con số
> local trong tài liệu này là cận dưới, không phải con số bị thổi phồng.

**Quy đổi.** Neo hằng đêm × 5 miền × 16 tuần ≈ 560 giao dịch/học kỳ × 54.752 gas
≈ **30,7 triệu gas/học kỳ cho toàn trường**. Ở 30 gwei ≈ **0,92 POL/học kỳ**; với 500 sinh
viên là ≈ **0,0018 POL/sinh viên/học kỳ**.

> ⚠️ Con số POL là `[ƯỚC LƯỢNG]` ở một mức giá gas giả định, không phải số đo. Quy đổi ra
> VNĐ cần tỷ giá POL **tại ngày viết báo cáo** — đừng điền sẵn bây giờ, giá sẽ lệch.
> Và nói rõ khi bảo vệ: đề tài chạy trên **testnet**, nơi POL lấy từ faucet và không có
> giá trị tiền tệ. Đây là ước tính chi phí *nếu* triển khai trên mainnet.

**Đồ thị cần vẽ:** gas/bản ghi theo N (trục N log scale).

---

## 11.2. Bảng mô hình đe dọa

**Trạng thái:** ◐ đã kiểm chứng bằng API, **chưa** kiểm chứng bằng người thật
**Nguồn dữ liệu cột 4:** hiện là test tự động qua HTTP (2026-08-05). Buổi demo điểm danh
thật với 5 người ở cuối tuần 2 sẽ thay thế/bổ sung.

Cột "Thiết kế đề xuất" dùng **ba mức**, không dùng nhị phân chặn/không chặn:

- **Ngăn** — kẻ tấn công không thực hiện được, kể cả khi cố tình.
- **Tăng chi phí** — vẫn làm được, nhưng đòi hỏi thêm điều kiện, thêm người, hoặc để lại dấu vết.
- **Phát hiện** — không ngăn được, nhưng chứng minh được sau đó.

| Mối đe dọa | CSDL truyền thống | Thiết kế đề xuất | Mức | Kiểm chứng |
|---|---|---|---|---|
| Quản trị viên sửa dữ liệu quá khứ | Không phát hiện được | Neo Merkle + hash chain nhật ký | Phát hiện | ☐ tuần 4 |
| Chia sẻ **ảnh chụp** mã QR (gửi sau) | Không chặn | QR đổi mỗi 10s, dung sai 1 nhịp | Ngăn | ✅ API |
| **Chuyển tiếp QR thời gian thực** (chụp và gửi ngay trong 20s) | Không chặn | Không ngăn được. Cần thêm đồng phạm có mặt tại chỗ | Tăng chi phí | ☐ chưa đo |
| Mượn **tài khoản** (đưa mật khẩu, quét bằng máy khác) | Không chặn | Thiết bị chưa duyệt bị từ chối | Ngăn | ✅ API |
| Đưa **chính điện thoại của mình** cho bạn quét hộ | Không chặn | **Không ngăn được** — thiết bị hợp lệ, tài khoản hợp lệ | Không | ☐ chưa đo |
| Sao chép `deviceFp` sang máy khác | Không chặn | **Không ngăn được** — chỉ là UUID trong `localStorage` | Không | ☐ chưa đo |
| Đổi thiết bị để quét hộ lâu dài | Không chặn | Phải qua cán bộ duyệt, có nhật ký, thiết bị cũ bị thu hồi | Tăng chi phí | ✅ API |
| Giả mạo chứng chỉ khi xin việc | Phải xin xác nhận từ trường | Verify độc lập, không cần trường | Ngăn | ☐ tuần 6 |
| Chối bỏ dữ liệu khi khiếu nại | Phụ thuộc nhật ký nội bộ | Có bằng chứng thời điểm on-chain | Phát hiện | ☐ tuần 4 |
| Máy chủ trường ngừng hoạt động | Mất khả năng xác minh | Verifier vẫn chạy | Ngăn | ☐ tuần 6 |
| **Cán bộ nhập liệu sai từ đầu** | **Không chặn** | **Không chặn (vấn đề oracle)** | **Không** | — |

### Phát biểu đúng mức về device binding

Bản trước của bảng này ghi *"Điểm danh hộ bằng tài khoản mượn → Chặn bằng device
binding"*. **Phát biểu đó quá mạnh và phải sửa.** Ba lý do:

1. **Đưa thẳng điện thoại của mình cho bạn** là hình thức điểm danh hộ tự nhiên nhất, và
   device binding không đụng được tới nó: thiết bị đã duyệt, tài khoản đã đăng nhập, mọi
   bước kiểm tra đều qua. Đây là kịch bản tấn công có xác suất cao nhất trong thực tế
   và cũng là kịch bản hệ thống bất lực nhất.
2. **`deviceFp` chỉ là một UUID ngẫu nhiên lưu `localStorage`** (`app/src/lib/device.ts`).
   Xoá dữ liệu trình duyệt là mất; đọc và sao chép sang máy khác thì bỏ qua được toàn bộ
   bước 2. Fingerprint canvas/WebGL ổn định hơn chút nhưng vẫn giả mạo được, và đang bị
   các trình duyệt chống fingerprinting chặn dần.
3. **Thiết bị đầu tiên được duyệt tự động** (`drl.attendance.auto-approve-first-device`).
   Ai chiếm được tài khoản của một sinh viên chưa từng đăng nhập sẽ đăng ký thiết bị đầu
   tiên mà không cần cán bộ duyệt.

**Phát biểu đúng:** device binding **ngăn** được trường hợp chia sẻ mật khẩu thuần tuý —
kiểu gian lận rẻ nhất và phổ biến nhất — và **tăng chi phí** của việc quét hộ lâu dài,
vì đổi thiết bị phải qua cán bộ duyệt, có nhật ký, và thiết bị cũ bị thu hồi ngay. Nó
**không** biến điểm danh hộ thành bất khả thi.

Giải pháp đúng cho lớp tấn công này là chứng thực nền tảng (Play Integrity, App Attest)
hoặc yếu tố sinh trắc tại thời điểm quét — cả hai đều đòi app native, nằm ngoài phạm vi
PWA của đề tài. **Ghi vào phần hạn chế và hướng phát triển.**

### Ba dòng cần chuẩn bị trả lời khi bảo vệ

Hội đồng thường hỏi đúng vào chỗ yếu. Chuẩn bị sẵn:

| Câu hỏi nhiều khả năng bị hỏi | Trả lời |
|---|---|
| "Sinh viên đưa máy cho bạn thì sao?" | Không ngăn được, và em ghi rõ trong bảng. Cần chứng thực nền tảng hoặc sinh trắc — đòi app native. |
| "Sao chép được cái `deviceFp` đó không?" | Được. Nó là UUID trong localStorage. Đây là giới hạn của PWA, không phải của thiết kế. |
| "Vậy blockchain giải quyết được gì ở đây?" | Không gì cả ở tầng này. Blockchain bảo vệ dữ liệu **sau khi** đã ghi. Chất lượng dữ liệu lúc thu thập là bài toán khác — đó chính là vấn đề oracle ở dòng cuối bảng. |

### Kết quả kiểm chứng cơ chế điểm danh — 2026-08-05

Đo qua HTTP trên máy dev, MySQL 8.4 container, `qr-slot-seconds=10`, `tolerance=1`.

| # | Kịch bản tấn công | Kết quả | Bước chặn |
|---|---|---|---|
| 1 | Gửi ảnh chụp QR (slot cũ 5 nhịp = 50 giây) | 🛡️ chặn | Bước 1 — HMAC/slot |
| 2 | Mượn tài khoản, quét bằng máy **chưa duyệt** | 🛡️ chặn | Bước 2 — device binding |
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

**Phạm vi của kịch bản 2 — đừng nói quá.** Nó chỉ chứng minh: mượn tài khoản rồi quét
bằng **máy chưa được duyệt** thì bị từ chối. Nó **không** chứng minh hệ thống chống được
điểm danh hộ nói chung. Ba kịch bản dưới đây chưa test và **dự kiến sẽ qua được**:

| # | Kịch bản chưa test | Dự kiến | Vì sao |
|---|---|---|---|
| 9 | Đưa chính điện thoại đã đăng nhập cho bạn quét | ❌ qua được | Thiết bị và tài khoản đều hợp lệ |
| 10 | Sao chép `localStorage['drl.deviceFp']` sang máy khác | ❌ qua được | `deviceFp` không gắn với phần cứng |
| 11 | Chuyển tiếp ảnh QR trong vòng 20 giây | ❌ qua được | Còn trong dung sai slot |

**Nên test cả ba ở buổi demo cuối tuần 2 và ghi kết quả thật.** Một bảng threat model
có ô "qua được" đáng tin hơn nhiều so với bảng toàn ô "chặn" — hội đồng biết không có hệ
thống nào chặn được tất cả, và bảng toàn màu xanh sẽ bị nghi ngờ ngay.

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

**Trạng thái:** ✅ **đã đo** trên Hardhat local — 2026-08-05
**Quan trọng:** phép đo này chạy được **thuần trên Hardhat local**, không cần deploy lên
Amoy. Nếu `StatusList` bị cắt khỏi phạm vi deploy ở cổng tuần 3, **vẫn giữ được số liệu này**.

**Tái lập:** `cd contracts && npm run gas`. Cùng điều kiện đo với §11.1.

**Tính công bằng của phép so sánh.** `StatusList` (bitmap) và `bench/StatusListMapping`
cùng hiện thực `IStatusList`, cùng `AccessControl`, cùng `onlyRole`, cùng sự kiện, cùng lối
tắt "trạng thái không đổi thì không ghi" — khác đúng một thứ là cách lưu trữ. Cả hai chạy
**chung một bộ test** (`contracts/test/StatusList.test.ts`, 13 test × 2). Nếu bỏ
`AccessControl` ở bản đối chứng cho gọn thì chênh lệch đo được sẽ lẫn chi phí kiểm tra
quyền, và con số đưa vào báo cáo là con số sai.

| Số credential | Gas — bitmap **gom cụm** | Gas — bitmap **rải đều** | Gas — mapping/credential | Tỷ lệ (rải đều) |
|---|---|---|---|---|
| 1 | 48.487 | 48.487 | 48.533 | 1,00× |
| 100 | 263.317 | 2.431.417 | 2.436.017 | 1,00× |
| 1.000 | 2.290.921 | *không vừa 1 giao dịch* | *không vừa 1 giao dịch* | — |
| 10.000 | *không vừa 1 giao dịch* | *không vừa 1 giao dịch* | *không vừa 1 giao dịch* | — |
| 100.000 | *không vừa 1 giao dịch* | *không vừa 1 giao dịch* | *không vừa 1 giao dịch* | — |

**Hai ô trống cuối bảng cũng là số liệu, không phải chỗ chưa làm.** Một giao dịch không
vượt được giới hạn gas của block (~30 triệu trên Amoy), nên thu hồi 10.000–100.000
credential **trong một giao dịch là bất khả thi với mọi cách lưu trữ**. Vì vậy con số dùng
cho báo cáo là **gas biên trên mỗi credential**:

| Cách lưu trữ | Gas biên/credential | Trần lý thuyết 1 giao dịch |
|---|---:|---:|
| Bitmap, chỉ số **gom cụm** | 2.633 | ~11.393 credential |
| Bitmap, chỉ số **rải đều** *(trường hợp thật)* | 24.314 | ~1.233 credential |
| Mapping-per-credential | 24.360 | ~1.231 credential |

### ⚠️ Kỳ vọng ban đầu đã SAI — và đây mới là kết quả đáng viết

Bản trước của mục này viết: *"gas bitmap **không đổi** theo quy mô (lật 1 bit trong 1 slot
uint256 chứa 256 credential)"*. **Đo xong thì thấy phát biểu đó chỉ đúng trong một điều kiện
mà đề tài đã cố ý từ bỏ.**

Bitmap chỉ rẻ khi nhiều chỉ số bị thu hồi **nằm cùng một word 256 bit**: lần ghi đầu vào
word trả giá slot 0 → khác 0 (đắt), các lần sau chỉ sửa slot đã khác 0 (rẻ hơn ~9 lần). Nếu
các chỉ số **rải đều**, mỗi lần thu hồi lại chạm một slot mới — và bitmap tụt xuống đúng
bằng mapping: **24.314 so với 24.360 gas, tức là 1,00×, không lợi gì cả.**

Mà `PROJECT.md` §2.3 bắt cấp `status_list_index` **ngẫu nhiên từ pool còn trống**, chính là
để sự kiện `StatusChanged(index)` không lộ thứ tự cấp phát và không tương quan được với danh
sách sinh viên. Cấp ngẫu nhiên ⇒ chỉ số rải đều ⇒ **đề tài đã cố ý chọn đúng trường hợp xấu
nhất của bitmap.**

**Kết luận đúng để viết vào báo cáo** không phải "bitmap rẻ hơn mapping", mà là:

> Lợi thế gas của bitmap phụ thuộc hoàn toàn vào cách cấp phát chỉ số. Đo được **8,47×** khi
> chỉ số gom cụm và **1,00×** khi rải đều. Thiết kế của đề tài chọn cấp ngẫu nhiên vì lý do
> quyền riêng tư, nên trên thực tế **từ bỏ gần như toàn bộ lợi thế gas của bitmap**. Đây là
> một đánh đổi định lượng được giữa quyền riêng tư và chi phí, không phải một lựa chọn hiển
> nhiên.

**Chỗ đánh đổi này được quyết ở đâu trong mã.** `drl.credential.status-list-pool-size`
(mặc định `2^20`) chính là cái núm: pool lớn ⇒ chỉ số rải đều ⇒ dòng "rải đều" của bảng trên;
pool nhỏ ⇒ gom cụm hơn ⇒ nhích về phía dòng "gom cụm", đổi lại bốc trùng nhiều hơn và có ngày
hết chỗ. Thuật toán cấp phát và lý do chọn mặc định: `docs/canonicalization.md` §10.4.

Chuẩn bị sẵn cho câu hỏi *"vậy sao không dùng mapping cho đơn giản?"*: vì lợi thế thật của
bitmap nằm ở **phía đọc**, chỗ không tốn gas nên không xuất hiện trong bảng trên.
`getWord(w)` trả 256 trạng thái trong **một** `eth_call`; mapping cần **256** lần gọi
`isRevoked`. Verifier là trang tĩnh chạy trên RPC công cộng không key, nơi mỗi vòng gọi mạng
là một khoản phải trả — nên bitmap vẫn là lựa chọn đúng, chỉ là **đúng vì lý do khác với lý
do ban đầu tưởng**.

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
| 2026-08-05 | #1 — gas neo theo kích thước lô (Hardhat local) | Neo 54.752 gas **bất kể N**. Gộp lô 5.000 giảm chi phí/bản ghi 4.977 lần. Còn nợ số đo thật trên Amoy | Hoàng |
| 2026-08-05 | #2 — thu hồi bitmap vs mapping (Hardhat local) | Bitmap rẻ hơn **8,47×** khi chỉ số gom cụm nhưng **1,00×** khi rải đều. Kỳ vọng ban đầu sai; đề tài cấp chỉ số ngẫu nhiên nên rơi vào trường hợp xấu nhất | Hoàng |
| 2026-08-05 | Deploy 3 contract lên Amoy + verify | 1.837.575 gas ≈ 0,0551 POL. Verify được trên cả PolygonScan lẫn Sourcify. Amoy tốn hơn local hằng số 6.720 gas/contract | Hoàng |
