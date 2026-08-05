/**
 * Dựng payload miền ATTEND — nửa JavaScript.
 * Nửa Java: `backend/src/main/java/vn/ptit/drl/attendance/AttendancePayload.java`.
 *
 * Đây là HỢP ĐỒNG giữa backend và verifier. Verifier dựng lại payload này từ bundle của
 * sinh viên để tính lại leaf hash và đối chiếu với proof. Lệch một trường, một kiểu, hay
 * một cách định dạng thời gian là mọi proof fail — và fail im lặng.
 *
 * Test vector chung: `canonical-vectors.json`, các vector có tiền tố `attend-payload`.
 * **Sửa file này phải đi kèm `/canonical-hash` và chạy lại test CẢ HAI phía.**
 */
import { leafHash } from './leaf.mjs';

/**
 * Đúng 11 trường, không hơn không kém. Danh sách này là đặc tả — dùng nó để TỪ CHỐI bundle
 * có trường lạ hoặc thiếu trường, thay vì lặng lẽ tính ra một hash khác.
 */
export const ATTEND_FIELDS = Object.freeze([
  'checkInAt',
  'checkOutAt',
  'deviceFp',
  'eventId',
  'geofenceOk',
  'lat',
  'lng',
  'method',
  'nonce',
  'studentCode',
  'verified',
]);

const METHODS = Object.freeze(['QR_SCAN', 'QR_SHOW', 'MANUAL', 'OFFLINE_SYNC']);

/**
 * Kiểm tra và chuẩn hoá payload ATTEND lấy từ bundle.
 *
 * Nghiêm ngặt có chủ ý: `null` và trường VẮNG MẶT cho ra hai hash khác nhau
 * (`docs/canonicalization.md` §4 quy tắc 6), nên bundle thiếu `checkOutAt` sẽ âm thầm tính
 * sai nếu ta tự điền `null` hộ. Thà báo lỗi.
 */
export function normalizeAttendPayload(raw) {
  if (raw === null || typeof raw !== 'object' || Array.isArray(raw)) {
    throw new Error('Payload ATTEND phải là một object.');
  }

  const keys = Object.keys(raw).sort();
  const expected = [...ATTEND_FIELDS].sort();
  if (keys.length !== expected.length || keys.some((k, i) => k !== expected[i])) {
    const thua = keys.filter((k) => !expected.includes(k));
    const thieu = expected.filter((k) => !keys.includes(k));
    throw new Error(
      'Payload ATTEND sai tập trường.' +
        (thieu.length ? ` Thiếu: ${thieu.join(', ')}.` : '') +
        (thua.length ? ` Thừa: ${thua.join(', ')}.` : ''),
    );
  }

  requireString(raw, 'studentCode');
  requireInteger(raw, 'eventId');
  requireString(raw, 'method');
  if (!METHODS.includes(raw.method)) {
    throw new Error(`method không hợp lệ: "${raw.method}". Chỉ có: ${METHODS.join(', ')}`);
  }
  requireIsoSeconds(raw, 'checkInAt', false);
  requireIsoSeconds(raw, 'checkOutAt', true);
  requireNullableString(raw, 'deviceFp');
  requireNullableNumber(raw, 'lat');
  requireNullableNumber(raw, 'lng');
  requireBoolean(raw, 'verified', false);
  requireBoolean(raw, 'geofenceOk', true);
  requireString(raw, 'nonce');

  // Trả về chính object đã kiểm, không sao chép lại theo thứ tự nào — `canonicalize`
  // sắp xếp khoá, nên thứ tự ở đây không ảnh hưởng hash.
  return raw;
}

/** Leaf hash của một bản ghi điểm danh lấy từ bundle. */
export function attendLeafHash(raw) {
  return leafHash('ATTEND', normalizeAttendPayload(raw));
}

// ------------------------------------------------------------------ kiểm tra

function requireString(o, k) {
  if (typeof o[k] !== 'string') {
    throw new Error(`Trường \`${k}\` phải là chuỗi, nhận được ${typeof o[k]}.`);
  }
}

function requireNullableString(o, k) {
  if (o[k] !== null && typeof o[k] !== 'string') {
    throw new Error(`Trường \`${k}\` phải là chuỗi hoặc null.`);
  }
}

function requireInteger(o, k) {
  if (!Number.isInteger(o[k])) {
    throw new Error(`Trường \`${k}\` phải là số nguyên.`);
  }
}

function requireNullableNumber(o, k) {
  if (o[k] !== null && (typeof o[k] !== 'number' || !Number.isFinite(o[k]))) {
    throw new Error(`Trường \`${k}\` phải là số hữu hạn hoặc null.`);
  }
}

function requireBoolean(o, k, nullable) {
  if (o[k] === null && nullable) return;
  if (typeof o[k] !== 'boolean') {
    throw new Error(`Trường \`${k}\` phải là boolean${nullable ? ' hoặc null' : ''}.`);
  }
}

/**
 * ISO-8601 UTC, ĐỘ CHÍNH XÁC GIÂY, hậu tố `Z` — ví dụ `2026-08-04T17:55:58Z`.
 *
 * Từ chối phần mili giây là có chủ ý. Cột trong CSDL là `DATETIME(3)`, và phía Java cắt
 * xuống giây trước khi hash; bundle mang mili giây nghĩa là nó không đi ra từ đường chuẩn
 * và sẽ cho hash khác. Bắt lỗi ở đây rẻ hơn nhiều so với một proof fail không rõ lý do.
 */
function requireIsoSeconds(o, k, nullable) {
  const v = o[k];
  if (v === null && nullable) return;
  if (typeof v !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/.test(v)) {
    throw new Error(
      `Trường \`${k}\` phải là ISO-8601 UTC độ chính xác giây (vd 2026-08-04T17:55:58Z),` +
        ` nhận được: ${JSON.stringify(v)}`,
    );
  }
}
