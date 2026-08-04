# PROJECT.md — Kế hoạch triển khai

**Đề tài:** Sổ tay hoạt động sinh viên & tự động hóa chấm điểm rèn luyện có neo blockchain
**Nguồn:** `Xay-dung-mobile-app-blockchain-Huong-dan-trien-khai.docx`
**Ràng buộc:** 1 người · 8 tuần · nghiệm thu NCKH
**Ngày lập:** 2026-08-04

---

## 1. Phán quyết khả thi (đọc trước tiên)

Tài liệu gốc viết rất tốt — phần đánh giá thẳng thắn về vai trò blockchain (ch.2) và bảng threat model (ch.11.2) là mức tư duy trên trung bình nhiều so với NCKH sinh viên thông thường. Định vị đề tài đúng, không thổi phồng.

**Nhưng khối lượng công việc bị ước lượng thiếu.** Tài liệu nói "phạm vi đầy đủ chỉ vừa 8 tuần nếu chấp nhận cả bảy quyết định". Đánh giá của tôi: kể cả chấp nhận đủ bảy quyết định, đây là khoảng **10–12 tuần-người**, không phải 8. Cụ thể:

| Tuần | Kế hoạch gốc | Ước lượng thực tế | Ghi chú |
|---|---|---|---|
| 1 | 16 bảng + JWT + CRUD 40 endpoint + OpenAPI | 1–1,5 tuần | Khả thi **nếu** đã thạo Spring Boot |
| 2 | QR động + presenter + check-in/out + device binding + geofence + hàng đợi offline | 1,5 tuần | Offline queue + đồng bộ là phần bị đánh giá thấp nhất |
| 3 | 3 contract + test + deploy + verify + web3j wrapper + MerkleService + test vector | **2–3 tuần nếu học Solidity từ đầu** | Đây là chỗ vỡ kế hoạch |
| 4 | Job neo + hash chain + VC + ES256K + StatusList + bundle | 1,5 tuần | |
| 5 | Rule engine + chấm kỳ + khiếu nại + khen thưởng | 1–1,5 tuần | |
| 6 | Hoàn thiện 3 PWA + verifier | **2 tuần** | "Hoàn thiện UI cho 3 vai trò" trong 1 tuần là không thực tế |
| 7 | Đo đạc + viết báo cáo | 1 tuần | Giữ nguyên, không được cắt |
| 8 | Slide + video + dự phòng | 1 tuần | |

Tổng: ~11–13 tuần. Thiếu khoảng 3–5 tuần.

**Kết luận:** kế hoạch này *sẽ* vỡ nếu bám nguyên phạm vi. Tài liệu đã có sẵn cơ chế đúng để xử lý (ba tín hiệu báo động ch.10.1 + thứ tự cắt ch.10.2), nhưng dùng nó **phản ứng** thì đã muộn — đến cuối tuần 3 mới biết phải cắt thì đã mất 3 tuần code vào thứ sắp bỏ.

→ **Khuyến nghị: cắt trước, ở tuần 0.** Xem §3.

---

## 2. Những điểm cần sửa trong tài liệu trước khi code

Đã kiểm chứng bằng nguồn công khai, tháng 8/2026.

### 2.1. Spring Boot 3.3 đã hết hỗ trợ — phải đổi

Tài liệu chốt Spring Boot 3.3. Thực tế theo [endoflife.date](https://endoflife.date/spring-boot):

| Nhánh | Hết hỗ trợ OSS | Patch cuối |
|---|---|---|
| 3.3 | 30/06/2025 (đã EOL 13 tháng) | 3.3.13 |
| 3.5 | 30/06/2026 (vừa EOL) | 3.5.16 |
| 4.0 | 31/12/2026 | 4.0.7 |
| 4.1 | 31/07/2027 | 4.1.0 |

**Khuyến nghị: Spring Boot 3.5.16.** Lý do — và đây là đánh đổi có chủ ý, không phải lười:

- Boot 4.x dùng **Jackson 3**, đổi package `com.fasterxml.jackson` → `tools.jackson`. Toàn bộ tutorial, câu trả lời StackOverflow, và ví dụ code hiện có đều viết cho Jackson 2. Với người làm một mình đang đồng thời học Solidity, gỡ lỗi tích hợp Jackson 3 ở tuần 4 là rủi ro lớn hơn nhiều so với việc dùng nhánh đã EOL.
- Đây là prototype testnet không chạy production, EOL là dòng ghi chú trong phần hạn chế, không phải rủi ro bảo mật thật.
- `springdoc-openapi-starter-webmvc-ui` 2.x ổn định trên Boot 3.5; bản 3.1.0 mới hỗ trợ Boot 4 và còn ít người dùng.

Nếu vẫn muốn Boot 4.0: dùng springdoc 3.1.0, và **dự phòng thêm 3 ngày** cho việc gỡ lỗi Jackson 3 ở tầng canonicalization (§2.3).

### 2.2. RPC endpoint — tài liệu nói đúng, xác nhận

Polygon đã [thông báo ngừng các endpoint RPC công cộng](https://forum.polygon.technology/t/deprecation-of-polygons-public-rpc-endpoints-mainnet-amoy/22014) (cả mainnet lẫn Amoy), `rpc-amoy.polygon.technology` ngừng từ 17/07/2026. Amoy (chainId 80002) vẫn là testnet chính thức đang hoạt động. Bắt buộc dùng Alchemy / Infura / Chainstack / QuickNode gói miễn phí.

Bổ sung: các namespace `erigon_*` và `trace_*` đã bị bỏ trên Amoy từ 01/07/2026. Không dùng chúng trong script đo gas — dùng `eth_getTransactionReceipt` và đọc `gasUsed`.

web3j hiện ở **5.0.3** (phát hành 21/01/2026), đã chuyển về Linux Foundation Decentralized Trust, **yêu cầu Java 21+** — khớp với lựa chọn Java 21 của tài liệu.

### 2.3. ⚠️ Lỗ hổng thiết kế: leaf hash không có nonce

Tài liệu (ch.3, ch.6.4) khẳng định "chỉ lưu hash, không lưu dữ liệu cá nhân" và dùng:

```
leaf = keccak256( domain || ":" || JCS(payload) )
```

Vấn đề: **payload nằm trong không gian nhỏ và đoán được.** MSSV của một trường là vài chục nghìn giá trị; eventId vài trăm; thời gian check-in trong khoảng một buổi. Ai có một `leaf_hash` có thể **vét cạn để khôi phục payload** trong vài giây — tổng không gian chỉ cỡ 10⁸–10⁹ tổ hợp.

Điều này chưa lộ gì từ *on-chain* (trên chuỗi chỉ có Merkle root, không có leaf). Nhưng nó vỡ ngay khi:
- Sinh viên xuất bundle JSON chứa proof — proof là các leaf hash anh em (sibling), tức là hash bản ghi điểm danh **của sinh viên khác**. Đưa bundle cho nhà tuyển dụng = tiết lộ dữ liệu người khác.
- Bất kỳ API công khai nào trả về leaf hash để tra cứu.

**Sửa:** thêm một trường `nonce` ngẫu nhiên 16 byte vào mọi payload được neo, lưu ở DB cùng bản ghi:

```
leaf = keccak256( domain || ":" || JCS({...payload, nonce}) )
```

Chủ sở hữu bản ghi có nonce nên vẫn tự xác minh được; người ngoài cầm proof không vét cạn được sibling. Chi phí: một cột `nonce BINARY(16)`, không tốn gas thêm.

**Sửa kèm:** cấp `status_list_index` **ngẫu nhiên từ pool còn trống**, không cấp tuần tự. Cấp tuần tự khiến sự kiện `StatusChanged(index)` trên chuỗi lộ thứ tự cấp phát và tương quan với danh sách sinh viên.

Đây là điểm nên viết hẳn một mục trong báo cáo — nó cho thấy hiểu rằng "hash không phải là ẩn danh", một hiểu lầm rất phổ biến trong các đề tài blockchain giáo dục. Có thể trích W3C Verifiable Credentials phần *unlinkability* để chống lưng.

### 2.4. ⚠️ Camera trong PWA trên iOS không đáng tin như tài liệu nói

Tài liệu (ch.4.3) viết: *"quét QR qua camera hoạt động tốt trên cả Android và iOS"*. Đây là điểm lạc quan nhất trong tài liệu và cần chỉnh.

Thực tế WebKit: `getUserMedia` trong PWA chạy **standalone mode** (đã "Add to Home Screen") trên iOS có lịch sử hỏng dài — [webkit bug 185448](https://bugs.webkit.org/show_bug.cgi?id=185448) — và ngay cả khi chạy được thì [quyền camera không được lưu](https://kb.strich.io/article/29-camera-access-issues-in-ios-pwa), người dùng bị hỏi lại mỗi lần mở app. Ở EU, iOS 17.4+ đã bỏ standalone PWA theo DMA, PWA mở trong tab Safari.

**Ảnh hưởng trực tiếp:** app sinh viên — chức năng quét QR — là luồng demo quan trọng nhất của đề tài.

**Biện pháp, chọn một:**
1. **Bỏ `apple-mobile-web-app-capable`** cho app sinh viên, để nó chạy trong tab Safari (camera ổn định), chỉ dùng standalone cho app cán bộ và presenter. Mất cảm giác "app thật" trên iOS, đổi lấy camera chạy được. — *Khuyến nghị.*
2. Demo nghiệm thu bằng thiết bị Android, ghi rõ giới hạn iOS trong báo cáo.
3. Luồng dự phòng: sinh viên **hiển thị** QR tĩnh của mình, cán bộ quét bằng máy cán bộ. Đảo chiều quét, không phụ thuộc camera máy sinh viên. Nên làm luồng này **bất kể chọn gì** — nó cũng là phương án cứu khi hội trường mất sóng.

**Phải kiểm chứng ở tuần 0**, không để đến tuần 6. Test trên một iPhone thật, 30 phút.

### 2.5. Quy chế điểm rèn luyện — xác nhận và cảnh báo

Thông tư 16/2015/TT-BGDĐT có hiệu lực từ 28/09/2015, thang 100 điểm chia **20/25/20/25/10** cho năm tiêu chí, phân loại: xuất sắc 90–100, tốt 80–<90, khá 65–<80, trung bình 50–<65, yếu 35–<50, kém <35. Tài liệu ghi đúng.

Chưa xác nhận được văn bản thay thế tính đến 8/2026. **Vẫn phải lấy bản quy chế trường đang áp dụng** — các trường điều chỉnh khá nhiều trong khung này, và hội đồng là người của trường nên sẽ biết ngay nếu dùng sai thang.

### 2.6. Thiếu trong tài liệu

- **Không có chiến lược test.** Với hệ có canonicalization xuyên hai ngôn ngữ, tối thiểu cần: bộ test vector Java↔JS (tài liệu có nêu, tốt), test Hardhat cho 3 contract, và một integration test cho luồng check-in. Không cần CI.
- **Không nói ai giữ backup master key.** Mục 12 ghi rủi ro "mất khóa custodial" nhưng biện pháp chỉ là "backup ngoài repo". Cần cụ thể: file mã hóa, 2 bản, một trên máy một trên USB/cloud riêng, ghi thủ tục khôi phục vào `docs/`.
- **Không có kế hoạch reset dữ liệu demo.** Buổi bảo vệ cần dữ liệu sạch, tái lập được. Viết `seed.sql` + script reset ngay tuần 1.
- **Không có ngân sách gas.** Neo hằng đêm × 5 domain × 8 tuần trên testnet là miễn phí, nhưng faucet Amoy có giới hạn ngày. Lấy POL sớm và đều, đừng để hết vào tuần 7 khi đang chạy đo gas.

---

## 3. Phạm vi hiệu chỉnh — cắt trước, không cắt sau

Bảy khối chức năng gốc (ch.1.1), phân loại lại theo giá trị nghiên cứu trên chi phí:

### Giữ — lõi không đụng tới

| Khối | Vì sao giữ |
|---|---|
| Điểm danh nhiều lớp kiểm tra (QR động, device binding, offline) | 80% giá trị thực tiễn; là tầng oracle làm cho phần chuỗi có nghĩa |
| Neo Merkle + AnchorRegistry | Hiện thực hóa luận điểm 2.2a — lý do tồn tại của đề tài |
| Verifier độc lập | Hiện thực hóa luận điểm 2.2b; bằng chứng trực quan nhất khi demo |
| Rule engine chấm điểm (SpEL) + evidence_hash | Là thứ đề tài hứa giải quyết; `evidence_hash` là đóng góp học thuật rõ nhất |
| IssuerRegistry | Rẻ (~40 dòng Solidity), chống lưng luận điểm 2.2c |

> **Về tên gọi khối điểm danh.** Trong báo cáo dùng "điểm danh **nhiều lớp kiểm tra**",
> không dùng "điểm danh **chống gian lận**". Cơ chế này **ngăn** được chia sẻ mật khẩu và
> ảnh chụp QR gửi sau, **tăng chi phí** của quét hộ lâu dài, nhưng **không ngăn** được
> việc sinh viên đưa thẳng điện thoại đã đăng nhập cho bạn. "Chống gian lận" đọc như một
> lời hứa tuyệt đối và sẽ bị hội đồng bắt bẻ. Xem bảng đầy đủ ở `docs/measurements.md` §11.2.

### Cắt ngay tuần 0

| Khối | Tiết kiệm | Ghi vào báo cáo |
|---|---|---|
| **Courses + enrollments** (2 bảng, CRUD, progress tracking) | ~4 ngày | "Khóa học nội bộ được mô hình hóa như một loại sự kiện; tách riêng là hướng phát triển" |
| **Appeals + rewards** (2 bảng, luồng duyệt, UI) | ~3 ngày | "Khiếu nại xử lý ngoài hệ thống ở phiên bản hiện tại" |
| **MinIO** | ~1 ngày | Dùng thư mục cục bộ. MinIO không đóng góp gì cho luận điểm nghiên cứu |

Tổng tiết kiệm: ~8 ngày. Vẫn còn thiếu, nên có tầng cắt thứ hai:

### Cắt có điều kiện — quyết ở cổng tuần 3

| Khối | Điều kiện cắt | Hệ quả |
|---|---|---|
| **StatusList + thu hồi credential** | Nếu cuối tuần 3 chưa có giao dịch trên Amoy | Mất "số liệu đo đẹp nhất" (ch.6.3 — so sánh gas bitmap vs mapping). Đau, nhưng đo gas có thể làm **thuần trên Hardhat local**, không cần deploy — xem §7 |
| **Credential VC đầy đủ (ES256K, HD wallet, bundle)** | Nếu cuối tuần 4 chưa ký được credential | Rút về credential ký bằng **một khóa issuer duy nhất**, bỏ HD wallet per-student. Tiết kiệm ~3 ngày, gần như không mất luận điểm nào |

**Lưu ý về thứ tự cắt gốc (ch.10.2):** tài liệu xếp credential sau chấm điểm. Tôi đồng ý. Nhưng bổ sung: HD wallet per-student (quyết định số 2) có thể cắt **riêng** khỏi credential. Ký tất cả credential bằng một khóa của tổ chức cấp phát vẫn đúng chuẩn VC — `issuer` trong VC là **tổ chức**, không phải sinh viên. Sinh viên là `subject`, không cần khóa. Đây là chỗ tài liệu làm phức tạp hơn cần thiết: HD wallet per-student tốn ~2 ngày mà không phục vụ luận điểm nào trong ch.2.

→ **Khuyến nghị bỏ HD wallet ngay tuần 0**, thêm ~2 ngày vào ngân sách. Ghi vào hạn chế: "mô hình custodial một khóa tổ chức; DID per-student và account abstraction là hướng phát triển".

---

## 4. Tech stack chốt

Thay đổi so với tài liệu được **in đậm**.

### Backend
```
Java 21
Spring Boot 3.5.16                     (doc: 3.3 — đã EOL, xem §2.1)
spring-boot-starter-{web,data-jpa,security,validation}
mysql-connector-j                      MySQL 8
flyway-core                            migration, KHÔNG ddl-auto=update
jjwt                                   JWT access + refresh
springdoc-openapi-starter-webmvc-ui 2.x
mapstruct + lombok
web3j 5.0.3                            (core + codegen) — cần Java 21+
bouncycastle                           (web3j kéo theo)
zxing core
bucket4j                               rate limit in-memory
caffeine                               cache in-memory
```
Không Redis, không Kafka, không ShedLock — hệ chạy một instance, `@Scheduled` là đủ.

### Hợp đồng
```
Solidity 0.8.26+       Hardhat       OpenZeppelin Contracts v5 (chỉ AccessControl)
Polygon Amoy, chainId 80002, verify trên PolygonScan
RPC: Alchemy/Infura/Chainstack — KHÔNG dùng RPC công cộng Polygon
```

### Frontend
```
Vite + React 18 + TypeScript
@tanstack/react-query · zustand · react-router-dom
tailwindcss + shadcn/ui
html5-qrcode                 (xem §2.4 về iOS)
qrcode                       render QR động ở presenter
recharts
vite-plugin-pwa
idb-keyval                   hàng đợi check-in offline
openapi-typescript-codegen
```

### Verifier
```
ethers v6 + merkletreejs, build tĩnh, deploy Vercel/GitHub Pages
RÀNG BUỘC CỨNG: không gọi backend một dòng nào
```

---

## 5. Kiến trúc & ranh giới module

Bốn tầng, dữ liệu đầy đủ off-chain, chỉ bằng chứng toàn vẹn on-chain.

```
Student PWA   Staff console   Presenter          Verifier (độc lập)
  (sổ tay,      (chấm điểm)   (QR động 10s)      (đọc thẳng chuỗi)
   quét QR)                                            │
      └──────────────┴──────────────┘                  │
                     ▼                                 │
          Spring Boot API (đơn khối, 7 module)          │
                     ▼                                 │
      MySQL + file cục bộ  ──►  Job neo hằng đêm        │
                                (Merkle root theo lô)   │
                                        ▼              ▼
                     Polygon Amoy: IssuerRegistry · AnchorRegistry · StatusList
```

Package backend `vn.ptit.drl.*`: `identity` · `org` · `event` · `attendance` · `credential` · `scoring` · `anchor` · `audit`

**Ranh giới quan trọng nhất:** module `anchor` **không được biết gì về nghiệp vụ**. Nó nhận `List<byte[]> leaves` + `domain`, trả `MerkleRoot` và `Proof`. Không import class nào từ `scoring` hay `attendance`. Giữ được ranh giới này thì phần đo đạc ở tuần 7 (§7.1) chỉ là gọi một hàm với N khác nhau; không giữ được thì phải viết lại.

**Repo:**
```
nckh-drl/
├── contracts/     Hardhat: 3 .sol, test, script deploy Amoy
├── backend/       Spring Boot đơn khối
├── app/           PWA: student + staff + presenter (1 codebase, 3 route tree)
├── verifier/      Static, chỉ ethers + merkletreejs
└── docs/          ERD, threat model, measurements.md, báo cáo
```

---

## 6. Kế hoạch 8 tuần (đã hiệu chỉnh)

Thay đổi lớn nhất so với bản gốc: **spike blockchain ở tuần 1, không phải tuần 3.**

Lý do: toolchain chuỗi (tài khoản RPC, faucet, deploy, verify, sinh wrapper web3j) là phần **rủi ro không biết trước** cao nhất và **hoàn toàn độc lập** với nghiệp vụ. Phát hiện faucet cạn hoặc verify PolygonScan hỏng ở tuần 3 làm vỡ kế hoạch; phát hiện ở tuần 1 chỉ tốn nửa ngày xoay xở.

### Tuần 0 (2–3 ngày, trước khi tính tuần 1)
- [ ] Lấy bản quy chế điểm rèn luyện trường đang áp dụng, xác nhận thang 5 tiêu chí
- [ ] **Test camera QR trên iPhone thật** — quyết định standalone vs Safari tab (§2.4)
- [ ] Tạo tài khoản Alchemy, lấy RPC Amoy; lấy POL từ faucet
- [ ] Đọc EduCTX (Turkanović 2018) + W3C VC Data Model, ghi 1 trang khác biệt của đề tài
- [ ] Chốt phạm vi đã cắt (§3), viết vào `docs/scope.md` — **ký tên vào đó, không sửa sau**
- [ ] Khởi tạo monorepo + Docker Compose MySQL
- [x] Vẽ ERD (**14 bảng** sau khi cắt và bổ sung `student_devices` — xem `docs/erd.md`)
- [ ] Tạo `docs/measurements.md` rỗng với sẵn khung bảng số liệu ch.11
- [ ] Đặt lịch cố định 3 cổng kiểm soát (§6, cuối tuần 2/3/5)

### Tuần 1 — Nền + spike chuỗi
Flyway 12 bảng · auth JWT · CRUD tổ chức/sự kiện · seed 500 sinh viên giả · OpenAPI → TS client.
**+ Spike nửa ngày:** deploy một contract `Hello` lên Amoy, verify trên PolygonScan, gọi từ Java bằng web3j.

> **Mốc:** Swagger ≥30 endpoint, lệnh sinh client chạy được, **và một tx của mình đã hiện trên Amoy explorer**.
>
> **Trạng thái 2026-08-05:** ✅ 30 endpoint · ✅ sinh client (`scripts/gen-api-client.ps1`, 7 service / 23 model) · ❌ spike chuỗi **chưa làm** — chờ tài khoản Alchemy và POL từ faucet.

### Tuần 2 — Điểm danh (khối giá trị nhất)
QR động HMAC 10s · màn hình presenter · check-in/out · device binding · geofence cảnh báo mềm · hàng đợi offline IndexedDB.

> **Mốc:** demo thật với 5 người, **có người được giao nhiệm vụ thử gian lận**. Ghi lại
> kết quả — đây là dữ liệu cho bảng threat model.
>
> **Sáu kịch bản phải thử, kể cả những cái biết trước là sẽ qua được:**
>
> | # | Kịch bản | Dự kiến |
> |---|---|---|
> | 1 | Chụp màn hình QR gửi bạn ở nhà (quá 20 giây) | 🛡️ chặn |
> | 2 | Mượn tài khoản, quét bằng máy chưa duyệt | 🛡️ chặn |
> | 3 | Đứng ngoài khu vực nhưng token và máy đúng | ✅ qua, bị đánh dấu |
> | 4 | **Đưa chính điện thoại đã đăng nhập cho bạn quét** | ❌ **qua được** |
> | 5 | **Sao chép `localStorage['drl.deviceFp']` sang máy khác** | ❌ **qua được** |
> | 6 | **Chuyển tiếp ảnh QR ngay trong 20 giây** | ❌ **qua được** |
>
> Kịch bản 4–6 quan trọng ngang kịch bản 1–2. Một bảng threat model có ô "qua được"
> đáng tin hơn nhiều so với bảng toàn ô "chặn" — hội đồng biết không hệ thống nào chặn
> được tất cả.

### Tuần 3 — Chuỗi + Merkle
3 contract + test Hardhat · deploy Amoy · verify · wrapper web3j · `MerkleService`.
**Ngày đầu tuần, việc đầu tiên:** viết bộ test vector canonicalization — 5 payload mẫu + leaf hash kỳ vọng, chạy qua **cả** Java và JS.

> **Mốc:** giao dịch neo thật trên explorer; test vector xanh cả hai phía.

### Tuần 4 — Neo + credential
Job neo hằng đêm · hash chain audit log · VC + chữ ký issuer · StatusList · xuất bundle JSON.
Nhớ: **nonce trong payload** (§2.3), **status index ngẫu nhiên** (§2.3).

> **Mốc:** bundle credential verify được offline bằng script Node, không chạm backend.

### Tuần 5 — Chấm điểm
Ruleset JSON có version · SpEL evaluator · chạy chấm theo kỳ · `evidence_hash` · neo domain `SCORE` + `RULESET`.

> **Mốc:** chấm 500 sinh viên, mỗi bản ghi có `evidence_hash` tái tính được.

### Tuần 6 — UI + verifier
Hoàn thiện 3 luồng PWA · verifier tĩnh · deploy.

> **Mốc:** end-to-end: điểm danh → credential → điểm → xác minh độc lập. **Quay video luồng này ngay tuần 6**, đừng đợi tuần 8 — nếu tuần 7 có gì hỏng vẫn còn video.

### Tuần 7 — Đo đạc + báo cáo (KHÓA CỨNG)
Bảng gas · threat model · benchmark thời gian · khảo sát SUS nếu kịp · bản nháp báo cáo hoàn chỉnh.

> **Quy tắc bất khả xâm phạm: tuần 7 không viết code tính năng.** Còn bug thì ghi bug vào phần hạn chế. Hội đồng chấm báo cáo, không chấm số dòng code.

### Tuần 8 — Slide, video, dự phòng

---

## 7. Cổng kiểm soát

Kiểm tra **đúng ngày**, không hoãn. Mỗi cổng có hành động cắt cụ thể, quyết trong ngày.

| Cổng | Nếu chưa đạt | Cắt ngay |
|---|---|---|
| **Cuối tuần 2** | Chưa demo được điểm danh thật với người lạ | Đang overengineer backend. Đóng băng mọi CRUD, dồn 100% vào điểm danh |
| **Cuối tuần 3** | Chưa có tx neo trên Amoy | Cắt StatusList, giữ AnchorRegistry + IssuerRegistry. **Đo gas bitmap vs mapping chuyển sang chạy thuần trên Hardhat local** — vẫn ra đủ số liệu cho ch.11.4 mà không cần deploy |
| **Cuối tuần 5** | Chưa chấm được điểm | Rút ruleset xuống **3 tiêu chí**, ghi rõ vào giới hạn phạm vi |

**Bổ sung một cổng gốc không có — cuối tuần 6:** nếu chưa có luồng end-to-end chạy được, **dừng phát triển hoàn toàn** và chuyển sang viết báo cáo sớm. Một hệ thống hẹp có báo cáo tử tế thắng một hệ thống rộng không có số liệu.

---

## 8. Đo lường — ranh giới NCKH vs đồ án môn học

Ghi vào `docs/measurements.md` **ngay khi đo được**, không dồn.

| # | Phép đo | Đầu ra | Làm được từ tuần |
|---|---|---|---|
| 1 | Gas theo kích thước lô Merkle (N = 10/100/1000/5000) | Đồ thị gas/bản ghi → chi phí/sinh viên/kỳ | 3 |
| 2 | Gas thu hồi: bitmap vs mapping-per-credential | Đồ thị thứ hai | 4 (hoặc Hardhat local nếu cắt) |
| 3 | Bảng threat model (11 dòng, ch.11.2) | Đối chiếu CSDL vs thiết kế đề xuất, phân **ba mức** Ngăn / Tăng chi phí / Phát hiện | 2 |
| 4 | Thời gian tổng hợp điểm: tự động (giây) vs thủ công | So sánh định lượng | 5 |
| 5 | Khảo sát SUS 20–30 sinh viên | Điểm SUS + phân tích | 7, nếu kịp |

**Về phép đo #3 — dòng quan trọng nhất là dòng cuối:** "Cán bộ nhập liệu sai từ đầu → cả hai thiết kế đều không chặn (vấn đề oracle)". Giữ nguyên dòng này. Sự trung thực ở đây làm **tăng** điểm bảo vệ — nó chứng minh hiểu công cụ thay vì tin mù quáng. Hội đồng phân biệt được hai thứ đó rất nhanh.

**Về phép đo #4:** con số thủ công lấy từ phỏng vấn 1–2 cán bộ CTSV là bằng chứng yếu, phải ghi rõ là ước lượng và nêu cỡ mẫu. Đừng trình bày nó như kết quả đo có kiểm soát. Có số vẫn hơn không có số, nhưng đừng để hội đồng bắt được chỗ thổi phồng.

---

## 9. Rủi ro

| Rủi ro | Biểu hiện | Biện pháp |
|---|---|---|
| **Vỡ tiến độ do phạm vi** | Tuần 5 mới xong tuần 3 | Cắt trước ở tuần 0 (§3); tôn trọng cổng kiểm soát (§7) |
| **Lệch canonicalization** | Mọi Merkle proof fail | Test vector ngày đầu tuần 3. Phát hiện tuần 3 mất 2 giờ, tuần 6 mất 2 ngày |
| **Camera iOS trong PWA** | Sinh viên không quét được | Test tuần 0; luôn có luồng đảo chiều dự phòng (§2.4) |
| **Sa lầy hạ tầng chuỗi** | Mất 3 tuần dựng Hyperledger Fabric | Public testnet. Tuyệt đối không tự dựng chain |
| **RPC chết** | Không kết nối Amoy | Alchemy/Infura; có sẵn provider thứ hai trong config |
| **Dồn báo cáo vào cuối** | Code đến tuần 8 rồi viết 3 đêm | Khóa cứng tuần 7 kể cả khi còn bug |
| **Ôm thêm tính năng** | Thêm token thưởng, IPFS, NFT | Bám `docs/scope.md` đã ký ở tuần 0 |
| **Mất master key** | Không ký được credential mới | 2 bản backup mã hóa ngoài repo + thủ tục khôi phục trong `docs/` |
| **Faucet cạn** | Không còn POL để deploy/đo | Lấy POL đều hằng tuần, không đợi đến lúc cần |

### Tuyệt đối không làm
- Hyperledger Fabric — dựng CA + orderer + chaincode một mình ngốn 3 tuần. Đây là cạm bẫy phổ biến nhất của NCKH blockchain sinh viên.
- Tự viết cơ chế đồng thuận, tự dựng chain riêng.
- Ví phi tập trung với seed phrase cho sinh viên.
- IPFS cluster, tokenomics, token thưởng, NFT chứng chỉ.
- Microservice, message queue, Kubernetes.
- **Thêm vào danh sách:** đừng học React Native/Flutter song song. Tài liệu nói đúng — đó là công thức thất bại.

---

## 10. Định vị trước hội đồng

Ba luận điểm blockchain **thật sự** bảo vệ được (ch.2.2) — chỉ bảo vệ ba cái này:

1. **Chống sửa hồi tố bởi chính người quản trị.** Không *ngăn* được, nhưng *chứng minh* được. Hiện thực qua hash-chained audit log + neo định kỳ.
2. **Xác minh độc lập sau khi sinh viên rời trường.** Verifier chạy khi máy chủ trường đã tắt. Đây là điểm bán hàng chính.
3. **Nhiều bên cấp phát không hoàn toàn tin nhau.** Đoàn trường, các khoa, CLB, doanh nghiệp đối tác — không bên nào nên độc quyền sổ cái.

**Không bảo vệ luận điểm "blockchain giúp chấm điểm nhanh hơn."** Tốc độ đến từ cơ chế điểm danh và rule engine. Nói câu này trước hội đồng là tự mở cửa cho câu hỏi khó nhất.

**Câu trả lời chuẩn bị sẵn cho "sao không dùng CSDL truyền thống?"** — câu này gần như chắc chắn được hỏi:

> "CSDL truyền thống giải quyết được phần lớn chức năng, và trong kiến trúc của em dữ liệu thật vẫn nằm ở MySQL. Blockchain ở đây không thay thế CSDL, mà giải quyết đúng hai điều CSDL không làm được: chứng minh dữ liệu không bị chỉnh sửa hồi tố bởi chính người có quyền quản trị, và cho phép bên thứ ba xác minh hồ sơ sau khi sinh viên tốt nghiệp mà không phụ thuộc hệ thống của trường. Đổi lại là chi phí gas và độ trễ neo — em có đo cụ thể ở chương đánh giá."

**Câu hỏi thứ hai hay gặp — Nghị định 13/2023/NĐ-CP:** quyền yêu cầu xóa dữ liệu cá nhân mâu thuẫn với tính bất biến của blockchain. Trả lời: dữ liệu cá nhân không bao giờ lên chuỗi, chỉ có Merkle root; xóa bản ghi off-chain làm proof mất hiệu lực, đúng như mong muốn. Nêu thẳng mâu thuẫn này trong báo cáo là điểm cộng học thuật.

**Trích dẫn nền:** W3C Verifiable Credentials Data Model · W3C DID · W3C Status List · Blockcerts (MIT Media Lab) · Turkanović et al., *EduCTX*, IEEE Access 2018.

Khác biệt với EduCTX cần nêu rõ: EduCTX tập trung **tín chỉ học thuật liên trường**; đề tài này tập trung **hoạt động ngoại khóa và điểm rèn luyện**, đóng góp thêm ở **tầng thu thập dữ liệu** — chính là chỗ EduCTX bỏ ngỏ.

Phát biểu đóng góp cho đúng mức: đề tài **không** tuyên bố giải quyết được bài toán oracle. Đóng góp là *đo được* và *phân loại được* chất lượng dữ liệu đầu vào — mỗi bản ghi điểm danh mang theo `verified` và `geofenceOk`, nên biết chính xác bao nhiêu phần trăm dữ liệu được xác thực bằng máy và bao nhiêu do cán bộ nhập tay. Đó là thứ EduCTX và phần lớn đề tài blockchain giáo dục không có.

---

## 11. Skills đã cài cho dự án

Ba skill trong `.claude/skills/`, gọi bằng `/tên-skill`:

| Skill | Dùng khi |
|---|---|
| `scope-guard` | Trước khi thêm bất kỳ tính năng nào — đối chiếu với phạm vi đã chốt và danh sách cấm |
| `canonical-hash` | Khi động vào bất kỳ code nào ảnh hưởng leaf hash — bắt buộc chạy lại test vector Java↔JS |
| `measurements` | Khi có số liệu đo mới — ghi ngay vào `docs/measurements.md` đúng khung chương 11 |

---

## Nhắc cuối

Hội đồng chấm **báo cáo và buổi bảo vệ**, không chấm số dòng code. Một hệ thống phạm vi hẹp nhưng được đo đạc và phân tích tử tế luôn được đánh giá cao hơn một hệ thống đầy đủ tính năng mà không có số liệu nào.

Điều nguy hiểm nhất với đề tài này không phải thiếu năng lực kỹ thuật — mà là **giữ nguyên phạm vi quá lâu rồi cắt vội ở tuần 6**.
