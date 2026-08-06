# Số liệu đo — chương 11 báo cáo

> **Nguyên tắc: ghi ngay khi đo được, không dồn về tuần 7.**
> Đo lúc chạy thì mất 5 phút ghi; dựng lại môi trường để đo lại ở tuần 7 mất nửa ngày.
>
> Mỗi số liệu phải kèm: ngày đo · điều kiện đo · cách tái lập.
> Nếu là **ước lượng** chứ không phải đo có kiểm soát → đánh dấu rõ `[ƯỚC LƯỢNG]`.

Cập nhật bằng `/measurements`.

---

## 11.1. Chi phí gas theo kích thước lô Merkle

**Trạng thái:** ✅ **đã đo cả trên Amoy lẫn Hardhat local** — 2026-08-06
**Cách đo:** neo N bản ghi, đọc `gasUsed` từ `eth_getTransactionReceipt`.
**Lưu ý:** namespace `erigon_*` và `trace_*` đã bị bỏ trên Amoy từ 01/07/2026 — không dùng.

**Tái lập:** `cd contracts && npm run gas` (`scripts/measure-gas.ts`).
**Điều kiện:** Hardhat 3.12 EDR mô phỏng · solc 0.8.28, optimizer bật, runs = 200 ·
evmVersion `cancun` · Windows 10, Node 24.18. Gas là đại lượng của EVM nên con số này khớp
với Amoy; **chi phí POL thì không** — nó còn phụ thuộc giá gas lúc gửi.

| N (số leaf) | Gas tổng | Gas/bản ghi | Môi trường | Ngày đo | Tx hash |
|---|---|---|---|---|---|
| **4 — `ATTEND` lô đầu của miền** | **81.968** | **20.492** | **Amoy** | **2026-08-06** | [`0x1d1ebe…db75`](https://amoy.polygonscan.com/tx/0x1d1ebe0d84320b669fe15243eee4a6a6d58b736cdd204db19ccbc08fa747db75) |
| **1 — `CRED` lô đầu của miền** | **81.944** | **81.944** | **Amoy** | **2026-08-06** | [`0x0cbaca…e4d6`](https://amoy.polygonscan.com/tx/0x0cbacae962f23e9c56cc8f87a2d46e7f358bcc1ec3c8168aa4aaff032190e4d6) |
| **5 — `AUDIT` lô đầu của miền** | **81.956** | **16.391** | **Amoy** | **2026-08-06** | [`0xe965fb…0a42`](https://amoy.polygonscan.com/tx/0xe965fb7c0e1e5f8de8f329493ceefefa67c1b6970643c51faf0fbfa0878c0a42) |
| **2 — `ATTEND` lô THỨ HAI** | **64.028** | **32.014** | **Amoy** | **2026-08-06** | [`0x9c63ad…ceb1`](https://amoy.polygonscan.com/tx/0x9c63ad9425347b765a10af88acd30664c71725fc7e5fe4a4f072ec44751dceb1) |
| **1 — `CRED` lô THỨ HAI** | **64.004** | **64.004** | **Amoy** | **2026-08-06** | [`0xd3a630…4b2d`](https://amoy.polygonscan.com/tx/0xd3a630c48f5df9415ba30320c57672ab3c04fce2bab3de0fc3ab62d88b644b2d) |
| 1 (ghi từng bản) | 54.752 | 54.752 | Hardhat local | 2026-08-05 | — |
| 10 | 54.752 | 5.475,2 | Hardhat local | 2026-08-05 | — |
| 100 | 54.752 | 547,5 | Hardhat local | 2026-08-05 | — |
| 1000 | 54.752 | 54,8 | Hardhat local | 2026-08-05 | — |
| 5000 | 54.752 | 11,0 | Hardhat local | 2026-08-05 | — |

> **Ba dòng đầu đắt hơn hai dòng sau — giải thích trước khi bị hỏi.** Chúng là lô **đầu tiên
> của từng miền**, nên trả thêm chi phí khởi tạo ô đếm `_batchCount` (0 → khác 0):
> **17.940 gas**, đo được bằng hiệu 81.968 − 64.028 (`ATTEND`) và 81.944 − 64.004 (`CRED`) —
> hai miền độc lập cho cùng một con số.
>
> Chuyện khởi tạo xảy ra **đúng 5 lần trong cả đời hệ thống**, một lần mỗi miền. Con số dùng
> cho mọi tính toán quy mô là **~64.000**, không phải ~82.000.

**Cột "Gas tổng" giống nhau ở mọi dòng, và đó chính là kết quả.** Cây Merkle dựng off-chain;
on-chain chỉ nhận đúng 32 byte root, nên chi phí neo **không phụ thuộc số bản ghi trong lô**.
Dòng N = 1 là đối chứng: nếu ghi từng bản ghi lên chuỗi thay vì gộp lô thì mỗi bản ghi phải
trả trọn một giao dịch. Gộp lô 5.000 làm chi phí mỗi bản ghi giảm **4.977 lần**.

**Ba con số của cùng một phép gọi `anchor()`, đo ở ba nơi:**

| Môi trường | Lô đầu của miền | Trạng thái ổn định |
|---|---:|---:|
| Hardhat EDR (`npm run gas`) | 71.852 | 54.752 |
| Hardhat node cục bộ, gọi qua web3j | 71.888 | 54.788 |
| **Amoy thật** | **81.944 – 81.968** | **64.004 – 64.028** ✅ *(đo 2026-08-06)* |

**Chi phí khởi tạo miền trên Amoy: 17.940 gas**, trả **đúng một lần cho mỗi miền** (ô đếm
`_batchCount` đi từ 0 sang khác 0). Con số dùng cho mọi tính toán quy mô là **~64.000**, không
phải ~82.000.

### Một dự đoán nhỏ, kiểm được, và đã đúng ở cả 5 phép đo

Năm giao dịch `anchor()` trên Amoy lệch nhau **đúng bội số của 12 gas**, và bội số đó bằng
**hiệu số ký tự trong tên miền**:

| Miền | Số ký tự | Lô đầu | Lô ổn định |
|---|---:|---:|---:|
| `CRED` | 4 | 81.944 | 64.004 |
| `AUDIT` | 5 | 81.956 | — |
| `ATTEND` | 6 | 81.968 | 64.028 |
| | | **+12/ký tự** | **+12/ký tự** |

Vì sao: tham số là `bytes8`, **đệm `0x00` bên phải**. Trong calldata, byte `0x00` tốn 4 gas
còn byte khác 0 tốn 16 — chênh **12 gas mỗi ký tự**. Tên miền dài thêm một ký tự là bớt một
byte `0x00`.

**Giá trị của mục này với báo cáo không nằm ở 12 gas** — nó nhỏ đến mức vô nghĩa về chi phí.
Giá trị nằm ở chỗ nó là một **dự đoán định lượng từ lý thuyết EVM, kiểm được bằng số đo thật,
và đúng chính xác ở cả năm điểm**. Nó cho thấy các con số trong chương này đến từ đo đạc có
hiểu biết chứ không phải chép lại từ explorer.

> **Chênh lệch Amoy ↔ Hardhat local: ~9.250 gas ở trạng thái ổn định** (64.028 vs 54.788),
> so với **6.720 gas** ở phần deploy. Hai con số khác nhau nên **không có một hằng số chung**,
> và **đừng giải thích bừa** nếu bị hỏi. Phép so này cũng không phải so có kiểm soát: hai bên
> khác cả miền lẫn `batchId`, mà cả hai đều đổi số byte `0x00` trong calldata.
> Điều nói được chắc chắn: **Amoy luôn cao hơn local**, nên mọi con số local trong tài liệu
> này là **cận dưới**.

Hai dòng Hardhat lệch nhau 36 gas do độ dài calldata của `batchId` khác nhau; điều đó xác nhận
**đường đi qua web3j không thêm chi phí nào**.

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

**Quy đổi — dùng số đo THẬT trên Amoy, không dùng số local.** Neo hằng đêm × 5 miền × 16 tuần
≈ 560 giao dịch/học kỳ × **64.000 gas** ≈ **35,8 triệu gas/học kỳ cho toàn trường**.
Ở 30 gwei ≈ **1,08 POL/học kỳ**; với 500 sinh viên là ≈ **0,0021 POL/sinh viên/học kỳ**.

> Bản trước quy đổi bằng 54.752 (Hardhat local) và ra 0,92 POL. Đã thay bằng số Amoy —
> chênh **17%**. Dùng số local cho một con số trình bày là hạ thấp chi phí thật, đúng loại
> chỗ hội đồng bắt được.

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
| Quản trị viên sửa dữ liệu quá khứ **(sửa vụng, không tính lại chuỗi)** | Không phát hiện được | Hash chain nhật ký | Phát hiện | ✅ test SQL thật |
| Quản trị viên sửa quá khứ **rồi TÍNH LẠI CẢ CHUỖI** | Không phát hiện được | Hash chain **không** bắt được; chỉ root đã neo mới bắt | Phát hiện **chỉ với khoảng đã neo** | ✅ **lô đã neo** |
| Chia sẻ **ảnh chụp** mã QR (gửi sau) | Không chặn | QR đổi mỗi 10s, dung sai 1 nhịp | Ngăn | ✅ API |
| **Chuyển tiếp QR thời gian thực** (chụp và gửi ngay trong 20s) | Không chặn | Không ngăn được. Cần thêm đồng phạm có mặt tại chỗ | Tăng chi phí | ☐ chưa đo |
| Mượn **tài khoản** (đưa mật khẩu, quét bằng máy khác) | Không chặn | Thiết bị chưa duyệt bị từ chối | Ngăn | ✅ API |
| Đưa **chính điện thoại của mình** cho bạn quét hộ | Không chặn | **Không ngăn được** — thiết bị hợp lệ, tài khoản hợp lệ | Không | ☐ chưa đo |
| Sao chép `deviceFp` sang máy khác | Không chặn | **Không ngăn được** — chỉ là UUID trong `localStorage` | Không | ☐ chưa đo |
| Đổi thiết bị để quét hộ lâu dài | Không chặn | Phải qua cán bộ duyệt, có nhật ký, thiết bị cũ bị thu hồi | Tăng chi phí | ✅ API |
| **Đưa ảnh chụp QR của bạn cho cán bộ quét hộ** (luồng đảo chiều) | Không chặn | **Không ngăn được** — mã đúng, chữ ký đúng. Chỉ mắt cán bộ chặn được | Tăng chi phí | ✅ API |
| **Sửa `studentId` trong mã QR của mình để mạo danh** | Không chặn | Chữ ký hỏng ngay, bị từ chối | Ngăn | ✅ API |
| Giả mạo chứng chỉ khi xin việc | Phải xin xác nhận từ trường | Verify độc lập, không cần trường | Ngăn | ✅ **bundle thật** |
| Chối bỏ dữ liệu khi khiếu nại | Phụ thuộc nhật ký nội bộ | Nhật ký có chuỗi băm + neo định kỳ | Phát hiện | ✅ **lô đã neo** |
| Máy chủ trường ngừng hoạt động | Mất khả năng xác minh | Verifier vẫn chạy | Ngăn | ✅ **bundle thật** |
| **Sửa nội dung credential trong tệp bundle** | Không áp dụng | Ba lớp độc lập cùng bắt: leaf · chữ ký · Merkle proof | Ngăn | ✅ **bundle thật** |
| **Trỏ bundle sang contract giả của kẻ tấn công** | Không áp dụng | Verifier dùng địa chỉ tin cậy trong mã nguồn của chính nó, không lấy từ bundle | Ngăn | ✅ test |
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

### Hai dòng đầu bảng là chỗ trung thực nhất của cả chương — giữ nguyên

Bản trước gộp chúng thành một dòng *"Quản trị viên sửa dữ liệu quá khứ → Neo Merkle + hash
chain → Phát hiện"*. **Phát biểu đó quá mạnh.** Tách ra vì hai kịch bản khác nhau hoàn toàn:

- **Sửa vụng** (đổi một dòng bằng SQL, quên tính lại mắt xích) → chuỗi băm bắt ngay. Đã kiểm
  bằng test sửa/xóa/chèn thẳng bằng SQL trên MySQL thật (`AuditServiceDbTest`).
- **Sửa rồi tính lại cả chuỗi** → chuỗi băm **không** bắt được, và có một test
  (`tinhLaiCaChuoiThiKhongBat`) **cố tình chứng minh điều đó**. Thứ duy nhất chặn được là root
  đã nằm trên chuỗi công khai.

Phát biểu đúng mức: **chuỗi băm làm việc sửa hồi tố trở nên tốn kém; việc neo làm nó bất khả
thi đối với khoảng thời gian đã neo.** Cửa sổ còn giấu được chính là khoảng cách giữa hai lần
neo — hiện là 24 giờ (job 02:00). Chi tiết: `docs/canonicalization.md` §14.2.

**Cập nhật 2026-08-06:** lô `AUDIT` `2026080601` **đã neo trên Amoy**, 5 mắt xích thật, 5/5
proof xác minh được từ RPC công cộng không key — xem §11.8. Hai dòng trên chuyển sang ✅.

Nhưng ✅ đó chỉ áp dụng cho **khoảng thời gian đã neo**. Cửa sổ giữa hai lần neo (hiện 24 giờ)
vẫn giấu được, và đó là điều phải nói khi bảo vệ chứ không phải giấu sau một dấu tích xanh.

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

### Luồng đảo chiều — kết quả kiểm chứng 2026-08-06

Sinh viên hiển thị mã, cán bộ quét (`PROJECT.md` §2.4 phương án 3). Đo qua HTTP trên máy dev.

| # | Kịch bản | Kết quả | Ghi chú |
|---|---|---|---|
| 1 | Mã còn tươi, sinh viên đã đăng ký | ✅ nhận, `verified=true` | `method=QR_SHOW` |
| 2 | Toạ độ máy cán bộ ngoài khu vực | ✅ nhận, `geofenceOk=false` | cảnh báo mềm, đúng thiết kế |
| 3 | Mã không phải của hệ thống (link, mã vạch) | 🛡️ từ chối | không ném lỗi, báo rõ ràng |
| 4 | Sai tiền tố `DRL1` | 🛡️ từ chối | |
| 5 | Token bịa | 🛡️ từ chối | chữ ký sai |
| 6 | **Sửa `studentId` trong mã để mạo danh** | 🛡️ từ chối | chữ ký gắn với `studentId` |
| 7 | Chưa đăng ký sự kiện có giới hạn | 🛡️ từ chối | giữ nguyên bước kiểm tra của luồng xuôi |
| 8 | Đã điểm danh rồi | 🛡️ từ chối | |
| 9 | Sinh viên gọi endpoint của cán bộ | 🛡️ HTTP 403 | |
| 10 | Cán bộ gọi endpoint `my-qr` | 🛡️ HTTP 403 | |
| 11 | **Đưa ảnh chụp mã của bạn cho cán bộ quét** | ❌ **qua được** | chỉ mắt cán bộ chặn được |
| 12 | Mã cũ quá cửa sổ offline | 🛡️ từ chối | `StudentQrServiceTest` |
| 13 | Mã cũ trong cửa sổ offline | ✅ nhận, **`verified=false`** | `StudentQrServiceTest` |
| 14 | Slot tương lai | 🛡️ từ chối | chặn sinh trước token hàng loạt |

**Dòng 11 và 13 là hai dòng quan trọng nhất.**

Dòng 11: luồng đảo chiều **không** chứng minh sự có mặt. Nó chỉ bảo đảm mã không giả được —
cán bộ không gõ nhầm MSSV và không bị đưa một mã bịa. Người cầm ảnh chụp mã của bạn mình vẫn
qua. Đây là mô hình tin cậy **giống kiểm tra thẻ sinh viên**, và phải nói đúng như vậy.

Dòng 13: mã cũ **vẫn được nhận** — vì đó chính là tình huống luồng này sinh ra để cứu (hội
trường mất sóng, máy sinh viên không xin được mã mới). Nhưng nó bị đánh dấu `verified=false`,
nên vẫn đếm riêng được. Nếu gộp hai mức này thành "hợp lệ / không hợp lệ" thì chỉ số chất
lượng dữ liệu mất hết ý nghĩa.

**Unit test:** `StudentQrServiceTest`, 18/18 đạt — phủ ba mức tươi, biên cửa sổ offline, tách
khóa theo mục đích, và việc xoay khóa JWT làm vô hiệu mọi mã cũ.

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

### Thu hồi TỪNG credential một — phép đo bổ sung, 2026-08-06

Bảng trên đo **gas biên trong cùng một giao dịch** (`setRevokedBatch`). Luồng vận hành thật
thu hồi **từng credential một**, mỗi lần một giao dịch riêng, nên nó gánh thêm 21.000 gas phí
giao dịch. Đo qua `StatusListClient` trên **Hardhat node cục bộ** (`CredentialRevocationDbTest`
+ `StatusListClientLocalChainTest`):

| Trường hợp | Gas | Ghi chú |
|---|---:|---|
| Thu hồi, **ô lưu trữ mới** (word chưa ai chạm) | **47.978** | 0 → khác 0: SSTORE 20.000 |
| Thu hồi, **word đã có bit khác bật** | **30.878** | khác 0 → khác 0: SSTORE 2.900 |
| Gọi lại khi **trạng thái không đổi** | thấp hơn hẳn | contract bỏ qua, không sinh sự kiện |

> **⚠️ Kỳ vọng ban đầu sai lần thứ hai ở mục này — và sai theo hướng ngược với lần trước.**
> Test đầu tiên đòi `gasDau > gasSau * 2` và **đỏ**: tỷ lệ thật chỉ **1,55×**, không phải 8,47×
> như bảng trên. Lý do: khi mỗi lần thu hồi là **một giao dịch riêng**, phần cố định (21.000
> gas phí giao dịch + kiểm quyền `AccessControl`) át tỷ lệ. **Hiệu** thì đúng lý thuyết:
> 47.978 − 30.878 = **17.100**, khớp chênh lệch SSTORE 20.000 − 2.900.
>
> **Hệ quả cho báo cáo:** con số 8,47× ở bảng trên **chỉ đúng cho thu hồi hàng loạt**. Trộn
> hai phép đo này lại là chỗ dễ bị hội đồng bắt bẻ nhất của cả mục 11.4. Trình bày chúng như
> hai dòng riêng, kèm điều kiện đo.

**Tái lập:**

```
cd contracts && npx hardhat node          # cửa sổ 1
cd contracts && npm run deploy:local      # cửa sổ 2 — in ra địa chỉ StatusList
$env:LOCAL_CHAIN_TEST="true"; $env:LOCAL_STATUS_LIST="0x…"
.\scripts\test-backend.ps1 StatusListClientLocalChainTest
```

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
| 2026-08-06 | **Giao dịch `anchor()` thật đầu tiên trên Amoy** | Lô `ATTEND` 2026080501, 4 bản ghi điểm danh thật, 81.968 gas. Proof lấy từ CSDL xác minh được về root đọc từ RPC công cộng **không key**. Đóng cổng kiểm soát cuối tuần 3 | Hoàng |
| 2026-08-06 | **Vòng khép kín CREDENTIAL — đóng mốc tuần 4** | Đăng ký issuer (133.590 gas) + neo lô `CRED` 2026080601 (81.944 gas) ≈ 0,0089 POL. Bundle 1.529 byte **verify 6/6** bằng script Node, ba `eth_call` trên RPC công cộng không key, không chạm backend. Sửa `totalPoints` 15→95 làm **ba lớp độc lập cùng đỏ**. Hạn chế: lô chỉ 1 lá nên proof rỗng. Xem §11.7 | Hoàng |
| 2026-08-06 | **Neo nhật ký `AUDIT` — đóng luận điểm 1** | 5 thao tác nghiệp vụ thật qua HTTP+JWT → 5 mắt xích → lô `AUDIT` 2026080601 (81.956 gas). **5/5 proof** xác minh được về root đọc từ Amoy; cây 5 lá nên có cả trường hợp nút lẻ bị đẩy lên (proof 1 sibling). Xem §11.8 | Hoàng |
| 2026-08-06 | **Gas trạng thái ổn định trên Amoy** *(lấp chỗ trống cũ)* | Lô thứ hai của `ATTEND` = **64.028**, của `CRED` = **64.004**. Chi phí khởi tạo miền = **17.940 gas**, trả một lần cho mỗi miền. Quy đổi học kỳ tính lại bằng số Amoy: **1,08 POL** thay vì 0,92 (số local cũ thấp hơn 17%) | Hoàng |

---

## 11.6. Vòng khép kín end-to-end — 2026-08-06

**Không phải một phép đo có số, mà là bằng chứng luận điểm 2 chạy được thật.** Nên đưa vào
báo cáo như một mục riêng, và quay video đúng luồng này ở tuần 6.

### Chuỗi mắt xích, từ bản ghi tới xác minh độc lập

| # | Bước | Bằng chứng |
|---|---|---|
| 1 | 4 bản ghi điểm danh thật trong MySQL | tuần 2, đủ `QR_SCAN` · `OFFLINE_SYNC` · `MANUAL` |
| 2 | → payload chuẩn tắc 11 trường | `AttendancePayload`, khớp vector `attend-payload-*` |
| 3 | → leaf hash | `LeafHasher`, Java 54 test · JS 121 test |
| 4 | → Merkle root | `MerkleService`, Java 72 test · JS 75 test |
| 5 | → giao dịch trên Amoy | [`0x1d1ebe…db75`](https://amoy.polygonscan.com/tx/0x1d1ebe0d84320b669fe15243eee4a6a6d58b736cdd204db19ccbc08fa747db75) · 81.968 gas |
| 6 | → đọc lại root bằng **một** `eth_call` | `0x88f8f893…a830cc`, khớp root job tính |
| 7 | → proof từ CSDL xác minh về root trên chuỗi | **4/4 bản ghi xác minh được** |
| 8 | → sửa 1 byte của leaf | **bị từ chối** — phép kiểm tra là thật |

### Điều kiện của bước 6–7, và vì sao nó là điểm bán hàng chính

Đọc bằng `verifier/src/chain.mjs`, chỉ dùng `ethers`, trỏ vào
`https://polygon-amoy-bor-rpc.publicnode.com/` — **endpoint công cộng, không cần API key,
không gọi backend một dòng nào**.

Nghĩa là: hồ sơ vẫn xác minh được kể cả khi trường đã tắt máy chủ **và** ngừng trả tiền cho
mọi dịch vụ RPC. Đây chính là luận điểm 2 (`PROJECT.md` §10), và giờ nó có số liệu thật chứ
không còn là lời hứa.

Điều này chỉ khả thi vì `AnchorRegistry` cho tra cứu trực tiếp `(domain, batchId) → root`.
Nếu verifier phải dò sự kiện thì giới hạn 10.000 block mỗi lần gọi `eth_getLogs` của endpoint
công cộng (`PROJECT.md` §2.2) sẽ buộc nó phân trang hàng trăm lần và chết.

### Phải nói rõ giới hạn của vòng này

- **Chưa có bundle JSON.** Bước 7 hiện lấy proof trực tiếp từ CSDL. Định dạng bundle mà sinh
  viên cầm đi là việc tuần 4.
- **Chưa có giao diện verifier.** Mới có thư viện đọc chuỗi, chưa có trang tĩnh — tuần 6.
- **4 bản ghi là lô nhỏ.** Cây Merkle 4 lá có 2 tầng; nó **không** chứng minh được hành vi ở
  quy mô lớn. Phần đó đã kiểm riêng: lô 9 lá (số lẻ, có nút bị đẩy lên) trên chuỗi cục bộ, và
  vector `n100-quy-mo-that`.

---

## 11.7. Vòng khép kín CREDENTIAL — 2026-08-06

**Mốc tuần 4 đã đóng.** Một credential thật, cấp từ dữ liệu điểm danh thật, neo lên Amoy, và
**xác minh được đầy đủ bằng script Node không chạm backend một dòng nào**.

Khác §11.6 ở chỗ nào: §11.6 lấy proof **trực tiếp từ CSDL**, tức là vẫn cần máy chủ của
trường. Vòng này chạy trên một **tệp bundle** mà sinh viên cầm đi — đúng thứ nhà tuyển dụng
nhận được.

### Chuỗi mắt xích

| # | Bước | Bằng chứng |
|---|---|---|
| 1 | 3 bản ghi điểm danh thật của B21DCCN002, 15 điểm, **3/3 xác minh bằng máy** | tuần 2 |
| 2 | → đăng ký ví issuer vào `IssuerRegistry` | [`0x32c420…5df6`](https://amoy.polygonscan.com/tx/0x32c420366f65c2e67473cbbdb1a3ffd97009aafe89b794eea61ea95ad82a5df6) · 133.590 gas |
| 3 | → cấp credential #81, ký secp256k1 trên leaf | `statusListIndex` 953016 (ngẫu nhiên) |
| 4 | → neo lô `CRED` `2026080601` | [`0x0cbaca…e4d6`](https://amoy.polygonscan.com/tx/0x0cbacae962f23e9c56cc8f87a2d46e7f358bcc1ec3c8168aa4aaff032190e4d6) · 81.944 gas |
| 5 | → xuất bundle JSON, 1.529 byte | `scripts\credential-now.ps1 B21DCCN002 -Bundle` |
| 6 | → **6/6 phép kiểm xanh**, ba `eth_call` trên RPC công cộng **không key** | `node scripts/verify-bundle.mjs` · mã thoát 0 |
| 7 | → sửa `totalPoints` 15 → 95 | **3 phép kiểm độc lập cùng đỏ**, mã thoát 1 |

**Tái lập:**

```
cd contracts && npm run register-issuer:amoy      # một lần cho mỗi ví issuer
.\scripts\credential-now.ps1 B21DCCN002           # cấp (chưa chạm chuỗi)
.\scripts\anchor-now.ps1                          # neo — KHÔNG HOÀN TÁC
.\scripts\credential-now.ps1 B21DCCN002 -Bundle   # xuất bundle
cd verifier && node scripts/verify-bundle.mjs ../bundles/B21DCCN002-2026-1.json
```

### Bước 7 là bước đáng trình bày nhất

Sửa một con số trong bundle làm **ba lớp bảo vệ độc lập** cùng báo đỏ:

| Lớp | Vì sao đỏ |
|---|---|
| Leaf hash | payload đổi ⇒ keccak đổi |
| Chữ ký issuer | phục hồi ra địa chỉ khác `issuerAddress` đã neo |
| Merkle proof | leaf mới không dẫn về root trên chuỗi |

Ba lớp này **không phụ thuộc nhau**: phá được một vẫn vướng hai cái còn lại. Đây là chỗ nên
demo trực tiếp trước hội đồng — nó trực quan hơn mọi biểu đồ gas.

Hai phép kiểm còn lại (`IssuerRegistry`, `StatusList`) **vẫn xanh** ở bước 7, và đó là hành vi
đúng: ví cấp vẫn có quyền, credential vẫn chưa bị thu hồi. Việc phân biệt được *"tệp bị sửa"*
với *"credential bị thu hồi"* chính là lý do verifier trả về **danh sách phép kiểm** thay vì
một giá trị đúng/sai.

### ⚠️ Bốn hạn chế phải nói ra, đừng để hội đồng tự tìm

1. **Lô `CRED` chỉ có 1 lá**, nên `root == leaf` và **proof rỗng — bước Merkle là trường hợp
   biên, không chứng minh gì về cây**. Lý do: chỉ 2 sinh viên trong CSDL có bản ghi điểm danh,
   và cấp được đúng 1 credential ở thời điểm neo. Hành vi cây nhiều lá đã có bằng chứng ở chỗ
   khác: lô `ATTEND` 4 lá trên chuỗi (§11.6, 4/4 proof) và fixture 4 lá (`bundle-fixture.json`).
   **Muốn có lô `CRED` nhiều lá thì cần thêm dữ liệu điểm danh thật** — chính là buổi demo 5
   người còn nợ.
2. **Khóa issuer TRÙNG khóa neo** (`0xf32728…F529F`). Quyết định của người làm, có ý thức.
   Hệ quả: một lần lộ khóa vừa cấp được credential giả vừa neo được root rác. Tách hai khóa
   là việc nên làm trước khi có người dùng thật; `IssuerSigner` đã cảnh báo lúc khởi động.
3. **Chưa lọc theo học kỳ.** `events` không có cột học kỳ/năm học, nên `activityCount` và
   `totalPoints` đếm **toàn bộ** bản ghi điểm danh của sinh viên. Với dữ liệu demo một đợt thì
   đúng; nhiều kỳ thì gộp. Cố ý không tự chế mốc ngày để chia kỳ — một khoảng ngày đoán mò sẽ
   thành con số sai trong credential **đã ký và đã neo**, thứ không sửa được.
4. **`StatusList` chưa nối dây.** Phép kiểm thu hồi đọc bit thật trên chuỗi, nhưng chưa có
   luồng nào **bật** bit đó. Nên hiện tại nó luôn trả "còn hiệu lực" — đúng, nhưng chưa chứng
   minh được cơ chế thu hồi hoạt động.

### Chi phí thật của cả vòng

| Khoản | Gas | Giá gas | POL |
|---|---:|---:|---:|
| `registerIssuer` (một lần / ví) | 133.590 | 30 gwei | 0,0040 |
| `anchor` lô `CRED` | 81.944 | 60 gwei | 0,0049 |
| **Cộng** | **215.534** | | **≈ 0,0089** |

Ví còn **0,2327 POL** sau cả hai giao dịch (trước đó 0,2417). `registerIssuer` là chi phí
**một lần cho mỗi đơn vị cấp phát**, không lặp lại theo số credential.

---

## 11.8. Nhật ký có chuỗi băm đã NEO — luận điểm 1 — 2026-08-06

**Mắt xích cuối cùng của luận điểm 1 đã đóng.** Trước hôm nay, chuỗi băm chỉ chứng minh được
tính nhất quán *nội bộ*: quản trị viên có toàn quyền CSDL tính lại cả chuỗi là qua mặt được.
Giờ root nằm trên chuỗi công khai và **không tính lại được nữa**.

### Vòng chạy

| # | Bước | Bằng chứng |
|---|---|---|
| 1 | 5 thao tác nghiệp vụ **thật qua HTTP + JWT**, đăng nhập bằng tài khoản `canbo` | 2× `ATTENDANCE_MANUAL` · `CREDENTIAL_ISSUE` · `DEVICE_REVOKE` · `DEVICE_APPROVE` |
| 2 | → 5 mắt xích, mỗi cái nối vào cái trước | `actor_id = 504` cho hành động của người |
| 3 | → neo lô `AUDIT` `2026080601` | [`0xe965fb…0a42`](https://amoy.polygonscan.com/tx/0xe965fb7c0e1e5f8de8f329493ceefefa67c1b6970643c51faf0fbfa0878c0a42) · 81.956 gas |
| 4 | → đọc root bằng **một `eth_call`** trên RPC công cộng không key | `0x1250efca…821b3f` |
| 5 | → **5/5 proof** xác minh được, dựng lại bằng chính mã của verifier | proof 3 sibling; lá cuối 1 sibling (nút lẻ đẩy lên) |
| 6 | → sửa `entityId` của một bản ghi | **bị từ chối** |

Cố ý đi qua **đúng đường HTTP + xác thực**, không gọi thẳng service: nhật ký ghi `actor_id`, và
giá trị đó chỉ có nghĩa khi nó đến từ một phiên đăng nhập thật.

### Vì sao lô này là lô có ý nghĩa nhất trong ba lô đã neo

Nó có **5 lá** — cây Merkle 3 tầng, proof dài 3, và **lá cuối bị đẩy lên nên proof của nó chỉ
dài 1**. Đó đúng là trường hợp biên mà `docs/canonicalization.md` §8.1 chốt bằng quy ước
`duplicateOdd: false`, và đây là lần đầu nó chạy **trên chuỗi thật** chứ không chỉ trong test.

Hai lô `CRED` đều 1 lá (`root == leaf`, proof rỗng) nên chúng **không** kiểm được gì về cây.

### Ba phép kiểm độc lập, và cái nào bắt được gì

| Sửa cái gì | Chuỗi băm | Merkle proof |
|---|---|---|
| Nội dung một bản ghi, quên tính lại mắt xích | **bắt** | **bắt** |
| Nội dung + tính lại mắt xích của chính nó | **bắt** (`prevHash` bản sau lệch) | **bắt** |
| **Nội dung + tính lại TOÀN BỘ chuỗi** | **KHÔNG bắt** | **bắt** — root trên chuỗi không đổi được |

Dòng cuối là lý do phải neo. Có test (`tinhLaiCaChuoiThiKhongBat`) cố tình chứng minh cột giữa
của dòng đó thua — xem `docs/canonicalization.md` §14.2.

### ⚠️ Ba hạn chế phải nói ra

1. **Cửa sổ 24 giờ.** Job neo chạy 02:00 hằng đêm, nên việc sửa hồi tố vẫn giấu được trong
   khoảng giữa hai lần neo. Thu hẹp chỉ tốn thêm giao dịch, không tốn thiết kế — chi phí neo
   không phụ thuộc số bản ghi. **Đây là cái núm đánh đổi định lượng được**, nên nêu trong báo
   cáo thay vì im lặng.
2. **`CREDENTIAL_ISSUE` trong lô này có `actor_id` NULL** dù được cấp qua API bởi `canbo` —
   `CredentialService` hardcode `null` ở chỗ ghi nhật ký. Phát hiện khi đọc lại nhật ký thật
   trước lần neo này; đã sửa, nhưng **bản ghi đã neo thì không sửa được** và giữ nguyên như
   một dấu vết trung thực.
3. **Nhật ký chưa phủ hết thao tác.** Năm loại sự kiện đã ghi là những loại có hệ quả lớn
   nhất; sửa sự kiện, sửa hồ sơ sinh viên, đổi ruleset thì chưa. Ghi vào phần hạn chế.

### Chi phí

| Giao dịch | Gas | Ghi chú |
|---|---:|---|
| `AUDIT` lô đầu | 81.956 | gồm 17.940 khởi tạo miền, trả một lần |
| `ATTEND` lô thứ hai | 64.028 | **số ổn định đầu tiên đo được trên Amoy** |
| `CRED` lô thứ hai | 64.004 | |
| **Cộng** | **210.988** | ≈ 0,0127 POL ở 60 gwei |

Ví còn **0,2201 POL**.
