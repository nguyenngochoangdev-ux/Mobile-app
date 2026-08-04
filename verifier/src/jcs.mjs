/**
 * JCS — JSON Canonicalization Scheme (RFC 8785), tập con giới hạn.
 *
 * Đây là NỬA JAVASCRIPT của cạm bẫy số 2 trong CLAUDE.md. Nửa còn lại là
 * `backend/src/main/java/vn/ptit/drl/anchor/Jcs.java`. Hai file phải sinh ra
 * BYTE GIỐNG HỆT NHAU. Sửa một bên mà không sửa bên kia = mọi Merkle proof fail,
 * và fail im lặng.
 *
 * Đặc tả chốt: `docs/canonicalization.md`. Đọc trước khi sửa.
 *
 * Vì sao viết tay thay vì dùng gói `canonicalize` trên npm: verifier bị ràng buộc
 * cứng chỉ được có `ethers` + `merkletreejs` (PROJECT.md §4). Đổi lại ~70 dòng này.
 */

/** Giới hạn số nguyên an toàn của JS — vượt ngưỡng là mất chính xác. */
const MAX_SAFE = Number.MAX_SAFE_INTEGER; // 2^53 - 1

/**
 * Ngưỡng an toàn cho số thực. Xem docs/canonicalization.md §4.
 *
 * JS chuyển sang ký hiệu mũ khi |v| >= 1e21 hoặc < 1e-6; Java (Double.toString)
 * chuyển khi |v| >= 1e7 hoặc < 1e-3. Vùng giao nhau — nơi hai ngôn ngữ chắc chắn
 * cho ra cùng chuỗi — là [1e-3, 1e7). Ngoài vùng này ta NÉM LỖI thay vì đoán.
 * Thà vỡ ồn ào lúc chạy còn hơn lệch hash im lặng.
 */
const MIN_ABS_FRACTIONAL = 1e-3;
const MAX_ABS_FRACTIONAL = 1e7;

/** Chuỗi thoát ngắn cho các ký tự điều khiển, theo RFC 8785 §3.2.2.2. */
const SHORT_ESCAPES = {
  0x08: '\\b',
  0x09: '\\t',
  0x0a: '\\n',
  0x0c: '\\f',
  0x0d: '\\r',
};

function serializeString(s) {
  let out = '"';
  for (let i = 0; i < s.length; i++) {
    const c = s.charCodeAt(i);
    if (c === 0x22) out += '\\"';
    else if (c === 0x5c) out += '\\\\';
    else if (c < 0x20) out += SHORT_ESCAPES[c] ?? '\\u' + c.toString(16).padStart(4, '0');
    else if (c >= 0xd800 && c <= 0xdfff) {
      // Cặp thay thế (surrogate pair) hợp lệ thì giữ nguyên cả hai đơn vị mã.
      const isHigh = c <= 0xdbff;
      const next = i + 1 < s.length ? s.charCodeAt(i + 1) : 0;
      if (isHigh && next >= 0xdc00 && next <= 0xdfff) {
        out += s[i] + s[i + 1];
        i++;
      } else {
        // Surrogate lẻ: JSON.stringify của ES2019 sẽ escape, Java thì không.
        // Không thống nhất được → cấm. Dữ liệu thật không bao giờ có ký tự này.
        throw new Error(`JCS: chuỗi chứa surrogate lẻ tại vị trí ${i} — không neo được`);
      }
    } else out += s[i];
  }
  return out + '"';
}

function serializeNumber(n) {
  if (typeof n !== 'number' || !Number.isFinite(n)) {
    throw new Error(`JCS: số không hữu hạn (${n}) — không neo được`);
  }
  if (n === 0) return '0'; // gộp cả -0 về "0", theo RFC 8785
  if (Number.isInteger(n)) {
    if (Math.abs(n) > MAX_SAFE) {
      throw new Error(`JCS: số nguyên ${n} vượt Number.MAX_SAFE_INTEGER — không neo được`);
    }
    return String(n);
  }
  const abs = Math.abs(n);
  if (abs < MIN_ABS_FRACTIONAL || abs >= MAX_ABS_FRACTIONAL) {
    throw new Error(
      `JCS: số thực ${n} nằm ngoài vùng an toàn [1e-3, 1e7) — Java và JS sẽ ` +
        `sinh chuỗi khác nhau. Xem docs/canonicalization.md §4.`,
    );
  }
  return String(n);
}

/**
 * Trả về chuỗi JSON chuẩn tắc của `value`.
 *
 * Khóa của object sắp xếp theo thứ tự **đơn vị mã UTF-16** ở mọi cấp lồng nhau —
 * `Array.prototype.sort()` mặc định của JS và `String.compareTo` của Java đều
 * dùng đúng thứ tự này, nên hai phía khớp nhau tự nhiên.
 */
export function canonicalize(value) {
  if (value === null) return 'null';
  const t = typeof value;
  if (t === 'boolean') return value ? 'true' : 'false';
  if (t === 'number') return serializeNumber(value);
  if (t === 'string') return serializeString(value);
  if (Array.isArray(value)) {
    return '[' + value.map(canonicalize).join(',') + ']';
  }
  if (t === 'object') {
    const keys = Object.keys(value).sort();
    return '{' + keys.map((k) => serializeString(k) + ':' + canonicalize(value[k])).join(',') + '}';
  }
  throw new Error(`JCS: kiểu không hỗ trợ (${t}) — không neo được`);
}
