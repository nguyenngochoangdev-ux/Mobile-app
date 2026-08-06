/**
 * Công thức leaf hash — nửa JavaScript.
 * Nửa Java: `backend/src/main/java/vn/ptit/drl/anchor/LeafHasher.java`.
 *
 *   leaf = keccak256( bytes8(domain) || 0x3A || UTF-8(JCS(payload)) )
 *
 * `0x3A` là dấu ':' trong ASCII, giữ đúng công thức của PROJECT.md §2.3.
 * Đặc tả đầy đủ: `docs/canonicalization.md`.
 */
import { keccak256, concat, toUtf8Bytes, getBytes } from 'ethers';
import { canonicalize } from './jcs.mjs';

/**
 * Năm miền neo. KHÔNG tự thêm giá trị thứ sáu — mỗi miền là một cây Merkle riêng
 * và một dòng riêng trong `AnchorRegistry`.
 */
export const DOMAINS = Object.freeze(['ATTEND', 'CRED', 'SCORE', 'AUDIT', 'RULESET']);

const SEPARATOR = 0x3a; // ':'
const NONCE_HEX_LENGTH = 34; // '0x' + 32 chữ số hex = 16 byte
const NONCE_PATTERN = /^0x[0-9a-f]{32}$/;

/**
 * Mã hóa tên miền thành 8 byte, **căn trái, đệm 0x00 bên phải** — khớp đúng kiểu
 * `bytes8` trong Solidity. Cùng một giá trị byte được dùng cả trong tiền ảnh hash
 * lẫn trong tham số gọi contract, nên không có chỗ nào để lệch.
 */
export function domainBytes8(domain) {
  if (!DOMAINS.includes(domain)) {
    throw new Error(`Miền neo không hợp lệ: "${domain}". Chỉ có: ${DOMAINS.join(', ')}`);
  }
  const out = new Uint8Array(8); // đã là 0x00 sẵn
  const ascii = toUtf8Bytes(domain);
  out.set(ascii, 0);
  return out;
}

/**
 * Kiểm tra `nonce` trước khi hash.
 *
 * Đây là biện pháp cấu trúc cho lỗ hổng ở PROJECT.md §2.3: nếu không có nonce,
 * payload nằm trong không gian đoán được (MSSV vài chục nghìn × eventId vài trăm ×
 * thời gian trong một buổi ≈ 10^8–10^9 tổ hợp) và ai cầm một leaf hash đều vét cạn
 * khôi phục được nội dung. Nó vỡ ngay khi sinh viên xuất bundle, vì proof chứa
 * hash bản ghi CỦA SINH VIÊN KHÁC.
 *
 * Bắt buộc ở đây thay vì "nhớ thêm vào" khiến không thể quên.
 */
function requireNonce(payload) {
  const n = payload?.nonce;
  if (typeof n !== 'string' || n.length !== NONCE_HEX_LENGTH || !NONCE_PATTERN.test(n)) {
    throw new Error(
      'Payload thiếu `nonce` hợp lệ. Yêu cầu: chuỗi hex 16 byte, chữ thường, ' +
        'tiền tố "0x" (34 ký tự). Xem PROJECT.md §2.3.',
    );
  }
}

/** Tiền ảnh của leaf hash — tách riêng để test vector đối chiếu được từng byte. */
export function leafPreimage(domain, payload) {
  requireNonce(payload);
  return concat([domainBytes8(domain), new Uint8Array([SEPARATOR]), toUtf8Bytes(canonicalize(payload))]);
}

/** Trả về leaf hash dạng chuỗi hex `0x…` (32 byte). */
export function leafHash(domain, payload) {
  return keccak256(leafPreimage(domain, payload));
}

// `leafBytes()` — trả về `Buffer` để đưa thẳng vào `merkletreejs` — ĐÃ BỎ.
//
// Không nơi nào gọi (đã grep toàn repo), nhưng nó kéo `Buffer` — một global của Node — vào
// module nằm trên đường xác minh của trình duyệt. Mã chết mang theo phụ thuộc là loại mã tệ
// nhất: không ai đọc nó, và nó vẫn quyết định module này chạy được ở đâu.
//
// Phát hiện bằng phép kiểm trong `scripts/build-web.mjs`, không phải bằng mắt.
