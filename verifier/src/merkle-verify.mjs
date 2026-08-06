/**
 * Xác minh Merkle proof — **phép tính DUY NHẤT verifier cần chạy**.
 * Nửa Java: `MerkleService.verify`. Đặc tả: `docs/canonicalization.md` §8.
 *
 * ## Vì sao tách khỏi `merkle.mjs`
 *
 * `merkle.mjs` import `merkletreejs` để **dựng** cây (root, proof) — việc của backend, chạy
 * trong Node. Verifier trong **trình duyệt** chỉ cần **xác minh**, và kéo theo `merkletreejs`
 * sẽ mang cả một gói CJS phụ thuộc `Buffer` vào một nơi không có `Buffer`.
 *
 * Tách ra làm trang tĩnh chỉ phụ thuộc **đúng một thư viện: `ethers`**. Ít phụ thuộc hơn ở
 * chỗ quan trọng nhất — nơi nhà tuyển dụng chạy mã mà không có ai bảo đảm cho họ.
 *
 * ## Không dùng `Buffer` — và phép so KHÔNG DẤU trở nên tường minh
 *
 * Bản trước dùng `Buffer.compare`, vốn so sánh **không dấu**. Đúng, nhưng tính chất đó nằm
 * ngầm trong hành vi của `Buffer`, và đây chính là chỗ Java từng suýt sai: `Arrays.compare`
 * của Java so sánh **có dấu** và làm lệch root ở khoảng một nửa số cặp
 * (`docs/canonicalization.md` §8.2).
 *
 * Viết tay `soSanhKhongDau` trên `Uint8Array` vừa chạy được trong trình duyệt, vừa làm cái
 * quy ước quan trọng nhất của cây hiện ra thành một hàm có tên.
 */
import { keccak256, getBytes, concat } from 'ethers';

/** keccak256 luôn cho 32 byte. */
export const HASH_BYTES = 32;

/**
 * So sánh hai mảng byte **KHÔNG DẤU**, theo thứ tự từ điển.
 *
 * <p>Đây là quy ước 1 của cây: nút nội bộ là `keccak256( min(a,b) ‖ max(a,b) )`. Chọn sai
 * kiểu so sánh là lệch root ở mọi cặp mà đúng một hash bắt đầu bằng byte ≥ `0x80` — khoảng
 * một nửa số cặp — và **không có gì báo lỗi**.
 */
export function soSanhKhongDau(a, b) {
  const n = Math.min(a.length, b.length);
  for (let i = 0; i < n; i++) {
    if (a[i] !== b[i]) {
      return a[i] < b[i] ? -1 : 1;
    }
  }
  return a.length - b.length;
}

/** Đọc một giá trị thành `Uint8Array` 32 byte, hoặc `null` nếu không hợp lệ. */
function hash32(v) {
  let b;
  try {
    b = getBytes(v);
  } catch {
    return null;
  }
  return b.length === HASH_BYTES ? b : null;
}

/**
 * Xác minh bằng chứng.
 *
 * <p>Cố ý viết tay thay vì gọi `MerkleTree.verify`, để nó đọc được y hệt
 * `MerkleService.verify` phía Java và đối chiếu được từng dòng.
 *
 * <p>Trả `false` thay vì ném lỗi với mọi đầu vào hỏng: đây là hàm nhận dữ liệu từ **tệp của
 * người dùng**, và "bằng chứng không hợp lệ" là một câu trả lời, không phải một sự cố.
 */
export function verifyProof(leaf, siblings, expectedRoot) {
  let node = hash32(leaf);
  if (node === null) return false;

  const root = hash32(expectedRoot);
  if (root === null) return false;

  if (!Array.isArray(siblings)) return false;

  for (const s of siblings) {
    const sib = hash32(s);
    if (sib === null) return false;

    const [first, second] = soSanhKhongDau(node, sib) <= 0 ? [node, sib] : [sib, node];
    node = getBytes(keccak256(concat([first, second])));
  }

  return soSanhKhongDau(node, root) === 0;
}
