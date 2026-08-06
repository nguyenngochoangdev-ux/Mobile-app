# Đặc tả canonicalization — nguồn sự thật

**Chốt ngày:** 2026-08-05 · cập nhật 2026-08-06 (miền `CRED` + chữ ký issuer)
**Trạng thái:** ✅ xanh cả hai phía — **Java 165 test · JS 174 test**
(leaf hash: 62 + 52 · cây Merkle §8: 72 + 75 · payload CRED §11: 13 + 34 · chữ ký §12: 18)

> Tổng test backend là **235** (0 fail, 12 skip — các test cần chuỗi). Con số ở trên chỉ
> đếm tầng canonicalization.

> Đây là cạm bẫy số 2 trong `CLAUDE.md`: lệch canonicalization làm **mọi Merkle proof
> fail**, và fail **im lặng** — hash vẫn tính ra bình thường, chỉ là không khớp.
> Phát hiện ở tuần 3 mất 2 giờ; phát hiện ở tuần 6 mất 2 ngày.
>
> **Mọi thay đổi ở file này phải đi kèm `/canonical-hash` và chạy lại test CẢ HAI phía.**

---

## 1. Công thức

```
leaf = keccak256( bytes8(domain) || 0x3A || UTF-8( JCS(payload) ) )
```

- `bytes8(domain)` — tên miền dạng ASCII, **căn trái, đệm `0x00` bên phải** cho đủ 8 byte.
  Đúng bằng giá trị truyền vào contract, nên không có chỗ nào để lệch giữa Java, JS và Solidity.
- `0x3A` — dấu `:` ASCII, giữ đúng công thức `PROJECT.md` §2.3.
- `JCS(payload)` — RFC 8785, tập con giới hạn (§4).
- `keccak256` — Keccak-256 của EVM, **không phải** SHA3-256 của NIST. Hai thuật toán khác
  nhau ở phần đệm và cho ra kết quả hoàn toàn khác. Java dùng `org.web3j.crypto.Hash.sha3`
  (tên gọi lịch sử, bên trong là Keccak-256); có test chốt bằng `keccak256("")`.

## 2. Năm miền neo

`ATTEND` · `CRED` · `SCORE` · `AUDIT` · `RULESET`

| Miền | bytes8 |
|---|---|
| `ATTEND` | `0x415454454e440000` |
| `CRED` | `0x4352454400000000` |
| `SCORE` | `0x53434f5245000000` |
| `AUDIT` | `0x4155444954000000` |
| `RULESET` | `0x52554c4553455400` |

Mỗi miền là một cây Merkle riêng và một dòng riêng trong `AnchorRegistry`. **Thêm miền
thứ sáu là đổi lược đồ on-chain** — phải qua `/scope-guard`.

## 3. Nonce — bắt buộc, không có ngoại lệ

Mọi payload được neo phải có trường `nonce`: **hex chữ thường, 16 byte, tiền tố `0x`**
(đúng 34 ký tự). Lưu ở DB cùng bản ghi, cột `nonce BINARY(16)`.

Cả `LeafHasher` (Java) lẫn `leaf.mjs` (JS) đều **từ chối hash** payload không có nonce hợp
lệ. Đây là biện pháp cấu trúc, không phải quy ước — không thể quên.

Lý do (`PROJECT.md` §2.3): không có nonce thì payload nằm trong không gian đoán được
(MSSV vài chục nghìn × eventId vài trăm × thời gian trong một buổi ≈ 10⁸–10⁹ tổ hợp), ai
cầm một leaf hash cũng vét cạn khôi phục được nội dung trong vài giây. Nó vỡ ngay khi sinh
viên xuất bundle, vì **proof chứa hash bản ghi của sinh viên khác**.

## 4. Quy tắc serialize

| # | Quy tắc | Ghi chú |
|---|---|---|
| 1 | Khóa object sắp xếp theo **đơn vị mã UTF-16**, **đệ quy mọi cấp** | `String.compareTo` (Java) và `Array.sort()` (JS) đều đúng thứ tự này |
| 2 | **Mảng giữ nguyên thứ tự** | Chỉ khóa object mới sắp xếp |
| 3 | Không khoảng trắng sau `:` và `,` | |
| 4 | Tiếng Việt có dấu ra **UTF-8 thô** | Không dùng chuỗi thoát u-hex |
| 5 | Thoát: `"` `\` và ký tự < `0x20` | Dùng `\b \t \n \f \r`, còn lại là u-hex **chữ thường** |
| 6 | `null` **giữ nguyên**, không lược bỏ | Trường vắng mặt ≠ trường null → **hai hash khác nhau**. Có chủ ý |
| 7 | Thời gian: **ISO-8601 UTC, độ chính xác giây**, hậu tố `Z` | `2026-08-05T09:30:00Z`. Là chuỗi JSON nên không dính bẫy số |

### Số — chỗ nguy hiểm nhất

Hai ngôn ngữ đổi sang ký hiệu mũ ở ngưỡng khác nhau:

| | Ký hiệu mũ khi |
|---|---|
| JS `Number.prototype.toString` | \|v\| ≥ 1e21 hoặc < 1e-6 |
| Java `Double.toString` | \|v\| ≥ 1e7 hoặc < 1e-3 |

Vùng giao nhau — nơi hai bên **chắc chắn** cho ra cùng chuỗi — là `[1e-3, 1e7)`.

**Quyết định: ngoài vùng này thì NÉM LỖI, không đoán.** Thà vỡ ồn ào lúc chạy còn hơn lệch
hash im lặng. Đây là chỗ cố tình lệch khỏi RFC 8785: JCS đầy đủ chấp nhận ký hiệu mũ, còn
ta chỉ nhận tập con. Ghi vào phần hạn chế của báo cáo.

Quy tắc cụ thể:

- `0` và `-0` → `"0"`
- Số nguyên vẹn (kể cả `Double` như `20.0`) → chữ số nguyên: **`20`, không phải `20.0`**
  — đây là bẫy Java↔JS kinh điển nhất
- `|v| > 2^53−1` → **lỗi** (JS mất chính xác)
- Số thực ngoài `[1e-3, 1e7)` → **lỗi**
- `NaN`, `Infinity` → **lỗi**

### Vì sao không dùng Jackson

`Jcs.java` **cố ý không chạm Jackson**. Jackson tuần tự hóa theo cấu hình —
`@JsonInclude`, `@JsonProperty`, module đăng ký, thứ tự field của POJO — nghĩa là leaf hash
sẽ phụ thuộc vào những thứ đổi được mà không ai để ý, kể cả một lần nâng version. Lớp này
nhận cây giá trị tường minh (`Map`/`List`/`String`/`Boolean`/`Number`/null) và không có
tham số cấu hình nào.

Hệ quả có lợi: tầng canonicalization **miễn nhiễm với việc nâng Jackson 2 → 3** — chính là
rủi ro khiến `PROJECT.md` §2.1 chọn ở lại Spring Boot 3.5.

Tương tự phía JS: verifier bị ràng buộc cứng chỉ được có `ethers` + `merkletreejs`, nên
`jcs.mjs` viết tay ~70 dòng thay vì kéo gói `canonicalize` từ npm.

## 5. Bộ test vector

Có **hai** bộ vector, mỗi bộ một file, cả hai đều do phía JS sinh và cả hai phía cùng đọc:

| Bộ | File | Chốt cái gì | Mục |
|---|---|---|---|
| 1 | `canonical-vectors.json` | **leaf hash** | mục này |
| 2 | `merkle-vectors.json` | **cây Merkle** (root + proof) | §8 |

Phần dưới đây nói về bộ 1.

**Một file duy nhất, hai phía cùng đọc:** `backend/src/test/resources/canonical-vectors.json`

| | |
|---|---|
| Sinh bởi | `verifier/scripts/gen-vectors.mjs` (`cd verifier && npm run gen-vectors`) |
| Test Java | `backend/src/test/java/vn/ptit/drl/anchor/CanonicalVectorTest.java` |
| Test JS | `verifier/test/canonical.test.mjs` |
| Chạy | `./mvnw test -Dtest=CanonicalVectorTest` · `cd verifier && npm test` |

Cố tình dùng chung một file thay vì nhân đôi — nhân đôi thì hai bản sẽ trôi khỏi nhau,
đúng thứ bộ test này sinh ra để chặn. Verifier đọc file trong `backend/` **chỉ lúc test**;
bản build tĩnh không chạm gì tới backend.

**6 vector chuẩn** phủ: số nguyên + số thực cùng payload · timestamp · tiếng Việt có dấu ·
ký tự cần escape · emoji (ngoài BMP) · object lồng nhau · mảng · `null` · giá trị biên
(0, số âm, object rỗng, mảng rỗng, chuỗi rỗng, sát `2^53−1`, hai đầu vùng an toàn) ·
tra tấn thứ tự khóa UTF-16.

**7 trường hợp bắt buộc bị từ chối** — thiếu nonce, nonce sai độ dài, nonce chữ hoa, số
thực quá nhỏ, số thực quá lớn, số nguyên vượt `2^53−1`, miền neo không hợp lệ. Test phần
này quan trọng ngang phần happy path: nó chứng minh hệ thống **vỡ ồn ào** thay vì lệch âm thầm.

> ⚠️ **Test đỏ nghĩa là một trong hai phía sai, KHÔNG phải file vector sai.**
> Đừng chạy `gen-vectors` để "sửa" test đang đỏ — làm thế là xóa mất bằng chứng.
> Chỉ sinh lại khi cố ý đổi đặc tả, và khi đó phải chạy lại cả hai phía.

## 6. Bẫy đã gặp trong lúc dựng

Ghi lại để không mất thời gian lần hai:

1. **`\u` trong comment Java vẫn bị diễn giải.** Bộ tiền xử lý Unicode của Java chạy
   *trước* bộ phân tích từ vựng, nên viết một chuỗi thoát u-hex trong comment cũng làm file
   không biên dịch được (`illegal unicode escape`). Đã tốn một lần build.
2. **`Double.toString(20.0)` cho `"20.0"`, `String(20.0)` của JS cho `"20"`.** Nếu không xử
   lý nhánh số-thực-nguyên-vẹn thì mọi payload có điểm tròn (rất nhiều) sẽ lệch hash.
3. **web3j đặt tên hàm là `sha3` nhưng nó là Keccak-256.** Dùng nhầm `MessageDigest
   .getInstance("SHA3-256")` của JDK là ra hash khác hoàn toàn, và không có gì báo lỗi.
4. **`node --test <thư mục>` lỗi `MODULE_NOT_FOUND` trên Windows.** Dùng `node --test`
   để nó tự dò.

## 7. Việc còn lại của tầng này

- [x] Cột `nonce BINARY(16)` ở **cả năm miền neo** — **xong 2026-08-05**, xem §9
- [x] `MerkleService` trong module `anchor` — **xong 2026-08-05**, xem §8
- [x] Test vector thứ hai cho **Merkle proof** — **xong 2026-08-05**, xem §8
- [x] `status_list_index` cấp **ngẫu nhiên từ pool còn trống** — **xong 2026-08-06**, xem §10
- [x] Payload miền `CRED` + bộ vector `cred-payload-*` — **xong 2026-08-06**, xem §11
- [x] Chữ ký issuer + bộ vector thứ ba — **xong 2026-08-06**, xem §12

Tầng canonicalization đã đóng hết mục. Việc còn lại của dự án nằm ở `PROJECT.md`.

Ba miền neo còn lại chưa có payload: `SCORE` (tuần 5) · `AUDIT` và `RULESET` (chưa xếp lịch).
Mỗi miền là **một hợp đồng mới với verifier**, nên mỗi miền cần một lớp payload phía Java,
một nửa phía JS, và vector riêng — đúng quy trình §11 dưới đây.

---

## 8. Cây Merkle — chỗ lệch Java↔JS thứ hai

**Chốt ngày:** 2026-08-05
**Trạng thái:** ✅ xanh cả hai phía (Java 72 test · JS 75 test)

> Chỗ này fail im lặng y hệt §1: root vẫn tính ra bình thường, chỉ là không khớp.
> Ba quy ước dưới đây **đã đối chiếu bằng thực nghiệm** với `merkletreejs`, không đọc từ
> tài liệu.

| | Java | JS |
|---|---|---|
| Hiện thực | `MerkleService.java` (tự viết) | `verifier/src/merkle.mjs` (bọc `merkletreejs`) |
| Test | `MerkleVectorTest.java` | `verifier/test/merkle.test.mjs` |
| Vector chung | `backend/src/test/resources/merkle-vectors.json` | |
| Sinh vector | `cd verifier && npm run gen-merkle-vectors` | |

Hai bên **hiện thực độc lập**, không phải hai bản sao của cùng một đoạn mã: phía JS dùng
thư viện, phía Java tự dựng cây. Java xanh ngay lần chạy đầu với vector do `merkletreejs`
sinh — nên đây là bằng chứng hai bên khớp thật, không phải khớp vì cùng nguồn.

### 8.1. Ba quy ước

| # | Quy ước | Giá trị chốt | Hỏng thế nào nếu chọn sai |
|---|---|---|---|
| 1 | Cặp anh em **sắp xếp trước khi nối** | `keccak256( min(a,b) ‖ max(a,b) )`, so sánh byte **không dấu** | Proof phải mang bit trái/phải; chọn lệch là lệch mọi root |
| 2 | Nút lẻ **đẩy lên nguyên vẹn** | `duplicateOdd: false` | Bitcoin **nhân đôi** nút cuối. Chọn nhầm ⇒ lệch root ở **mọi lô có số lá lẻ** — khoảng một nửa số lô |
| 3 | Thứ tự lá **giữ nguyên** | `sortLeaves: false` | Sắp xếp lá làm mất thứ tự trong lô, và đổi luôn lá nào bị đẩy lên |

Hệ quả của quy ước 1: **proof không cần bit trái/phải**, nên verifier chỉ là một vòng lặp
`node = keccak256(min(node, sibling) ‖ max(node, sibling))`. Đây cũng đúng quy ước của
OpenZeppelin `MerkleProof`, nên nếu sau này cần xác minh proof **on-chain** thì không phải
đổi gì.

Hai trường hợp biên đã chốt:
- **Cây một lá:** root **chính là lá đó**, không băm thêm vòng nào, proof rỗng.
- **Lá bị đẩy lên** không có anh em ở tầng đó, nên proof của nó **ngắn hơn** proof của lá
  khác trong cùng cây. Đó là hành vi đúng, không phải lỗi.

### 8.2. ⚠️ Bẫy `Arrays.compare` của Java

Kiểu `byte` của Java **có dấu**. `Arrays.compare(byte[], byte[])` dùng `Byte.compare`, nên
nó coi `0xFF` là −1 và xếp **trước** `0x00`. JavaScript (`Buffer.compare`) so sánh **không
dấu**.

Dùng nhầm `Arrays.compare` thay vì **`Arrays.compareUnsigned`** làm đảo thứ tự nối ở mọi cặp
mà đúng một hash bắt đầu bằng byte ≥ `0x80` — tức khoảng **một nửa số cặp** — và không có gì
báo lỗi.

Bộ vector có riêng một cây `bay-so-sanh-co-dau` với hai lá **chọn cố ý** để phân biệt hai
cách so sánh, kèm test khẳng định cặp đó vẫn còn phân biệt được (nếu không thì cây bẫy đã
mất tác dụng và phải sinh lại).

### 8.3. Ba thứ bị từ chối

`MerkleService` và `merkle.mjs` đều **ném lỗi**, không "xử lý mềm":

| Trường hợp | Vì sao từ chối |
|---|---|
| Lô rỗng | Không có cây từ 0 lá. `AnchorRegistry` cũng chặn `leafCount = 0` |
| **Lá trùng** | Bằng chứng trở nên nhập nhằng — một proof hợp lệ cho hai vị trí. Mỗi payload có `nonce` 16 byte riêng nên trùng lá nghĩa là lô chứa **bản ghi lặp**, một lỗi cần vỡ ồn ào |
| Lá sai độ dài | Lá phải đúng 32 byte. Nhận lá khác là nhận cả một lớp lỗi im lặng ở tầng gọi |

### 8.4. Về việc `MerkleService` không nhận `domain`

`PROJECT.md` §5 phác chữ ký là `(List<byte[]> leaves, domain)`. Bản hiện thực **cố ý bỏ
`domain`**: mỗi lá đã là `keccak256(bytes8(domain) ‖ ':' ‖ JCS(payload))` nên miền neo nằm
sẵn trong từng lá. Thêm một tham số mà hàm không dùng tới sẽ gợi ý sai rằng hai cây khác
miền được tách nhau bởi thứ gì đó ngoài chính các lá. Việc tách miền thuộc về `LeafHasher`
và về khóa `(domain, batchId)` của `AnchorRegistry`.

### 8.5. Bộ vector gồm gì

**10 cây** — `n=1` (biên nhỏ nhất) · `n=2` · **`n=3` (nút lẻ, quan trọng nhất)** · `n=4`
(cân bằng) · `n=5` (lẻ ở hai tầng liên tiếp) · `n=7` · `n=8` · `bay-so-sanh-co-dau` ·
`canonical-6-la-that` · `n=100` (cỡ một buổi điểm danh thật).

`canonical-6-la-that` dựng từ **đúng 6 leaf hash của `canonical-vectors.json`** — nối hai bộ
vector lại: nếu tầng leaf hash lệch thì cây cũng lệch, nên bộ này bảo vệ luôn bộ kia.

Phần test **proof phải thất bại khi bị sửa** (sai lá · sai root · đổi một byte trong sibling
· bỏ bớt sibling · dùng proof của lá khác) quan trọng ngang phần happy path: một hàm
`verify` luôn trả `true` cũng làm mọi test root xanh.

---

## 9. Nonce ở tầng cơ sở dữ liệu

**Chốt ngày:** 2026-08-05 · Migration `V2__nonce_cho_moi_mien_neo.sql`, đã chạy thật trên
MySQL 8.4 (Flyway v2, 41,5 s).

### 9.1. Đính chính: "thêm cột nonce vào V2" là mô tả sai

Mục §7 trước đây ghi việc còn lại là *"thêm cột `nonce BINARY(16)`"*. Kiểm tra lại `V1__init`
thì **ba bảng đã có sẵn** `nonce BINARY(16) NOT NULL`: `attendances`, `credentials`, `scores`.

Việc thật sự còn thiếu là **hai miền neo còn lại**:

| Miền | Bảng | Trước V2 |
|---|---|---|
| ATTEND | `attendances` | ✅ có từ V1 |
| CRED | `credentials` | ✅ có từ V1 |
| SCORE | `scores` | ✅ có từ V1 |
| **AUDIT** | `audit_logs` | ❌ **thiếu cả `nonce` lẫn `leaf_hash`** |
| **RULESET** | `rulesets` | ❌ **thiếu cả `nonce` lẫn `leaf_hash`** |

Hệ quả cụ thể: `LeafHasher` và `leaf.mjs` đều **từ chối** hash payload không có nonce hợp lệ,
nên hai miền `AUDIT` và `RULESET` **không sinh được leaf nào**. Không phải chuyện làm cho đẹp.

### 9.2. Nonce của `rulesets` không phải biện pháp riêng tư

Bộ quy tắc chấm điểm là **tài liệu công khai** — sinh viên phải đọc được để tự tính lại điểm.
Nonce ở bảng này tồn tại để `LeafHasher` có **đúng một** đường đi cho cả năm miền, và được
công bố kèm ruleset.

Mở ngoại lệ *"miền này không cần nonce"* là mở nhánh thứ hai trong hàm nhạy cảm nhất của hệ
thống, và ngoại lệ kiểu đó luôn lan ra. Ba dòng chú thích rẻ hơn nhiều so với một nhánh mã.

### 9.3. `audit_logs` có hai hash — dùng nhầm là hỏng

| Cột | Là gì | Dùng để |
|---|---|---|
| `hash` | `keccak(prev_hash ‖ record)` | **Mắt xích** của chuỗi băm. Đứt xích ⇒ phát hiện được việc chèn/sửa quá khứ |
| `leaf_hash` | `keccak(bytes8('AUDIT') ‖ ':' ‖ JCS(payload có nonce))` | **Lá** trong cây Merkle của lô neo. Chứng minh **một** bản ghi cụ thể đã tồn tại |

Hai thứ phục vụ hai mục đích khác nhau và không thay thế nhau được.

Vì sao vẫn cần nonce dù đã có `prev_hash`: `prev_hash` cho entropy ở mọi bản ghi **trừ bản
ghi đầu tiên** (`prev_hash` NULL), và dựa vào nó là dựa vào một tính chất phụ. Ngoài ra
`before_json`/`after_json` chứa dữ liệu cá nhân thật — đây là nhu cầu riêng tư thật sự, khác
với trường hợp `rulesets`.

### 9.4. `NOT NULL` một mình là không đủ

Cách hỏng phổ biến nhất của một cột `BINARY NOT NULL` là **được thêm vào bảng đã có dữ liệu
rồi nhận giá trị mặc định ngầm** — với `BINARY` thì mặc định đó là **toàn byte `0x00`**.

Một nonce toàn `0x00` vẫn thỏa `NOT NULL`, và vẫn khớp regex `^0x[0-9a-f]{32}$` của
`LeafHasher`. Nó **đi lọt qua mọi tầng kiểm tra hiện có** và vô hiệu hóa đúng biện pháp mà
`PROJECT.md` §2.3 đặt ra.

V2 thêm CHECK constraint ở **cả năm bảng**:

```sql
CHECK (nonce <> 0x00000000000000000000000000000000)
```

Đã kiểm chứng bằng cách cố tình chèn nonce toàn `0x00` → `ERROR 3819: Check constraint
'ck_ruleset_nonce_khac_khong' is violated`.

### 9.5. Backfill chỉ an toàn vì chưa neo gì

V2 điền nonce ngẫu nhiên (`RANDOM_BYTES(16)`) cho các dòng cũ. **Đổi nonce của một bản ghi đã
neo sẽ làm mọi Merkle proof của nó fail vĩnh viễn, và fail im lặng.**

Việc này an toàn ở thời điểm chạy vì `anchor_batches` **rỗng** — chưa có giao dịch `anchor()`
nào. Đã kiểm tra trước khi chạy, không phải giả định.

> ⚠️ **Nếu về sau cần migration tương tự trên bảng đã có dữ liệu ĐÃ NEO — đừng chép lại V2.**
> Viết migration chỉ chạm các bản ghi có `leaf_hash IS NULL`.

---

## 10. Cấp `status_list_index` ngẫu nhiên

**Chốt ngày:** 2026-08-06 · `StatusListIndexAllocator` + `StatusListIndexService` trong
`vn.ptit.drl.credential` · 16 test.

### 10.1. Vì sao không cấp tuần tự

Thu hồi credential phát sự kiện `StatusChanged(index)` **lên chuỗi công khai, vĩnh viễn**.
Cấp tuần tự thì bản thân con số đã là một dấu thời gian: index nhỏ = cấp sớm. Ai đối chiếu
thứ tự đó với danh sách sinh viên theo khóa, theo lớp, hay theo thứ tự nhập học là truy ngược
được credential nào của ai — **mà không cần chạm vào máy chủ của trường**.

Đây là lỗ hổng cùng họ với lỗ hổng nonce (§3): dữ liệu không nằm trên chuỗi, nhưng **thứ tự**
thì có, và thứ tự cũng là thông tin.

> ⚠️ **Đừng "đơn giản hóa" thành một bộ đếm.** Nó sẽ chạy đúng, mọi test nghiệp vụ vẫn xanh,
> và biện pháp riêng tư biến mất không dấu vết. Nhóm test `KhongDuocTuanTu` tồn tại riêng để
> chặn chuyện đó — nó kiểm dãy không tăng dần, không bám quanh 0, và trải đều khắp pool.

### 10.2. Thuật toán: bốc ngẫu nhiên rồi thử lại

Bốc chỉ số ngẫu nhiên (`SecureRandom`) trong `[0, poolSize)`, hỏi CSDL xem đã dùng chưa,
trùng thì bốc lại. Tối đa 64 lần rồi ném lỗi.

Với độ đầy `p`, số lần bốc kỳ vọng là `1/(1−p)`. Đã **đo** chứ không chỉ tin công thức: ở độ
đầy 50%, trung bình ~2 lần bốc (test `soLanBocKhopLyThuyet`).

**`SecureRandom`, không phải `java.util.Random`.** `Random` là bộ sinh tuyến tính đồng dư —
chỉ cần vài giá trị đầu ra là khôi phục được trạng thái và đoán được mọi giá trị sau. Dùng
nhầm ở đây làm chỉ số "ngẫu nhiên" trở nên dự đoán được, tức mất sạch tác dụng của §10.1.

**Đã cân nhắc và bỏ phương án hoán vị giả ngẫu nhiên có khóa (Feistel).** Nó cho ánh xạ song
ánh nên không bao giờ trùng và không cần thử lại, nhưng đổi lại phải quản lý **thêm một khóa
bí mật nữa** — và lộ khóa đó là khôi phục được toàn bộ thứ tự cấp phát, đúng thứ cơ chế này
sinh ra để giấu. `PROJECT.md` §2.6 đã ghi việc giữ khóa là điểm yếu; thêm khóa thứ hai để
tiết kiệm vài truy vấn là đánh đổi sai.

### 10.3. Giới hạn có chủ ý: không lấp đầy được 100% pool

Ở ô trống **cuối cùng** của pool `N`, mỗi lần bốc chỉ có xác suất `1/N` trúng — nên trượt cả
64 lần là chuyện thường xuyên. Với pool 64, xác suất trượt là `(63/64)^64 ≈ 36%`.

**Đây là tính chất, không phải lỗi.** Pool được cỡ sao cho độ đầy ở mức vài phần trăm; khi nó
gần đầy ta **muốn** vỡ ồn ào để người vận hành tăng pool. Phương án "chữa" bằng quét tuyến
tính tìm ô trống sẽ tốn một truy vấn CSDL cho mỗi chỉ số (pool mặc định `2^20`) và tệ hơn nữa
là **giấu mất** vấn đề hết chỗ. Có test `khongLapDayDuoc100PhanTram` chốt điều này để một
"sửa lỗi" như thế không lọt vào mà không ai cân nhắc.

`StatusListIndexService` cảnh báo trong log khi độ đầy vượt **50%**.

### 10.4. Chọn `poolSize` — cái núm có hệ quả đo được

`drl.credential.status-list-pool-size`, mặc định **`2^20` = 1.048.576**.

| | Pool **lớn** | Pool **nhỏ** |
|---|---|---|
| Bốc trùng | gần như không | nhiều lên, có ngày hết chỗ |
| Mật độ chỉ số | rải đều | gom cụm hơn |
| Gas thu hồi bitmap | **đắt nhất** — mỗi lần chạm một ô lưu trữ mới | rẻ hơn |

Đầu thứ hai của bảng chính là phát hiện ở `docs/measurements.md` §11.4: bitmap rẻ hơn mapping
**8,47×** khi chỉ số gom cụm nhưng chỉ **1,00×** khi rải đều. Cấp ngẫu nhiên đẩy hệ thống về
phía rải đều — **quyền riêng tư mua bằng gas**, và đây là chỗ đánh đổi đó được quyết.

Mặc định `2^20` với cỡ 50.000 credential cho độ đầy ~5%, số lần bốc kỳ vọng ~1,05.

### 10.5. Ràng buộc UNIQUE mới là trọng tài cuối cùng

`allocate()` trả về chỉ số còn trống **tại thời điểm hỏi**. Hai luồng cấp đồng thời vẫn có thể
bốc trúng cùng một số. Chốt chặn thật là `UNIQUE KEY uk_cred_status_index` trong CSDL — bên
gọi phải sẵn sàng nhận `DataIntegrityViolationException` và cấp lại.

Hệ chạy một instance nên xác suất rất nhỏ, nhưng "rất nhỏ" không phải "không có".

`allocateBatch(n)` tự loại trùng **trong nội bộ lô** — việc mà gọi `allocate()` nhiều lần
không làm được khi các bản ghi chưa kịp ghi xuống CSDL. Dùng khi cấp credential hàng loạt
cuối kỳ (tuần 5, chấm 500 sinh viên).

### 10.6. ~~Còn phải nối dây ở tuần 4~~ — **đã nối, 2026-08-06**

Entity `Credential` và `CredentialService` đã gọi `StatusListIndexService.allocate()`. Chỉ số
được cấp **trước** khi dựng payload, vì nó nằm *trong* payload được neo — xem §11.2.

`CredentialService.allocateIndexAndPersist` thử lại tối đa 5 lần khi đụng
`uk_cred_status_index`, đúng như §10.5 đòi hỏi. Test `chiSoNgauNhien` trong
`CredentialIssueDbTest` cấp 6 credential thật xuống MySQL và chốt rằng dãy chỉ số **không
tăng dần** — nếu ai đó "đơn giản hóa" thành bộ đếm thì test đỏ.

---

## 11. Payload miền `CRED`

**Chốt ngày:** 2026-08-06 · Java 13 test · JS 34 test

| | Java | JS |
|---|---|---|
| Hiện thực | `credential/CredentialPayload.java` | `verifier/src/cred.mjs` |
| Test | `CredentialPayloadVectorTest.java` | `verifier/test/cred.test.mjs` |
| Vector | `canonical-vectors.json`, tiền tố `cred-payload` | |

### 11.1. Mười một trường, trong đó `claims` là object lồng

```
credentialId · type · studentCode · studentName · issuerOrgId · issuerAddress
issuedAt · expiresAt · statusListIndex · claims · nonce
```

`claims` của loại `HOAT_DONG`: `semester` · `activityCount` · `totalPoints`.

Đây là payload đầu tiên có **object lồng**, nên nó là chỗ đầu tiên quy tắc "sắp xếp khóa đệ
quy" (§4 quy tắc 1) thật sự có tác dụng trên đường đi thật. Vector `score-nested` đã chốt quy
tắc đó từ tuần 3 nên rủi ro không mới.

Lược đồ `claims` **tách theo loại**, không dùng một tập trường chung. Tập chung buộc mọi loại
mang trường `null` của các loại khác, mà `null` và trường vắng mặt cho ra hai hash khác nhau
(§4 quy tắc 6) — tức là nhân đôi số cách hỏng.

### 11.2. Hai trường BẮT BUỘC nằm trong payload, không được để ở vỏ bundle

| Trường | Để ngoài leaf thì hỏng thế nào |
|---|---|
| `issuerAddress` | Verifier phục hồi địa chỉ từ chữ ký rồi hỏi `IssuerRegistry`. Nếu địa chỉ chỉ nằm ở vỏ bundle thì sửa nó không phá leaf, và verifier sẽ đi hỏi về **địa chỉ do kẻ tấn công chọn** |
| `statusListIndex` | Nghiêm trọng hơn: người cầm credential **đã bị thu hồi** chỉ cần trỏ sang một bit chưa bật là verifier báo "còn hiệu lực". **Thu hồi trở thành vô nghĩa** |

Cả hai đã có test chống giả mạo ở `cred.test.mjs` nhóm *"Chữ ký — sửa vào là hỏng"*.

### 11.3. ⚠️ Quy tắc CHỤP ẢNH — và một khoản nợ kỹ thuật có thật

Mọi trường đi vào payload phải là **bản sao chốt tại thời điểm cấp**, không phải giá trị đọc
qua khóa ngoại. Lý do:

```
cán bộ sửa tên sinh viên  →  payload đổi  →  leaf hash đổi
→  MỌI Merkle proof đã neo của sinh viên đó FAIL VĨNH VIỄN, và fail IM LẶNG
```

`AnchorRegistry` không cho neo lại `(domain, batchId)` nên **không có đường sửa**.

`credentials` đã theo đúng quy tắc: migration V4 thêm `student_code`, `student_name`,
`issuer_address` làm cột chụp ảnh, và `CredentialPayload.of()` chỉ đọc các cột đó. Test
`ChupAnh.doiSinhVienKhongDoiLeaf` cố tình cho entity `Student` mang giá trị khác hẳn rồi
khẳng định leaf không đổi.

> ### 🔴 Nợ kỹ thuật: `attendances` KHÔNG chụp ảnh
>
> `AttendancePayload.of()` đọc `a.getStudent().getMssv()` **qua khóa ngoại**. Đổi MSSV của
> một sinh viên sẽ làm hỏng mọi proof điểm danh đã neo của họ — kể cả lô `2026080501` đang
> nằm trên Amoy.
>
> **Chưa sửa**, và đây là quyết định có ý thức: MSSV gần như không bao giờ đổi, còn sửa bây
> giờ đòi thêm cột chụp ảnh cho một bảng đã có bản ghi **đã neo** — chính xác là tình huống
> §9.5 cảnh báo không được đụng vào.
>
> **Phải ghi vào phần hạn chế của báo cáo.** Đừng để hội đồng tự tìm ra: đây là loại chi
> tiết mà một người đọc kỹ sẽ hỏi, và trả lời được nó cho thấy hiểu hệ thống mình xây.
> Cách sửa đúng nếu có thời gian: thêm cột chụp ảnh, backfill **chỉ các dòng có
> `leaf_hash IS NULL`**, và để nguyên các dòng đã neo.

### 11.4. Địa chỉ ví: chữ thường, không phải EIP-55

Chốt `0x` + 40 hex **chữ thường**, ở cả ba tầng: `ck_cred_issuer_address` trong CSDL,
`CredentialPayload.requireLowercaseAddress` phía Java, `requireLowercaseAddress` phía JS.

EIP-55 trộn hoa/thường theo hash của chính địa chỉ. Một phía lưu dạng checksum còn phía kia
lưu chữ thường là ra hai chuỗi JCS khác nhau — **cùng họ lỗi với nonce chữ hoa**, thứ bộ
vector đã chặn từ tuần 3.

`ethers.recoverAddress` trả về **dạng checksum**, nên bên gọi phải `.toLowerCase()` trước khi
so sánh. Nếu quên, phép so luôn sai và mọi credential hợp lệ bị báo là giả.

---

## 12. Chữ ký của tổ chức cấp phát

**Chốt ngày:** 2026-08-06 · Java 18 test · JS (trong 34 test của `cred.test.mjs`)

```
sig = ECDSA_secp256k1( leaf )      65 byte: r(32) ‖ s(32) ‖ v(1), v ∈ {27, 28}
```

Ký **thẳng leaf hash**, không băm lại, **không** tiền tố EIP-191.

| | Java | JS |
|---|---|---|
| Hiện thực | `credential/IssuerSigner.java` (web3j `Sign`) | `ethers` `SigningKey` / `recoverAddress` |
| Test | `IssuerSignerVectorTest.java` | `verifier/test/cred.test.mjs` |
| Vector | `cred-signature-vectors.json` | |
| Sinh vector | `cd verifier && npm run gen-cred-sig-vectors` | |

### 12.1. Bộ vector thứ ba — vì sao cần

Hai bộ trước chốt hai phía tính ra cùng một **leaf** và cùng một **cây Merkle**. Bộ này chốt
mắt xích còn lại: hai phía đọc cùng một chữ ký ra **cùng một địa chỉ ví**.

"ECDSA thì ở đâu cũng thế" là sai. Bốn chỗ hai thư viện lệch nhau được, và **cả bốn đều fail
im lặng** — phục hồi địa chỉ luôn trả về *một* địa chỉ hợp lệ chứ không báo lỗi:

1. có băm lại thông điệp trước khi ký không (web3j: cờ `needToHash`)
2. có thêm tiền tố EIP-191 `"\x19Ethereum Signed Message:\n32"` không
3. `v` là 27/28, hay 0/1, hay đã cộng chainId theo EIP-155
4. `s` có chuẩn hóa về nửa dưới đường cong không

Chọn sai bất kỳ cái nào: verifier phục hồi ra địa chỉ rác, hỏi `IssuerRegistry` về địa chỉ
rác, nhận về "không có quyền" — **trông y hệt credential giả**.

**Kết quả đo được:** web3j và ethers cho ra chữ ký **giống hệt từng byte** trên cùng một leaf.
Cả hai dùng `k` tất định theo RFC 6979 và cùng chuẩn hóa `s`. Test `kyRaByteGiongHet` chốt
điều này — mạnh hơn mức cần cho tính đúng đắn (chỉ cần địa chỉ khớp là đủ), nhưng nó bắt được
cả những lệch mà phép kiểm địa chỉ bỏ qua.

### 12.2. ⚠️ KHÔNG phải ES256K của JOSE — và vì sao vẫn là lựa chọn đúng

ES256K trong JOSE là ECDSA secp256k1 với **SHA-256**, chữ ký **64 byte** `r‖s`, **không có
recovery id**. Kiểm chữ ký đó đòi **biết trước khóa công khai** của bên cấp — mà chỗ duy nhất
có nó là máy chủ của trường, và verifier **bị cấm gọi backend một dòng nào** (`PROJECT.md` §4).

Byte `v` phá thế bí: từ `(leaf, sig)` phục hồi thẳng ra **địa chỉ ví**, rồi hỏi
`IssuerRegistry` trên chuỗi xem địa chỉ đó có quyền cấp không. Không cần biết trước khóa nào.
Đây chính là cách hiện thực **luận điểm 3** (`PROJECT.md` §10) — nhiều bên cấp phát, không bên
nào độc quyền sổ cái.

**Đánh đổi phải ghi vào phần hạn chế của báo cáo:** credential không phải JWS/ES256K hợp lệ
theo đúng chữ. Nó là chữ ký secp256k1 kiểu Ethereum — mọi ví và thư viện EVM kiểm được, thư
viện JOSE thì không. Chuyển sang JWS đúng chuẩn là hướng phát triển, và khi đó phải kèm một
cách công bố khóa công khai không phụ thuộc máy chủ trường (`did:web`, hoặc để
`IssuerRegistry` lưu khóa thay vì địa chỉ).

### 12.3. Ký digest thô — vì sao an toàn ở đây

Ký một digest 32 byte tùy ý bằng khóa cũng dùng gửi giao dịch là mẫu nguy hiểm quen thuộc: kẻ
tấn công dụ ký một giá trị hóa ra là hash của một giao dịch.

Ở đây rủi ro bị chặn bằng **cấu trúc**: digest luôn là keccak của tiền ảnh bắt đầu bằng
`bytes8("CRED") ‖ ':'` rồi tới JSON chuẩn tắc, nên không đầu vào nào của bên gọi biến nó
thành hash RLP của một giao dịch. Đây đúng là mẫu EIP-712 dùng.

**Vẫn nên tách khóa issuer khỏi khóa neo.** Lộ khóa neo → neo được root rác nhưng không cấp
được credential giả. Lộ khóa issuer → ngược lại. Gộp một khóa là nhân đôi thiệt hại của một
lần lộ mà không tiết kiệm gì. `IssuerSigner.warnIfSameAsAnchorKey` cảnh báo lúc khởi động.

### 12.4. Chữ ký KHÔNG nằm trong payload — và không cần

Không **thể**, vì chữ ký ký chính leaf; đưa nó vào payload là vòng tròn.

Không **cần**, vì hai thứ chứng minh hai điều khác nhau:

| | Chứng minh gì |
|---|---|
| leaf + proof + root trên chuỗi | Bản ghi **tồn tại từ lúc nào** và **không sửa được** |
| chữ ký | **Ai** phát biểu |

Cái sau không cần cái trước bảo vệ: sửa chữ ký thì nó hết verify.

`revokedAt` cũng **không** neo — trạng thái thu hồi thay đổi được sau khi cấp, còn leaf thì
không. Nguồn sự thật về thu hồi là bit trên `StatusList`, đọc bằng một `eth_call`. Đó chính
là lý do `statusListIndex` **phải** neo (§11.2).

### 12.5. `recoverAddress` KHÔNG phải phép kiểm chữ ký

Hàm này **luôn** trả về một địa chỉ nào đó với bất kỳ chữ ký đúng định dạng nào. Phép kiểm
thật gồm **ba bước, thiếu bước nào cũng hỏng**:

1. leaf tính lại từ payload → verify được về root trên chuỗi qua Merkle proof
2. địa chỉ phục hồi từ chữ ký **khớp** `issuerAddress` trong payload
3. địa chỉ đó **có quyền** trong `IssuerRegistry`

`CredentialService.persistWithProof` chạy bước 2 ngay lúc cấp: nó phục hồi địa chỉ từ chữ ký
vừa tạo và so với payload, ném lỗi nếu lệch. Tốn ~1 ms mỗi lần cấp; đổi lại một credential ký
sai **không bao giờ** ra khỏi hàm đó. Nếu để lọt, chỗ phát hiện tiếp theo là nhà tuyển dụng
bấm "xác minh" và thấy đỏ — muộn nhất có thể.
