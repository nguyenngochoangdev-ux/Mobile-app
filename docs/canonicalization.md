# Đặc tả canonicalization — nguồn sự thật

**Chốt ngày:** 2026-08-05
**Trạng thái:** ✅ xanh cả hai phía (Java 46 test · JS 40 test)

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
- [ ] `MerkleService` trong module `anchor` — nhận `List<byte[]> leaves` + `domain`, trả
      root và proof; **không import gì từ nghiệp vụ** (`PROJECT.md` §5)
- [ ] Test vector thứ hai cho **Merkle proof** (không chỉ leaf): cùng một tập leaf phải cho
      cùng root ở Java và JS — chú ý quy ước sắp xếp cặp anh em và xử lý nút lẻ của
      `merkletreejs`
- [ ] `status_list_index` cấp **ngẫu nhiên từ pool còn trống**, không tuần tự (`PROJECT.md` §2.3)
