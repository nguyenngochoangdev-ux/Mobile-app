/**
 * Cây Merkle — nửa JavaScript.
 * Nửa Java: `backend/src/main/java/vn/ptit/drl/anchor/MerkleService.java`.
 * Đặc tả chốt: `docs/canonicalization.md` §8.
 *
 * Đây là chỗ lệch Java↔JS **thứ hai**, và nó fail im lặng y hệt chỗ thứ nhất: root vẫn
 * tính ra bình thường, chỉ là không khớp. Bộ vector `merkle-vectors.json` chặn đúng chuyện đó.
 *
 * Ba quy ước (đã đối chiếu bằng thực nghiệm với merkletreejs, không đọc từ tài liệu):
 *
 *   1. `sortPairs: true`  — nút nội bộ = keccak256( min(a,b) || max(a,b) ), so sánh byte
 *      KHÔNG DẤU. Proof do đó không cần mang bit trái/phải.
 *   2. `duplicateOdd: false` — nút lẻ được ĐẨY LÊN nguyên vẹn, không nhân đôi. Bitcoin làm
 *      ngược lại; chọn sai là lệch root ở mọi lô có số lá lẻ.
 *   3. `sortLeaves: false` — giữ nguyên thứ tự lá do backend cấp.
 *
 * Module này chỉ dùng `ethers` + `merkletreejs`, đúng ràng buộc cứng của PROJECT.md §4.
 * Không gọi backend một dòng nào.
 */
import { keccak256, getBytes, hexlify } from 'ethers';
import { MerkleTree } from 'merkletreejs';

/** keccak256 luôn cho 32 byte. */
export const HASH_BYTES = 32;

/** Hàm băm truyền cho merkletreejs — vào Buffer, ra Buffer. */
const keccakBuf = (data) => Buffer.from(getBytes(keccak256(data)));

/**
 * Tùy chọn CHỐT. Đừng sửa từng chỗ gọi — sửa ở đây, rồi chạy lại test vector cả hai phía.
 * Xem `/canonical-hash`.
 */
const TREE_OPTIONS = Object.freeze({
  sortPairs: true,
  duplicateOdd: false,
  sortLeaves: false,
  hashLeaves: false, // lá ĐÃ là hash rồi (leafHash), không băm lại
});

/** Chuẩn hóa lá về Buffer 32 byte và kiểm tra lô, khớp `MerkleService.validated`. */
function validated(leaves) {
  if (!Array.isArray(leaves) || leaves.length === 0) {
    throw new Error('Lô rỗng: không dựng được cây Merkle từ 0 lá.');
  }

  const seen = new Set();
  return leaves.map((leaf, i) => {
    let buf;
    try {
      buf = Buffer.from(getBytes(leaf));
    } catch {
      throw new Error(`Lá thứ ${i} không phải dữ liệu byte hợp lệ.`);
    }
    if (buf.length !== HASH_BYTES) {
      throw new Error(
        `Lá thứ ${i} phải dài đúng ${HASH_BYTES} byte, nhận được ${buf.length} byte`,
      );
    }
    const hex = buf.toString('hex');
    if (seen.has(hex)) {
      // Trùng lá làm bằng chứng nhập nhằng (một proof hợp lệ cho hai vị trí). Với nonce
      // 16 byte bắt buộc trong mọi payload, trùng lá nghĩa là lô chứa bản ghi lặp.
      throw new Error(
        `Lá thứ ${i} trùng với một lá trước đó. Mỗi payload có nonce 16 byte riêng nên` +
          ' trùng lá nghĩa là lô chứa bản ghi lặp — sửa ở tầng gọi.',
      );
    }
    seen.add(hex);
    return buf;
  });
}

/** Dựng cây. Dùng khi cần cả root lẫn nhiều proof; nếu chỉ cần một thứ, dùng hàm dưới. */
export function buildTree(leaves) {
  return new MerkleTree(validated(leaves), keccakBuf, TREE_OPTIONS);
}

/** Merkle root của một lô, dạng hex `0x…`. */
export function merkleRoot(leaves) {
  return hexlify(buildTree(leaves).getRoot());
}

/**
 * Bằng chứng cho lá thứ `index`, dạng mảng hex từ dưới lên gốc.
 *
 * Truyền cả `index` chứ không chỉ giá trị lá: `getProof(leaf)` dò theo giá trị và nhập
 * nhằng khi có lá trùng. Ta đã cấm lá trùng, nhưng dựa vào chỉ số thì vẫn đúng hơn.
 *
 * Lá bị đẩy lên (nút lẻ cuối tầng) không có anh em ở tầng đó, nên proof của nó ngắn hơn
 * proof của lá khác trong cùng cây. Đó là hành vi đúng, không phải lỗi.
 */
export function merkleProof(leaves, index) {
  const buffers = validated(leaves);
  if (!Number.isInteger(index) || index < 0 || index >= buffers.length) {
    throw new Error(`index ngoài phạm vi: ${index}, lô có ${buffers.length} lá`);
  }
  const tree = new MerkleTree(buffers, keccakBuf, TREE_OPTIONS);
  return tree.getProof(buffers[index], index).map((p) => hexlify(p.data));
}

/**
 * Xác minh bằng chứng. Đây là phép tính DUY NHẤT verifier cần chạy để đối chiếu một bản
 * ghi với root đã neo trên chuỗi — cố ý viết tay thay vì gọi `MerkleTree.verify`, để nó
 * đọc được y hệt `MerkleService.verify` phía Java và đối chiếu được từng dòng.
 */
export function verifyProof(leaf, siblings, expectedRoot) {
  let node;
  try {
    node = Buffer.from(getBytes(leaf));
  } catch {
    return false;
  }
  if (node.length !== HASH_BYTES) return false;

  let root;
  try {
    root = Buffer.from(getBytes(expectedRoot));
  } catch {
    return false;
  }
  if (root.length !== HASH_BYTES) return false;

  for (const s of siblings) {
    let sib;
    try {
      sib = Buffer.from(getBytes(s));
    } catch {
      return false;
    }
    if (sib.length !== HASH_BYTES) return false;

    // Buffer.compare so sánh KHÔNG DẤU — khớp Arrays.compareUnsigned phía Java.
    const [first, second] = Buffer.compare(node, sib) <= 0 ? [node, sib] : [sib, node];
    node = keccakBuf(Buffer.concat([first, second]));
  }
  return node.equals(root);
}
