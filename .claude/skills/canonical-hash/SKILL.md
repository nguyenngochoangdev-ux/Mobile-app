---
name: canonical-hash
description: Bảo vệ tính nhất quán của công thức leaf hash giữa backend Java và verifier JavaScript. Dùng BẮT BUỘC khi động vào bất cứ thứ gì ảnh hưởng leaf hash — payload được neo, serializer JSON, MerkleService, code verifier, thêm/đổi trường của bản ghi được neo, hoặc nâng version Jackson/ethers.
---

# Canonical hash — cạm bẫy lớn nhất của đề tài

Công thức leaf hash phải cho ra **byte giống hệt nhau** ở backend Java và verifier JavaScript:

```
leaf = keccak256( domain || ":" || JCS({...payload, nonce}) )
```

Lệch một dấu cách, một chữ số thập phân, một thứ tự field → **mọi Merkle proof fail**. Và nó fail *im lặng*: hash vẫn tính ra, chỉ là không khớp. Phát hiện ở tuần 3 mất 2 giờ; phát hiện ở tuần 6 mất 2 ngày.

## Ba quy tắc serialize bất khả xâm phạm

Dùng RFC 8785 (JSON Canonicalization Scheme), hoặc tự viết serializer tuân thủ đúng:

1. **Field sắp xếp theo thứ tự alphabet** — ở mọi cấp lồng nhau, không chỉ cấp ngoài cùng
2. **Số nguyên không có dấu chấm thập phân** — `5` không phải `5.0`. Cẩn thận với `Double`/`BigDecimal` trong Java và `number` trong JS
3. **Thời gian ở ISO-8601 UTC** — cùng độ chính xác hai phía. Chốt mili giây hay giây và ghi vào code comment

Thêm hai điểm hay bị bỏ sót:
4. **Không có khoảng trắng thừa** — không space sau `:` hay `,`
5. **Unicode escape thống nhất** — tên sinh viên có dấu tiếng Việt. Chốt UTF-8 raw, không `\uXXXX` escape, ở cả hai phía

## Bắt buộc: bộ test vector

File `backend/src/test/resources/canonical-vectors.json` — 5 payload mẫu kèm leaf hash kỳ vọng. Chạy qua **cả** phía Java và phía JS.

Năm payload phải phủ:
- Tên có dấu tiếng Việt (`Nguyễn Ngọc Hoàng`)
- Số nguyên và số thực trong cùng payload
- Timestamp
- Object lồng nhau (kiểm tra sort đệ quy)
- Field null hoặc thiếu

Khi skill này được gọi:
1. Nếu test vector chưa tồn tại → **viết nó trước**, trước mọi thứ khác
2. Nếu đã tồn tại → chạy cả hai phía, xác nhận xanh, rồi mới sửa code
3. Sau khi sửa → chạy lại cả hai phía. Không được coi là xong nếu chỉ chạy một phía

## Nonce — không được quên

Mọi payload được neo phải có trường `nonce` ngẫu nhiên 16 byte, lưu ở DB cùng bản ghi.

Không có nonce, payload nằm trong không gian đoán được (MSSV vài chục nghìn, eventId vài trăm, thời gian trong một buổi) — ai cầm một leaf hash có thể vét cạn khôi phục nội dung trong vài giây. Điều này vỡ khi sinh viên xuất bundle: proof chứa sibling hash, tức hash bản ghi **của sinh viên khác**.

Xem §2.3 của `PROJECT.md`.

## Domain — năm giá trị, không tự thêm

`"ATTEND"` · `"CRED"` · `"SCORE"` · `"AUDIT"` · `"RULESET"`

Kiểu `bytes8` trong Solidity. Padding phải giống nhau hai phía — chốt right-padded với `0x00` và ghi vào test vector.

## Checklist trước khi coi là xong

- [ ] Test vector xanh ở phía Java
- [ ] Test vector xanh ở phía JS (verifier)
- [ ] Hai bên cho ra hash **bằng nhau từng byte**, không phải "trông giống nhau"
- [ ] Payload mới (nếu có) đã thêm vào test vector
- [ ] Trường `nonce` có mặt trong payload
- [ ] Verifier vẫn không import gì từ backend — kiểm tra `verifier/package.json` chỉ có `ethers` và `merkletreejs`
