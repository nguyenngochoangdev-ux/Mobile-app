/**
 * Dựng payload miền CRED — nửa JavaScript.
 * Nửa Java: `backend/src/main/java/vn/ptit/drl/credential/CredentialPayload.java`.
 *
 * Đây là HỢP ĐỒNG giữa backend và verifier. Verifier dựng lại payload này từ bundle của
 * sinh viên để tính lại leaf hash và đối chiếu với proof. Lệch một trường, một kiểu, hay
 * một cách định dạng thời gian là mọi proof fail — và fail im lặng.
 *
 * Test vector chung: `canonical-vectors.json`, các vector có tiền tố `cred-payload`.
 * **Sửa file này phải đi kèm `/canonical-hash` và chạy lại test CẢ HAI phía.**
 */
import { leafHash } from './leaf.mjs';

/**
 * Đúng 11 trường ở cấp ngoài, không hơn không kém. Danh sách này là đặc tả — dùng nó để
 * TỪ CHỐI bundle có trường lạ hoặc thiếu trường, thay vì lặng lẽ tính ra một hash khác.
 */
export const CRED_FIELDS = Object.freeze([
  'claims',
  'credentialId',
  'expiresAt',
  'issuedAt',
  'issuerAddress',
  'issuerOrgId',
  'nonce',
  'statusListIndex',
  'studentCode',
  'studentName',
  'type',
]);

/**
 * Lược đồ `claims` theo từng loại credential.
 *
 * Tách theo loại chứ không dùng một tập trường chung: `claims` là phần NỘI DUNG PHÁT BIỂU,
 * và mỗi loại phát biểu một thứ khác nhau. Một tập chung sẽ buộc mọi loại mang trường null
 * của các loại khác — mà `null` và trường vắng mặt cho ra hai hash khác nhau
 * (`docs/canonicalization.md` §4 quy tắc 6), nên đó là cách nhân đôi số cách hỏng.
 *
 * `DIEM_REN_LUYEN` là việc của tuần 5 — thêm ở đây kèm vector riêng, không sớm hơn.
 */
const CLAIMS_BY_TYPE = Object.freeze({
  HOAT_DONG: Object.freeze(['activityCount', 'semester', 'totalPoints']),
});

export const CRED_TYPES = Object.freeze(Object.keys(CLAIMS_BY_TYPE));

/**
 * Kiểm tra và chuẩn hoá payload CRED lấy từ bundle.
 *
 * Nghiêm ngặt có chủ ý, giống `normalizeAttendPayload`: thà báo lỗi còn hơn tự điền hộ một
 * trường thiếu rồi tính ra hash khác mà không ai biết.
 */
export function normalizeCredPayload(raw) {
  requireExactFields(raw, CRED_FIELDS, 'CRED');

  requireInteger(raw, 'credentialId');
  requireString(raw, 'type');
  if (!CRED_TYPES.includes(raw.type)) {
    throw new Error(`type không hợp lệ: "${raw.type}". Chỉ có: ${CRED_TYPES.join(', ')}`);
  }
  requireString(raw, 'studentCode');
  requireString(raw, 'studentName');
  requireInteger(raw, 'issuerOrgId');
  requireLowercaseAddress(raw, 'issuerAddress');
  requireIsoSeconds(raw, 'issuedAt', false);
  requireIsoSeconds(raw, 'expiresAt', true);
  requireInteger(raw, 'statusListIndex');
  if (raw.statusListIndex < 0) {
    throw new Error('Trường `statusListIndex` không được âm.');
  }
  requireString(raw, 'nonce');

  const claims = raw.claims;
  requireExactFields(claims, CLAIMS_BY_TYPE[raw.type], `CRED.claims (${raw.type})`);
  requireString(claims, 'semester');
  requireInteger(claims, 'activityCount');
  requireInteger(claims, 'totalPoints');

  // Trả về chính object đã kiểm — `canonicalize` sắp xếp khoá nên thứ tự ở đây không ảnh
  // hưởng hash.
  return raw;
}

/** Leaf hash của một credential lấy từ bundle. */
export function credLeafHash(raw) {
  return leafHash('CRED', normalizeCredPayload(raw));
}

// ------------------------------------------------------------------ kiểm tra

function requireExactFields(o, expectedFields, what) {
  if (o === null || typeof o !== 'object' || Array.isArray(o)) {
    throw new Error(`Payload ${what} phải là một object.`);
  }
  const keys = Object.keys(o).sort();
  const expected = [...expectedFields].sort();
  if (keys.length !== expected.length || keys.some((k, i) => k !== expected[i])) {
    const thua = keys.filter((k) => !expected.includes(k));
    const thieu = expected.filter((k) => !keys.includes(k));
    throw new Error(
      `Payload ${what} sai tập trường.` +
        (thieu.length ? ` Thiếu: ${thieu.join(', ')}.` : '') +
        (thua.length ? ` Thừa: ${thua.join(', ')}.` : ''),
    );
  }
}

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

/**
 * Địa chỉ ví: `0x` + 40 hex CHỮ THƯỜNG.
 *
 * Từ chối dạng checksum EIP-55 là có chủ ý, không phải khắt khe thừa. EIP-55 trộn hoa/thường
 * theo hash của chính địa chỉ, nên nếu một phía lưu checksum còn phía kia lưu chữ thường thì
 * JCS ra hai chuỗi khác nhau → hai leaf khác nhau → proof fail im lặng. Cùng họ lỗi với
 * nonce chữ hoa, thứ bộ vector đã chặn từ tuần 3.
 *
 * Người gọi so sánh địa chỉ phục hồi từ chữ ký với trường này phải `.toLowerCase()` trước —
 * `ethers.recoverAddress` trả về dạng checksum.
 */
function requireLowercaseAddress(o, k) {
  const v = o[k];
  if (typeof v !== 'string' || !/^0x[0-9a-f]{40}$/.test(v)) {
    throw new Error(
      `Trường \`${k}\` phải là địa chỉ "0x" + 40 hex CHỮ THƯỜNG` +
        ` (dạng checksum EIP-55 bị từ chối), nhận được: ${JSON.stringify(v)}`,
    );
  }
}

/**
 * ISO-8601 UTC, ĐỘ CHÍNH XÁC GIÂY, hậu tố `Z` — ví dụ `2026-08-06T10:00:00Z`.
 *
 * Từ chối phần mili giây: cột trong CSDL là `DATETIME(3)` và phía Java cắt xuống giây trước
 * khi hash, nên bundle mang mili giây nghĩa là nó không đi ra từ đường chuẩn.
 */
function requireIsoSeconds(o, k, nullable) {
  const v = o[k];
  if (v === null && nullable) return;
  if (typeof v !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/.test(v)) {
    throw new Error(
      `Trường \`${k}\` phải là ISO-8601 UTC độ chính xác giây (vd 2026-08-06T10:00:00Z),` +
        ` nhận được: ${JSON.stringify(v)}`,
    );
  }
}
