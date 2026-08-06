/**
 * Nhật ký có chuỗi băm — nửa JavaScript.
 * Nửa Java: `backend/.../audit/AuditHasher.java` và `AuditPayload.java`.
 *
 * Hai công thức, hai mục đích khác nhau — đừng dùng nhầm:
 *
 *   hash = keccak256( prevHash(32 byte) ‖ UTF-8(JCS(record)) )     MẮT XÍCH của chuỗi
 *   leaf = keccak256( bytes8('AUDIT') ‖ ':' ‖ UTF-8(JCS(payload)) ) LÁ trong cây Merkle
 *
 * Mắt xích chứng minh **không ai chèn/xóa/sửa bản ghi quá khứ**. Lá chứng minh **một bản ghi
 * cụ thể đã tồn tại lúc lô được neo**. Cần cả hai: chuỗi băm một mình vẫn tính lại được bởi
 * người có toàn quyền CSDL, còn root đã lên chuỗi công khai thì không.
 *
 * Test vector chung: `canonical-vectors.json` (tiền tố `audit-payload`) và
 * `audit-chain-vectors.json`.
 * **Sửa file này phải đi kèm `/canonical-hash` và chạy lại test CẢ HAI phía.**
 */
import { keccak256, toUtf8Bytes, getBytes, concat } from 'ethers';

import { canonicalize } from './jcs.mjs';
import { leafHash } from './leaf.mjs';

/** Dùng thay cho `prevHash` null ở bản ghi đầu tiên — 32 byte 0x00. */
export const GENESIS_PREV_HASH = '0x' + '00'.repeat(32);

/** Đúng 11 trường. Danh sách này là đặc tả — dùng để TỪ CHỐI bản ghi sai tập trường. */
export const AUDIT_FIELDS = Object.freeze([
  'action',
  'actorId',
  'afterHash',
  'at',
  'beforeHash',
  'entity',
  'entityId',
  'hash',
  'nonce',
  'prevHash',
  'seq',
]);

/**
 * `keccak256` của **chính byte UTF-8** của một chuỗi JSON, hoặc `null`.
 *
 * Không phân tích, không canonical hóa. `before_json`/`after_json` là JSON tuỳ ý do tầng
 * nghiệp vụ sinh; đưa chúng qua JCS nghĩa là một giá trị nằm ngoài tập con mà JCS chấp nhận
 * sẽ làm **ném lỗi giữa lúc ghi nhật ký** — một thao tác nghiệp vụ bình thường bị chặn bởi
 * tầng ghi log. Băm byte thô thì không có gì để từ chối, mà vẫn cam kết đúng nội dung.
 */
export function hashOfJson(json) {
  return json === null || json === undefined ? null : keccak256(toUtf8Bytes(json));
}

/**
 * Cây giá trị được băm để ra mắt xích. **Không chứa `prevHash`** — nó nối vào *trước* chuỗi
 * JSON dưới dạng 32 byte thô. Cũng không chứa `nonce`: nonce phục vụ lá Merkle.
 */
export function chainRecord(p) {
  return {
    action: p.action,
    actorId: p.actorId,
    afterHash: p.afterHash,
    at: p.at,
    beforeHash: p.beforeHash,
    entity: p.entity,
    entityId: p.entityId,
  };
}

/** Mắt xích, tính lại từ payload đã neo. */
export function chainHash(p) {
  const prev = p.prevHash === null || p.prevHash === undefined ? GENESIS_PREV_HASH : p.prevHash;
  const prevBytes = getBytes(prev);
  if (prevBytes.length !== 32) {
    throw new Error(`prevHash phải dài đúng 32 byte, nhận được ${prevBytes.length}.`);
  }
  return keccak256(concat([prevBytes, toUtf8Bytes(canonicalize(chainRecord(p)))]));
}

/** Kiểm tra và chuẩn hoá payload AUDIT. Nghiêm ngặt có chủ ý — xem `cred.mjs`. */
export function normalizeAuditPayload(raw) {
  if (raw === null || typeof raw !== 'object' || Array.isArray(raw)) {
    throw new Error('Payload AUDIT phải là một object.');
  }
  const keys = Object.keys(raw).sort();
  const expected = [...AUDIT_FIELDS].sort();
  if (keys.length !== expected.length || keys.some((k, i) => k !== expected[i])) {
    const thua = keys.filter((k) => !expected.includes(k));
    const thieu = expected.filter((k) => !keys.includes(k));
    throw new Error(
      'Payload AUDIT sai tập trường.' +
        (thieu.length ? ` Thiếu: ${thieu.join(', ')}.` : '') +
        (thua.length ? ` Thừa: ${thua.join(', ')}.` : ''),
    );
  }

  requireInteger(raw, 'seq');
  requireString(raw, 'action');
  requireString(raw, 'entity');
  requireNullableInteger(raw, 'entityId');
  requireNullableInteger(raw, 'actorId');
  requireIsoSeconds(raw, 'at');
  requireNullableHash32(raw, 'beforeHash');
  requireNullableHash32(raw, 'afterHash');
  requireNullableHash32(raw, 'prevHash');
  requireHash32(raw, 'hash');
  requireString(raw, 'nonce');

  return raw;
}

/** Leaf hash của một bản ghi nhật ký lấy từ lô đã neo. */
export function auditLeafHash(raw) {
  return leafHash('AUDIT', normalizeAuditPayload(raw));
}

/**
 * Kiểm một đoạn chuỗi liên tiếp — **đây là phép kiểm có giá trị nhất của cả file**.
 *
 * Ba thứ được kiểm ở mỗi mắt xích, mỗi thứ bắt một kiểu tấn công:
 *
 *   1. `hash` tính lại có khớp không     → bắt việc SỬA NỘI DUNG một bản ghi
 *   2. `prevHash` có bằng `hash` của bản ghi liền trước không → bắt việc CHÈN hoặc XÓA
 *   3. chỉ phần tử đầu mới được `prevHash` null → bắt việc cắt chuỗi rồi bắt đầu lại
 *
 * Người kiểm toán cầm các payload **đã neo** chạy hàm này là chứng minh được chuỗi liền lạc
 * mà **không cần tin CSDL của trường một chút nào** — mỗi payload đã được cây Merkle và root
 * trên chuỗi công khai bảo chứng.
 *
 * @param payloads mảng payload AUDIT, **theo đúng thứ tự `seq` tăng dần**
 * @param laDauChuoi phần tử đầu tiên có phải bản ghi đầu tiên của CẢ chuỗi không
 */
export function verifyChain(payloads, laDauChuoi = true) {
  const loi = [];
  let mongDoiPrev = null;

  payloads.forEach((p, i) => {
    try {
      normalizeAuditPayload(p);
    } catch (e) {
      loi.push(`#${p?.seq ?? i}: payload không hợp lệ — ${e.message}`);
      return;
    }

    if (i === 0) {
      if (laDauChuoi && p.prevHash !== null) {
        loi.push(
          `#${p.seq}: là bản ghi đầu chuỗi nhưng prevHash khác null —` +
            ' có bản ghi nào đó trước nó đã bị xóa.',
        );
      }
    } else if (p.prevHash !== mongDoiPrev) {
      loi.push(
        `ĐỨT XÍCH tại #${p.seq}: prevHash = ${p.prevHash} nhưng bản ghi liền trước có` +
          ` hash = ${mongDoiPrev}. Có bản ghi bị chèn vào, bị xóa, hoặc bị sửa.`,
      );
    }

    const tinhLai = chainHash(p);
    if (tinhLai !== p.hash) {
      loi.push(
        `NỘI DUNG BỊ SỬA tại #${p.seq}: hash lưu = ${p.hash} nhưng tính lại ra ${tinhLai}.`,
      );
    }

    if (i > 0 && p.seq <= payloads[i - 1].seq) {
      loi.push(`#${p.seq}: seq không tăng dần — mảng chưa sắp xếp, hoặc bị lặp.`);
    }

    mongDoiPrev = p.hash;
  });

  return { soBanGhi: payloads.length, nguyenVen: loi.length === 0, loi };
}

// ------------------------------------------------------------------ kiểm tra

function requireString(o, k) {
  if (typeof o[k] !== 'string') {
    throw new Error(`Trường \`${k}\` phải là chuỗi, nhận được ${typeof o[k]}.`);
  }
}

function requireInteger(o, k) {
  if (!Number.isInteger(o[k])) {
    throw new Error(`Trường \`${k}\` phải là số nguyên.`);
  }
}

function requireNullableInteger(o, k) {
  if (o[k] !== null && !Number.isInteger(o[k])) {
    throw new Error(`Trường \`${k}\` phải là số nguyên hoặc null.`);
  }
}

function requireHash32(o, k) {
  if (typeof o[k] !== 'string' || !/^0x[0-9a-f]{64}$/.test(o[k])) {
    throw new Error(`Trường \`${k}\` phải là 32 byte hex chữ thường, nhận được ${o[k]}.`);
  }
}

function requireNullableHash32(o, k) {
  if (o[k] === null) return;
  requireHash32(o, k);
}

function requireIsoSeconds(o, k) {
  const v = o[k];
  if (typeof v !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/.test(v)) {
    throw new Error(
      `Trường \`${k}\` phải là ISO-8601 UTC độ chính xác giây, nhận được: ${JSON.stringify(v)}`,
    );
  }
}
