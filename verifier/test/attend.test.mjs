/**
 * Test lược đồ payload ATTEND — nửa JS.
 * Nửa Java: `backend/src/test/java/vn/ptit/drl/attendance/AttendancePayloadTest.java`.
 *
 * Hai phía cùng đọc `canonical-vectors.json`. Java chốt rằng backend DỰNG RA payload này từ
 * bản ghi thật; file này chốt rằng verifier TÍNH LẠI được đúng leaf hash từ payload trong
 * bundle, và từ chối bundle sai lược đồ thay vì lặng lẽ ra hash khác.
 */
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { attendLeafHash, normalizeAttendPayload, ATTEND_FIELDS } from '../src/attend.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const DOC = JSON.parse(
  readFileSync(resolve(HERE, '../../backend/src/test/resources/canonical-vectors.json'), 'utf8'),
);

const vector = (id) => DOC.vectors.find((v) => v.id === id);
const DAY_DU = vector('attend-payload-day-du');
const TOAN_NULL = vector('attend-payload-toan-null');

describe('Lược đồ ATTEND — tính lại leaf hash từ bundle', () => {
  it('bản ghi QR_SCAN đầy đủ cho ra đúng leaf trong vector', () => {
    assert.equal(attendLeafHash(DAY_DU.payload), DAY_DU.expected.leaf);
  });

  it('bản ghi MANUAL toàn null cho ra đúng leaf trong vector', () => {
    assert.equal(attendLeafHash(TOAN_NULL.payload), TOAN_NULL.expected.leaf);
  });

  it('thứ tự khóa trong bundle KHÔNG ảnh hưởng hash', () => {
    // JSON không đảm bảo thứ tự khóa qua các lần tuần tự hóa; canonicalize sắp xếp lại.
    const daoNguoc = Object.fromEntries(Object.entries(DAY_DU.payload).reverse());
    assert.equal(attendLeafHash(daoNguoc), DAY_DU.expected.leaf);
  });

  it('đúng 11 trường, khớp phía Java', () => {
    assert.equal(ATTEND_FIELDS.length, 11);
    assert.deepEqual([...ATTEND_FIELDS].sort(), Object.keys(DAY_DU.payload).sort());
  });
});

describe('Lược đồ ATTEND — bundle sai phải bị TỪ CHỐI', () => {
  // Quan trọng ngang phần trên: một hàm chấp nhận mọi thứ vẫn làm các test kia xanh, rồi
  // âm thầm tính ra hash khác khi gặp bundle hỏng.

  it('thiếu trường bị từ chối, KHÔNG tự điền null hộ', () => {
    // `null` và trường VẮNG MẶT cho ra hai hash khác nhau (canonicalization §4 quy tắc 6).
    // Tự điền hộ nghĩa là tính ra một hash không ai kiểm được.
    const thieu = { ...TOAN_NULL.payload };
    delete thieu.checkOutAt;
    assert.throws(() => normalizeAttendPayload(thieu), /Thiếu: checkOutAt/);
  });

  it('trường thừa bị từ chối', () => {
    assert.throws(
      () => normalizeAttendPayload({ ...DAY_DU.payload, qrSlot: 12345 }),
      /Thừa: qrSlot/,
    );
  });

  it('thời gian có mili giây bị từ chối', () => {
    assert.throws(
      () => normalizeAttendPayload({ ...DAY_DU.payload, checkInAt: '2026-08-04T17:55:58.123Z' }),
      /giây/,
    );
  });

  it('thời gian không có hậu tố Z bị từ chối', () => {
    assert.throws(
      () => normalizeAttendPayload({ ...DAY_DU.payload, checkInAt: '2026-08-04T17:55:58' }),
      /giây/,
    );
  });

  it('method lạ bị từ chối', () => {
    assert.throws(
      () => normalizeAttendPayload({ ...DAY_DU.payload, method: 'TU_BIA' }),
      /method không hợp lệ/,
    );
  });

  it('eventId dạng chuỗi bị từ chối', () => {
    // Bẫy thật: JSON của backend trả số, nhưng một số client biến nó thành chuỗi.
    // "7" và 7 cho ra hai hash khác nhau.
    assert.throws(
      () => normalizeAttendPayload({ ...DAY_DU.payload, eventId: '7' }),
      /số nguyên/,
    );
  });

  it('verified null bị từ chối, nhưng geofenceOk null thì hợp lệ', () => {
    assert.throws(
      () => normalizeAttendPayload({ ...DAY_DU.payload, verified: null }),
      /boolean/,
    );
    assert.ok(normalizeAttendPayload({ ...DAY_DU.payload, geofenceOk: null }));
  });

  it('payload không phải object bị từ chối', () => {
    for (const bad of [null, 'chuỗi', 42, [1, 2, 3]]) {
      assert.throws(() => normalizeAttendPayload(bad), /object/);
    }
  });

  it('nonce thiếu vẫn bị chặn ở tầng leaf hash', () => {
    const khongNonce = { ...DAY_DU.payload };
    delete khongNonce.nonce;
    assert.throws(() => attendLeafHash(khongNonce), /Thiếu: nonce/);
  });
});
