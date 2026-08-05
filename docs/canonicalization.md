# Đặc tả canonicalization — nguồn sự thật

**Chốt ngày:** 2026-08-05
**Trạng thái:** ✅ xanh cả hai phía — **Java 118 test · JS 115 test**
(leaf hash: 46 + 40 · cây Merkle §8: 72 + 75)

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

- [ ] Cột `nonce BINARY(16)` vào các bảng được neo (migration Flyway `V2`)
- [x] `MerkleService` trong module `anchor` — **xong 2026-08-05**, xem §8
- [x] Test vector thứ hai cho **Merkle proof** — **xong 2026-08-05**, xem §8
- [ ] `status_list_index` cấp **ngẫu nhiên từ pool còn trống**, không tuần tự (`PROJECT.md` §2.3)

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
