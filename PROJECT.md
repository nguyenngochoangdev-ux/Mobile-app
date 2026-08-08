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

#### Kiểm chứng thực tế 2026-08-05 — và một ràng buộc nó đặt lên thiết kế contract

`AMOY_RPC_URL` hiện trỏ tới **PublicNode** (`polygon-amoy-bor-rpc.publicnode.com`), không
phải Alchemy. Endpoint không cần key, đã đo chạy được: `eth_chainId` = `0x13882`,
`eth_gasPrice`, `eth_estimateGas`, `eth_getTransactionCount`, `eth_getBlockByNumber` đều
OK ở 170–350 ms. Tải của đề tài (3 tx deploy, 5 tx/đêm, vài chục lần đọc receipt) nằm
thừa trong khả năng của nó.

**Nhưng `eth_getLogs` bị giới hạn hai tầng:** bắt buộc có bộ lọc `address`, và tối đa
**10.000 block mỗi lần gọi** (≈ 5,6 giờ lịch sử trên Amoy).

→ **Hệ quả thiết kế, quyết ngay từ khi viết contract:** `AnchorRegistry` phải có hàm đọc
`(domain, batchId) → root`, và bundle của sinh viên mang sẵn `batchId`. Verifier khi đó
chỉ cần **một `eth_call`**, không quét lịch sử, không đụng `eth_getLogs`. Nếu để verifier
đi dò sự kiện thì nó phải phân trang hàng trăm lần và sẽ chết trên bất kỳ endpoint công
cộng nào.

Điều này còn **củng cố luận điểm 2.2b**: verifier là trang tĩnh chạy trong trình duyệt,
nên không giấu được API key. Việc nó chỉ cần một endpoint công cộng không key nghĩa là nó
vẫn xác minh được kể cả khi trường đã ngừng trả tiền cho mọi dịch vụ. Nên viết hẳn ý này
vào báo cáo thay vì coi là hạn chế.

Alchemy vẫn nên lấy làm endpoint **chính cho backend** (`AMOY_RPC_URL_FALLBACK` đang trống)
— lý do là bảo hiểm cho buổi nghiệm thu, không phải vì thiếu năng lực.

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

**Phải kiểm chứng ở tuần 0**, không để đến tuần 6.

### Quyết định 2026-08-05 — demo bằng Android, iOS để ở phần hạn chế

Chốt **phương án 1 + 2 + 3**, không chỉ một:

- **Phương án 1 đã áp dụng từ tuần 2** — `app/index.html` cố ý không đặt
  `apple-mobile-web-app-capable`. Không cần làm gì thêm.
- **Phương án 2 là quyết định mới:** thiết bị demo và thiết bị test là **Android**,
  không phải iPhone.
- **Phương án 3 vẫn phải làm** — luồng đảo chiều là phương án cứu khi hội trường mất sóng,
  độc lập với chuyện chọn thiết bị nào.
  **✅ Xong 2026-08-06.** Sinh viên: `/sv/ma-cua-toi` · cán bộ: `/cb/su-kien/:id/quet` ·
  `StudentQrService` (18 test) · `method = QR_SHOW`. Mã QR **cache vào `localStorage`** nên
  còn hiện được khi mất mạng — nếu không thì trang này vô dụng đúng lúc cần nhất. Mã cũ vẫn
  nhận nhưng `verified = false`. 14 kịch bản kiểm chứng ở `docs/measurements.md` §11.2.

**Hệ quả phải viết đúng trong báo cáo.** Android chưa bao giờ là chỗ có rủi ro; rủi ro nằm
ở WebKit/iOS. Chọn Android **không giải quyết** rủi ro đó mà **né** nó. Vì vậy trong báo
cáo chỉ được phát biểu *"giới hạn iOS dẫn theo báo cáo lỗi WebKit công khai"* — **không**
được viết *"đã kiểm chứng"*. Chuẩn bị sẵn câu trả lời cho câu hỏi "em đã test trên iPhone
chưa?": chưa, và đây là lý do.

### Về việc đóng gói APK — hoãn, không làm trong 8 tuần

Đã xét bằng `/scope-guard` ngày 2026-08-05. Không phục vụ luận điểm nào trong §10, không
sinh số liệu cho chương 11, tốn ~1–1,5 ngày không có chỗ cắt để bù.

Hai lý do kỹ thuật khiến nó cũng **không làm được việc** người ta hay kỳ vọng ở nó:

1. Trusted Web Activity bắt buộc phải có **origin HTTPS đã deploy** kèm
   `.well-known/assetlinks.json` khớp SHA-256 của keystore. PWA deploy ở tuần 6, nên APK
   không thể tồn tại trước tuần 6 — không dùng làm bài test tuần 0 được. Nếu assetlinks
   sai, Chrome tụt về Custom Tab và hiện thanh URL, mất đúng thứ duy nhất APK mang lại.
2. TWA **chính là Chrome đang render trang đó**, nên camera hành xử y hệt Chrome Android.
   Nó không cho thêm thông tin nào so với mở PWA trong Chrome trên máy Android.

Câu ghi vào hướng phát triển: *"Đóng gói PWA thành ứng dụng Android qua Trusted Web
Activity để phát hành trên CH Play là hướng phát triển; ở phiên bản hiện tại hệ thống truy
cập qua trình duyệt."*

### Xét lại lần hai — 2026-08-07, kết luận KHÔNG ĐỔI

Người dùng yêu cầu đóng gói APK. Chạy lại `/scope-guard`; ước lượng cũ 1–1,5 ngày **giờ không
còn đúng**, và lý do làm nó tệ hơn chứ không tốt hơn:

- Máy phát triển **không có gì**: không `ANDROID_HOME`, không Android Studio, không `adb` /
  `sdkmanager` / `gradle` / `keytool`. Phải tải vài GB SDK trước khi viết được dòng nào.
- TWA vẫn bắt buộc **origin HTTPS đã deploy** + `assetlinks.json` khớp SHA-256 keystore, mà
  PWA chưa deploy.
- Hôm nay là **tuần 7 — khóa cứng cho báo cáo**, không có gì cắt được để bù ~2 ngày.

**Thay bằng đường đã có sẵn:** Chrome trên Android cài PWA thành **WebAPK** — biểu tượng trên
màn hình chính, chạy toàn màn hình, không thanh URL, không cần APK/keystore/CH Play. Nó thiếu
đúng **một** thứ: HTTPS, vốn đã là việc còn lại của tuần 6.

**✅ Đã làm 2026-08-07:** backend phục vụ luôn PWA (`WebAppConfig` + `scripts/build-pwa.ps1`)
để **một origin duy nhất** cấp cả app lẫn API. Không có bước này thì app HTTPS gọi API
`http://192.168.x.x:8080` sẽ bị chặn **mixed content** — cài được nhưng đăng nhập không nổi.
Đã kiểm: `/` `/sv/diem` `/manifest.webmanifest` `/sw.js` trả 200, `/api/**` vẫn 401 khi chưa
đăng nhập, fallback SPA khớp `index.html`. Hướng dẫn đầy đủ: `docs/cai-dat-android.md`.

### Xét lại lần ba — 2026-08-08: ĐẢO QUYẾT ĐỊNH, APK vào phạm vi

Người dùng quyết định làm APK. Quyền quyết định phạm vi thuộc về người dùng, không thuộc về
`/scope-guard`. Ghi vào `docs/scope.md` §Nhật ký thay đổi.

**Hai lý do bác bỏ cũ giờ đứng thế nào:**

- Lý do "máy không có toolchain" **đã hết hiệu lực**. Người dùng tự cài Android SDK
  (`~/.bubblewrap/android_sdk`, build-tools 35 và 36.1), tạo keystore, build và ký APK thật
  trước khi mở phiên ngày 08-08. Phần đắt nhất đã là chi phí chìm.
- Lý do "TWA không thêm khả năng nào" **vẫn đúng nguyên**. Nó không phục vụ luận điểm nào
  trong §10 và không sinh số liệu cho chương 11. Đây là lựa chọn hình thức trình bày, và
  báo cáo phải phát biểu đúng như vậy.

**Chẩn đoán sự cố ngày 08-08.** App cài xong mở ra báo `ERR_NAME_NOT_RESOLVED`. Đã kiểm từng
mắt xích, và **toàn bộ cấu hình TWA đều đúng**:

| Mắt xích | Kết quả kiểm |
|---|---|
| Vân tay chữ ký APK | `acde5a51…9c1d5a` (đọc bằng `apksigner verify --print-certs`) |
| `assetlinks.json` khai | **cùng một chuỗi** — khớp tuyệt đối |
| `SecurityConfig` | `/.well-known/assetlinks.json` đã permit cho cả GET lẫn HEAD |
| Backend phục vụ | `/manifest.webmanifest` và `/.well-known/assetlinks.json` đều trả 200 |

Hỏng đúng **một** chỗ: domain. Tiến trình `cloudflared` **vẫn đang chạy** và vẫn khai hostname
cũ qua endpoint metrics, nhưng Cloudflare đã thu hồi quick tunnel — tra DNS thật qua `1.1.1.1`
trả về `Non-existent domain`.

> ⚠️ **Bẫy cốt lõi, ghi để không lần lại từ đầu: tiến trình tunnel còn sống KHÔNG có nghĩa là
> domain còn sống.** `cloudflared` không tự biết mình đã bị thu hồi. Mọi phép kiểm dựa vào
> "tunnel có chạy không" đều cho kết quả xanh trong khi app đã chết. Phép kiểm đúng duy nhất là
> **phân giải DNS từ ngoài** rồi gọi thật vào domain đó.

**Hệ quả kiến trúc phải chấp nhận:** APK nung cứng domain vào lúc build (`twa-manifest.json`
→ `host`, `webManifestUrl`, `fullScopeUrl`, `iconUrl`). Đổi domain là **phải build lại và cài
lại**. Người dùng chọn giữ quick tunnel miễn phí thay vì mua domain cố định, nên cái vòng đó
lặp lại mỗi lần tunnel chết. `scripts/build-apk.ps1` tự động hoá nó và chặn trước trường hợp
build ra một APK trỏ vào domain đã chết.

**Câu ghi vào báo cáo — thay câu ở phần hướng phát triển bên trên:**

> *Hệ thống được đóng gói thành ứng dụng Android (APK) qua Trusted Web Activity, cài trực tiếp
> lên thiết bị. Về bản chất TWA vẫn dùng engine của Chrome để render giao diện web, nên khả
> năng chức năng tương đương bản PWA; đóng gói APK phục vụ mục đích phân phối và trải nghiệm
> cài đặt, không mở rộng năng lực hệ thống.*

**Đừng viết** "ứng dụng Android native". Hội đồng hỏi một câu về vòng đời Activity hay JNI là
lộ ngay. Nói đúng bản chất TWA thì không ai bắt bẻ được.

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

### 5.1. Không gán cứng dữ liệu trong ứng dụng

**Mọi dữ liệu phải có một nơi lưu thật, và ứng dụng phải đọc ra từ nơi đó.** Không viết thẳng
giá trị vào mã nguồn.

"Dữ liệu" ở đây gồm cả những thứ hay bị coi là vô hại: nhãn hiển thị, danh mục, trần điểm,
ngưỡng xếp loại, quy ước học kỳ, danh sách tổ chức, học kỳ mặc định của một ô nhập.

| Loại | Nơi lưu | Ứng dụng đọc bằng |
|---|---|---|
| Dữ liệu nghiệp vụ | MySQL | repository → API → client sinh từ OpenAPI |
| Quy tắc chấm điểm, trần và ngưỡng | `rulesets/*.json` — băm nguyên văn và neo | `GET /api/scoring/rulesets/{semester}` |
| Cấu hình môi trường | `.env`, `application.yml` | `@Value`, `import.meta.env` |

**Vì sao đây là quy tắc cứng, không phải sở thích.** Ba lý do, nặng dần:

1. **Đổi một con số không được bắt phải deploy lại.** V8 đã viết đúng ý này cho quy ước học kỳ:
   nằm trong code thì đổi lịch học phải sửa mã và deploy; nằm trong một cột thì chỉ là một lệnh
   `UPDATE`.
2. **Hai bản sao của cùng một dữ liệu sẽ trôi khỏi nhau.** Không phải "có thể", mà là sẽ. Và
   chúng trôi lặng lẽ, vì không có gì so hai bản với nhau.
3. **Nặng nhất: giá trị gán cứng nói dối về thứ đã neo.** Bộ quy tắc được băm nguyên văn và neo
   ở miền `RULESET`. Nếu trang điểm vẽ thanh tiến trình theo trần gán cứng trong TypeScript thì
   con số sinh viên nhìn thấy **không còn là con số đã neo** — nó là con số một lập trình viên
   gõ vào, tình cờ đang trùng. Ra bộ quy tắc `v2` với trần khác, trang vẫn vẽ theo trần cũ, và
   không có phép kiểm nào bắt được. Cả luận điểm "điểm này kiểm lại được" sụp ở đúng chỗ đó.

**Ngoại lệ hẹp — hằng số của giao thức, không phải của nghiệp vụ.** `chainId 80002`, nonce 16
byte, tiền tố miền neo, độ dài chữ ký 65 byte. Đổi chúng thì hệ thống thành một hệ thống khác,
nên chúng thuộc về mã nguồn. Ranh giới để phân biệt: *nếu nhà trường có thể muốn đổi giá trị
này mà không cần lập trình viên, nó là dữ liệu.*

**Nợ phát hiện ngày 2026-08-07 — đã trả hết cùng ngày:**

| Chỗ | Gán cứng cái gì | Nguồn thật đang dùng |
|---|---|---|
| `ScorePage.tsx` | `TEN_TIEU_CHI`, `TRAN` (20/25/20/25/10), `XEP_LOAI`, thang 100 | `tieuChi[].ten`, `.toiDa`, `.nguon`, `phanLoai[]`, `thang` |
| `StaffScoring.tsx` | `XEP_LOAI`, thứ tự xếp loại, học kỳ và version mặc định | `phanLoai[]` sắp theo ngưỡng; version lấy từ ruleset của kỳ |
| `CredentialSuggestionService` | đếm điểm danh **không lọc** `events.semester` | cột đã có từ V8 |

Cả ba đọc qua `app/src/lib/ruleset.ts` (client) và `events.semester` (backend).

Dòng thứ ba là **lỗi thật, không phải nợ thẩm mỹ**: nó làm số hoạt động trong credential gộp
mọi học kỳ, mà credential thì ký rồi neo vĩnh viễn.

Hai dòng đầu đáng nhắc lại vì chúng cho thấy cách hỏng đặc trưng: `TRAN = { c1: 20, … }` **trùng
đúng bộ quy tắc** ở thời điểm viết, nên không test nào đỏ và không ai thấy gì sai. Nó chỉ sai
vào ngày ra bản `v2`, và sai lặng lẽ.

**Ngoại lệ đã dùng, ghi ra để lần sau không tranh cãi lại:** nhãn tiếng Việt của mã xếp loại
(`XUAT_SAC` → "Xuất sắc") ở lại trong `lib/ruleset.ts`. Bộ quy tắc chỉ khai `ma`/`tu`/`den`,
không có tên hiển thị; thêm trường `ten` vào tệp là đổi byte của tệp, tức đổi `ruleset_hash`,
tức làm 500 bản ghi điểm đã chấm không tái tạo được. **Ngưỡng** thì vẫn đọc từ `phanLoai`.

---

## 6. Kế hoạch 8 tuần (đã hiệu chỉnh)

Thay đổi lớn nhất so với bản gốc: **spike blockchain ở tuần 1, không phải tuần 3.**

Lý do: toolchain chuỗi (tài khoản RPC, faucet, deploy, verify, sinh wrapper web3j) là phần **rủi ro không biết trước** cao nhất và **hoàn toàn độc lập** với nghiệp vụ. Phát hiện faucet cạn hoặc verify PolygonScan hỏng ở tuần 3 làm vỡ kế hoạch; phát hiện ở tuần 1 chỉ tốn nửa ngày xoay xở.

### Tuần 0 (2–3 ngày, trước khi tính tuần 1)
- [ ] Lấy bản quy chế điểm rèn luyện trường đang áp dụng, xác nhận thang 5 tiêu chí
- [ ] **Test camera QR trên Android thật** (đổi từ iPhone — quyết định 2026-08-05, xem §2.4)
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
> **Trạng thái 2026-08-05:** ✅ 30 endpoint · ✅ sinh client (`scripts/gen-api-client.ps1`, 7 service / 23 model) · ❌ spike chuỗi **chưa làm**.
>
> Chi tiết phần chuỗi: ✅ ví triển khai `0xf32728c5c2D0575ea406Ad37e2467916c89F529F` đã có
> **0.3167 POL** trên Amoy · ✅ `ETHERSCAN_API_KEY` đã kiểm chứng gọi được `chainid=80002`
> (**không còn key riêng cho PolygonScan** — Etherscan hợp nhất thành API V2, V1 tắt từ
> 15/08/2025) · ✅ **`AMOY_RPC_URL` đã trỏ PublicNode và chạy được** (xem §2.2).
>
> **Cập nhật 2026-08-05:** dòng "`AMOY_RPC_URL` vẫn là placeholder → thứ duy nhất còn chặn
> deploy" ở bản trước đã **hết đúng**. Kiểm tra lại từ chính Hardhat: `chainId` = 80002,
> signer = ví trên, số dư 0,3167 POL, `gasPrice` = 30 gwei. Deploy 3 contract tốn ~1,82
> triệu gas ≈ **0,055 POL**, thừa sức. **Không còn gì chặn việc deploy về mặt kỹ thuật.**

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
>
> **Trạng thái 2026-08-05:** ✅ **test vector xanh cả hai phía** — 46 test Java
> (`CanonicalVectorTest`) + 40 test JS (`verifier/test/canonical.test.mjs`), cùng đọc một
> file `backend/src/test/resources/canonical-vectors.json`. Phía Java xanh ngay lần chạy đầu
> với bộ vector do JS sinh, tức hai bên khớp từng byte một cách độc lập.
> Đặc tả chốt: `docs/canonicalization.md`. Đã có `Jcs` · `AnchorDomain` · `LeafHasher`
> trong module `anchor`, chưa import gì từ nghiệp vụ.
>
> ✅ **3 contract xong, 57 test Hardhat xanh, chạy thuần local** (2026-08-05).
> `AnchorRegistry` · `IssuerRegistry` · `StatusList` + `bench/StatusListMapping` (đối chứng
> đo gas, không deploy). Hardhat 3.12 · solc 0.8.28 ghim `evmVersion: cancun` · OZ 5.6.1.
> Phép đo #1 và #2 **đã có số liệu local** — xem `docs/measurements.md` §11.1 và §11.4.
> `contracts/test/AnchorRegistry.test.ts` chốt cứng 5 giá trị `bytes8(domain)` khớp
> `docs/canonicalization.md` §2, nên lệch mã hóa miền ở bất kỳ tầng nào cũng làm test đỏ.
>
> ✅ **Đã deploy Amoy và verify** (2026-08-05, 09:41 UTC) — cả ba contract verify được trên
> **PolygonScan lẫn Sourcify**. Tổng 1.837.575 gas ≈ 0,0551 POL; còn ~0,26 POL trong ví.
> Địa chỉ đã ghi vào `.env` và `contracts/deployments/amoy.json`:
>
> | Contract | Địa chỉ |
> |---|---|
> | `AnchorRegistry` | `0x4aC296Ad010233799bA3B91b8505269213503fAF` |
> | `IssuerRegistry` | `0xD323118Fa310a730BC4202fADd8dfA7CeA4C5637` |
> | `StatusList` | `0xc8538A8741CE428C4A26f3a06678b6Ca10972106` |
>
> ✅ **`MerkleService` + bộ test vector Merkle xong** (2026-08-05). Hai hiện thực **độc lập**
> — phía JS bọc `merkletreejs`, phía Java tự dựng cây — cùng đọc
> `backend/src/test/resources/merkle-vectors.json`. **Java xanh ngay lần chạy đầu** với
> vector do `merkletreejs` sinh. Tổng test tầng canonicalization giờ là
> **Java 118 · JS 115**. Đặc tả: `docs/canonicalization.md` §8.
>
> ✅ **GIAO DỊCH NEO THẬT — cổng tuần 3 đã đóng** (2026-08-06). Lô `ATTEND` `2026080501`,
> 4 bản ghi điểm danh thật, 81.968 gas:
> [`0x1d1ebe…db75`](https://amoy.polygonscan.com/tx/0x1d1ebe0d84320b669fe15243eee4a6a6d58b736cdd204db19ccbc08fa747db75)
>
> Vòng khép kín đã chạy thật: bản ghi MySQL → payload 11 trường → leaf → cây Merkle → giao
> dịch Amoy → đọc lại root bằng **một `eth_call` trên RPC công cộng không key** → **4/4 proof
> xác minh được**, và sửa một byte thì bị từ chối. Chi tiết: `docs/measurements.md` §11.6.
>
> Đây là lần đầu luận điểm 2 có bằng chứng chạy được thay vì là lời hứa.

### Tuần 4 — Neo + credential
Job neo hằng đêm · hash chain audit log · VC + chữ ký issuer · StatusList · xuất bundle JSON.
Nhớ: **nonce trong payload** (§2.3), **status index ngẫu nhiên** (§2.3).

> **Mốc:** bundle credential verify được offline bằng script Node, không chạm backend.
>
> **Trạng thái 2026-08-06 — luồng cấp credential đã xong, mốc thì CHƯA đạt.**
>
> ✅ **Đã có:** entity `Credential` · `CredentialPayload` (miền `CRED`, 11 trường, hợp đồng
> backend↔verifier) · `IssuerSigner` (ECDSA secp256k1 trên leaf, 65 byte có recovery id) ·
> `CredentialService` cấp phát đầy đủ (chụp ảnh → nonce → status index ngẫu nhiên → payload →
> leaf → ký → lưu) · `CredentialAnchorSource` nên job neo đã nhặt được miền `CRED` ·
> `CredentialController` 4 endpoint · Flyway V4 + V5.
>
> **Bộ vector thứ ba** (`cred-signature-vectors.json`): web3j và ethers ký cùng một leaf ra
> **giống hệt từng byte**. Đặc tả: `docs/canonicalization.md` §11 và §12.
> Test: **Java 235 · JS 174**, 0 fail.
>
> ✅ **Bundle JSON xong (2026-08-06).** `CredentialBundleService` + `GET /api/credentials/
> {id}/bundle` phía backend · `verifier/src/bundle.mjs` + `scripts/verify-bundle.mjs` phía
> verifier, **chỉ dùng `ethers`, không gọi backend một dòng nào**. Sáu phép kiểm, bốn trong
> số đó chạy được hoàn toàn offline. Bộ vector thứ tư (`bundle-fixture.json`) chốt bundle
> Java dựng ra **khớp từng trường** với fixture do JS sinh. Đặc tả: `docs/canonicalization.md`
> §13.
>
> Đã chạy thật trên **RPC công cộng không key**: cả ba `eth_call` (`getRoot`,
> `isActiveIssuer`, `isRevoked`) trả lời được từ Amoy.
>
> ## ✅ MỐC TUẦN 4 ĐÃ ĐÓNG — 2026-08-06
>
> Credential #81 của **B21DCCN002**, cấp từ **3 bản ghi điểm danh thật** (15 điểm, 3/3 xác
> minh bằng máy), neo lên Amoy, và **xác minh được 6/6 bằng script Node không chạm backend**.
>
> | Bước | Giao dịch | Gas |
> |---|---|---|
> | Đăng ký issuer vào `IssuerRegistry` | [`0x32c420…5df6`](https://amoy.polygonscan.com/tx/0x32c420366f65c2e67473cbbdb1a3ffd97009aafe89b794eea61ea95ad82a5df6) | 133.590 |
> | Neo lô `CRED` `2026080601` | [`0x0cbaca…e4d6`](https://amoy.polygonscan.com/tx/0x0cbacae962f23e9c56cc8f87a2d46e7f358bcc1ec3c8168aa4aaff032190e4d6) | 81.944 |
>
> Tổng ≈ **0,0089 POL**; ví còn 0,2327 POL. Sửa `totalPoints` 15 → 95 trong bundle làm **ba
> lớp độc lập cùng đỏ** (leaf · chữ ký · Merkle proof). Số liệu đầy đủ:
> `docs/measurements.md` §11.7.
>
> **Bốn hạn chế đã ghi vào §11.7, phải nói ra khi bảo vệ:** lô `CRED` chỉ có **1 lá** nên
> proof rỗng (bước Merkle là trường hợp biên) · khóa issuer **trùng khóa neo** · chưa lọc
> theo học kỳ · `StatusList` chưa nối dây.
>
> ✅ **`StatusList` đã nối dây (2026-08-06).** `StatusListClient` + `CredentialRevocationService`
> + `POST /api/credentials/{id}/revoke`. Kiểm chứng trên **chuỗi Hardhat cục bộ thật**, không
> mock: 14 test (`StatusListClientLocalChainTest` 7 · `CredentialRevocationDbTest` 7).
> Gas đo được: **47.978** khi chạm ô lưu trữ mới, **30.878** khi word đã có bit bật —
> `docs/measurements.md` §11.4.
>
> **Thứ tự ghi NGƯỢC với job neo, và đó là chủ ý:** gửi giao dịch **trước**, ghi CSDL **sau**.
> Nguồn sự thật về thu hồi là bit trên chuỗi, vì đó là thứ **duy nhất verifier đọc**. Ghi CSDL
> trước rồi giao dịch hỏng sẽ làm trang quản trị báo "đã thu hồi" trong khi nhà tuyển dụng
> chạy verifier vẫn thấy còn hiệu lực — hỏng im lặng, đúng chỗ quan trọng nhất. Cách hỏng
> ngược lại (chuỗi xong, CSDL chưa) thì verifier vẫn đúng và `reconcile()` sửa được.
> Cố ý **không có chế độ "thu hồi cục bộ"**.
>
> ✅ **Hash chain `audit_logs` xong (2026-08-06)** — **luận điểm 1 giờ có mã chạy được.**
> `AuditHasher` · `AuditPayload` · `AuditService` · `AuditAnchorSource` + nửa JS
> `verifier/src/audit.mjs`. Bộ vector thứ năm (`audit-chain-vectors.json`) mang một chuỗi 5
> mắt xích **cộng sáu biến thể bị phá cố ý**, mỗi biến thể phải bị từ chối.
> Java 34 · JS 37 test. Đặc tả: `docs/canonicalization.md` §14.
>
> Đã kiểm bằng **sửa/xóa/chèn thẳng bằng SQL trên MySQL thật** — đúng mô hình đe dọa (quản
> trị viên có toàn quyền CSDL).
>
> ⚠️ **Chỗ thua đã được chứng minh bằng test, không giấu:** kẻ tấn công **tính lại toàn bộ
> chuỗi** thì `verifyChain()` lại xanh hoàn toàn. Test `tinhLaiCaChuoiThiKhongBat` tồn tại để
> chốt điều đó. Phát biểu đúng mức cho báo cáo: *chuỗi băm làm việc sửa hồi tố trở nên **tốn
> kém**; việc neo làm nó **bất khả thi** đối với khoảng thời gian đã neo.* Cửa sổ còn giấu
> được = khoảng cách giữa hai lần neo (hiện 24 giờ).
>
> **V7** đổi `audit_logs.before_json`/`after_json` và `rulesets.json_body` sang `LONGTEXT` —
> lần thứ hai dính bẫy kiểu `JSON` của MySQL, lần này chặn cả họ.
>
> ## ✅ LUẬN ĐIỂM 1 ĐÃ ĐÓNG — 2026-08-06
>
> 5 thao tác nghiệp vụ **thật qua HTTP + JWT** (2× điểm danh tay · cấp credential · thu hồi
> và duyệt thiết bị) → 5 mắt xích → neo lô `AUDIT` `2026080601`:
> [`0xe965fb…0a42`](https://amoy.polygonscan.com/tx/0xe965fb7c0e1e5f8de8f329493ceefefa67c1b6970643c51faf0fbfa0878c0a42),
> 81.956 gas. **5/5 proof xác minh được** về root đọc từ Amoy bằng chính mã của verifier.
>
> Cây **5 lá** nên đây là lô đầu tiên trên chuỗi thật có cả trường hợp **nút lẻ bị đẩy lên**
> (proof 1 sibling thay vì 3) — thứ mà hai lô `CRED` 1 lá không kiểm được.
>
> Cùng lượt neo cũng đóng **lô thứ hai** của `ATTEND` và `CRED`, cho **số gas trạng thái ổn
> định trên Amoy lần đầu tiên**: 64.028 và 64.004. Chi phí khởi tạo miền = **17.940 gas**,
> trả một lần mỗi miền. Quy đổi học kỳ tính lại bằng số Amoy: **1,08 POL** thay vì 0,92 —
> số local cũ thấp hơn 17%. Chi tiết: `docs/measurements.md` §11.1 và §11.8.
>
> **Ba hạn chế đã ghi vào §11.8:** cửa sổ 24 giờ giữa hai lần neo vẫn giấu được việc sửa ·
> một bản ghi trong lô có `actor_id` NULL do lỗi đã sửa nhưng không sửa được bản đã neo ·
> nhật ký mới phủ 5 loại sự kiện.
>
> ❌ **Còn lại (tuỳ chọn, giao dịch ghi):** thu hồi thật một credential để có ảnh chụp
> verifier báo "ĐÃ THU HỒI". Đảo ngược được (khác `anchor`) nhưng sự kiện `StatusChanged`
> nằm lại vĩnh viễn.
>
> **Tuần 4 xem như xong.** Việc tiếp theo là tuần 5: rule engine + miền `SCORE`.
>
> ⚠️ **Nợ kỹ thuật phát hiện khi làm phần này:** `attendances` **không chụp ảnh** MSSV —
> `AttendancePayload` đọc qua khóa ngoại. Đổi MSSV làm hỏng mọi proof điểm danh đã neo.
> Chưa sửa (bảng đã có bản ghi đã neo, §9.5 của `docs/canonicalization.md` cấm đụng vào).
> **Phải viết vào phần hạn chế của báo cáo** — chi tiết ở `docs/canonicalization.md` §11.3.

### Tuần 5 — Chấm điểm
Ruleset JSON có version · SpEL evaluator · chạy chấm theo kỳ · `evidence_hash` · neo domain `SCORE` + `RULESET`.

> **Mốc:** chấm 500 sinh viên, mỗi bản ghi có `evidence_hash` tái tính được.
>
> ## ✅ MỐC TUẦN 5 ĐÃ ĐẠT — 2026-08-06
>
> **500 sinh viên trong 0,68–0,90 giây**, `evidence_hash` của **cả 500** tái tính được từ bản
> ghi điểm danh (`ScoringServiceDbTest.chamToanKhoa`). Test: **Java 367 · JS 297**, 0 fail.
>
> `RulesetDoc` + `RuleEvaluator` (SpEL) · `EvidenceHasher` · `ScorePayload` ·
> `RulesetPayload` · `ScoringService` · hai `AnchorSource` mới. **Cả năm miền neo giờ đã có
> payload** — tầng canonicalization đóng hoàn toàn (`docs/canonicalization.md` §15).
>
> **V8** thêm `events.semester` — trả khoản nợ đã ghi hai lần ở §11.7 và javadoc
> `CredentialNowRunner`.
>
> ⚠️ **Ba điều phải nói kèm con số 0,7 giây, nếu không nó là con số rỗng** — chi tiết ở
> `docs/measurements.md` §11.3:
> 1. **Chỉ 4/500 sinh viên có dữ liệu điểm danh.** 496 người còn lại chấm trên bằng chứng
>    rỗng, nên 0,7 giây là **cận dưới**, không phải thời gian chấm một khóa thật.
> 2. **Phân bố xếp loại méo** (`YEU` 499 · `TRUNG_BINH` 1) — hệ quả của thiếu dữ liệu, không
>    phải kết quả đánh giá. Trình bày nó như một phân bố điểm rèn luyện là sai nghiêm trọng.
> 3. **Bộ quy tắc chỉ chấm được 50/100 thang điểm.** 40/100 là mặc định cố định (C2 không có
>    bảng kỷ luật, C4 phần nền), 10/100 của C5 không bao giờ cấp. Điểm cao nhất đạt được là
>    **90**, thấp nhất **40**.
>
> Bộ quy tắc **tự khai** phần này bằng trường `nguon` của từng tiêu chí và mục `hanChe` —
> sinh viên đọc tệp là thấy. Hai con số 50/40 tính từ chính bộ quy tắc và có test chốt, nên
> chúng không lệch được khỏi tệp đang dùng.
>
> ❌ **Còn lại:** vế **thủ công** của phép đo #4 chưa có — cần phỏng vấn 1–2 cán bộ CTSV.
> Việc này người dùng phải tự làm. Và chưa neo lô `SCORE`/`RULESET` nào lên Amoy.

### Tuần 6 — UI + verifier
Hoàn thiện 3 luồng PWA · verifier tĩnh · deploy.

> **Mốc:** end-to-end: điểm danh → credential → điểm → xác minh độc lập. **Quay video luồng này ngay tuần 6**, đừng đợi tuần 8 — nếu tuần 7 có gì hỏng vẫn còn video.
>
> ## ✅ VERIFIER TĨNH XONG — 2026-08-06
>
> Trang chạy trong trình duyệt, **không gọi máy chủ của trường một dòng nào**. Đã thử thật với
> bundle thật, đọc Amoy thật:
>
> | Thả vào | Kết quả |
> |---|---|
> | Bundle thật | **✓ 6/6 — "Xác minh được đầy đủ"** |
> | Sửa `totalPoints` 15 → 95 | **✗ 3 lớp độc lập cùng đỏ** (leaf · chữ ký · proof) |
> | Trỏ contract sang địa chỉ lạ | **✗ dừng ngay sau 2 phép kiểm** |
>
> **Không có bundler** — `PROJECT.md` §4 cấm thêm dependency kể cả devDependency.
> `npm run build:web` chỉ chép tệp; `importmap` trỏ tới bản ESM `ethers` đã ship sẵn.
> `dist/` 1.075 KB, phụ thuộc lúc chạy **chỉ `ethers`**, mở bằng bất kỳ máy chủ tĩnh nào.
> Chi tiết + ba lỗi bắt được khi dựng: `docs/measurements.md` §11.10.
>
> ## ✅ TRANG SINH VIÊN XONG — 2026-08-07
>
> `/sv/diem` và `/sv/chung-nhan`, cộng `ScoringController`. Đã chạy thử trong trình duyệt với
> dữ liệu thật: B21DCCN002 · 54/100 · Trung bình · C1 8/20 · C2 25/25 · C3 6/20 · C4 15/25 ·
> C5 0/10 — số khớp đúng bộ quy tắc và dữ liệu điểm danh.
>
> **Trang điểm cố ý không chỉ hiện con số.** Nó in cả `evidenceHash`, `rulesetHash`, trạng
> thái neo, và một dòng cảnh báo *"50/100 chấm từ dữ liệu; 40/100 là điểm mặc định"* lấy
> thẳng từ bộ quy tắc. Giấu chuyện đó đi là để sinh viên tin một con số mà chính hệ thống
> không đo được.
>
> Trang chứng nhận có nút **Tải bundle** — đường duy nhất để bằng chứng ra khỏi hệ thống.
>
> Đã thêm `operationId` tường minh cho hai controller: sinh lại client làm `DevicesService
> .revoke` biến thành `revoke1` (trùng tên với `CredentialController.revoke`), làm hỏng một
> trang đã chạy được. Sửa gốc thay vì chạy theo tên sinh ra.
>
> ## ✅ TRANG CÁN BỘ XONG — 2026-08-08 (commit `e90f8a0`)
>
> `/cb/cham-diem` và `/cb/chung-nhan`, đều có `RequireRole` cho `STAFF`/`ADMIN`. Trước đó hai
> việc này chỉ gọi được qua API.
>
> Cùng lượt trả một khoản nợ đáng kể: **ba chỗ gán cứng dữ liệu lẽ ra phải đọc từ bộ quy tắc**
> — trần điểm, ngưỡng xếp loại, học kỳ mặc định. Nặng nhất là `CredentialSuggestionService`
> đếm điểm danh **không lọc `events.semester`**, làm số hoạt động trong credential gộp mọi học
> kỳ. Đó là lỗi thật, không phải nợ thẩm mỹ, vì credential ký rồi neo vĩnh viễn. Quy tắc chung
> đã viết thành §5.1.
>
> ❌ **Còn lại của tuần 6:**
> 1. **Quay video end-to-end** — người dùng làm, không code hộ được.
> 2. Deploy verifier lên GitHub Pages / Vercel.
> 3. Neo lô `SCORE`/`RULESET` thật (giao dịch ghi).
> 4. Thu hồi thật một credential trên Amoy — để có ảnh verifier báo "ĐÃ THU HỒI".

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

**✅ Cổng cuối tuần 5 ĐÃ ĐÓNG — 2026-08-06.** Chấm được 500 sinh viên trong 0,7 giây, giữ đủ
**5 tiêu chí** của Thông tư 16/2015 — **không phải rút xuống 3**. Nhưng phải nói đúng mức: giữ
đủ 5 tiêu chí về *cấu trúc*, còn về *nguồn dữ liệu* thì chỉ 2,5 tiêu chí được chấm thật
(C1, C3, và 10/25 của C4). Bộ quy tắc tự khai điều đó thay vì giấu.

**✅ Cổng cuối tuần 3 ĐÃ ĐÓNG — 2026-08-06.** 3 contract deploy + verify trên Amoy · phép đo
#1 và #2 đủ số liệu · `MerkleService` + test vector Merkle xanh cả hai phía · **giao dịch neo
thật trên explorer** và proof xác minh được từ RPC công cộng. **Không phải cắt `StatusList`.**

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
| **Camera iOS trong PWA** | Sinh viên không quét được | **Chấp nhận rủi ro, không kiểm chứng** — demo bằng Android (quyết định 2026-08-05, §2.4). `apple-mobile-web-app-capable` đã bỏ; luồng đảo chiều là phương án cứu. Ghi vào phần hạn chế, phát biểu dẫn theo báo cáo lỗi WebKit chứ không phải đo của mình |
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
