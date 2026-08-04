/**
 * Test vector canonicalization — NỬA JAVASCRIPT.
 * Nửa Java: `backend/src/test/java/vn/ptit/drl/anchor/CanonicalVectorTest.java`.
 *
 * Chạy: `npm test` (trong verifier/)
 *
 * Hai phía đọc CÙNG MỘT file vector. Xanh một phía không có nghĩa gì —
 * `/canonical-hash` yêu cầu xanh cả hai.
 *
 * Đọc file trong `backend/` là ràng buộc lúc TEST, không phải lúc chạy: verifier
 * khi build ra bản tĩnh không chạm gì tới backend. Cố tình dùng chung một file
 * thay vì nhân đôi — nhân đôi thì hai bản sẽ trôi khỏi nhau, đúng thứ bộ test này
 * sinh ra để chặn.
 */
import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { canonicalize } from '../src/jcs.mjs';
import { leafHash, domainBytes8, DOMAINS } from '../src/leaf.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const VECTORS_PATH = resolve(HERE, '../../backend/src/test/resources/canonical-vectors.json');
const doc = JSON.parse(readFileSync(VECTORS_PATH, 'utf8'));

describe('Bộ test vector dùng chung', () => {
  test('file vector đọc được và không rỗng', () => {
    assert.ok(doc.vectors.length >= 5, 'phải có tối thiểu 5 vector');
    assert.ok(doc.rejects.length > 0, 'phải có trường hợp bắt buộc từ chối');
  });

  for (const v of doc.vectors) {
    test(`${v.id} — chuỗi JCS khớp`, () => {
      assert.equal(canonicalize(v.payload), v.expected.jcs, v.why);
    });

    test(`${v.id} — leaf hash khớp`, () => {
      assert.equal(leafHash(v.domain, v.payload), v.expected.leaf);
    });

    test(`${v.id} — bytes8 của miền khớp`, () => {
      const hex = '0x' + Buffer.from(domainBytes8(v.domain)).toString('hex');
      assert.equal(hex, v.expected.domainBytes8);
    });
  }

  for (const r of doc.rejects) {
    test(`từ chối: ${r.id}`, () => {
      assert.throws(
        () => leafHash(r.domain, r.payload),
        (err) => {
          assert.ok(
            err.message.includes(r.expectedError),
            `mong đợi lỗi chứa "${r.expectedError}", nhận được: ${err.message}`,
          );
          return true;
        },
        r.why,
      );
    });
  }
});

describe('Quy tắc serialize — bẫy Java↔JS đã biết', () => {
  const nonce = '0x9f86d081884c7d659a2feaa0c55ad015';

  test('số thực nguyên vẹn KHÔNG có đuôi .0 (Java Double.toString cho "20.0")', () => {
    assert.equal(canonicalize({ a: 20.0 }), '{"a":20}');
    assert.equal(canonicalize({ a: 100.0 }), '{"a":100}');
  });

  test('-0 gộp về "0"', () => {
    assert.equal(canonicalize({ a: -0 }), '{"a":0}');
  });

  test('sắp xếp khóa là đệ quy ở mọi cấp', () => {
    assert.equal(canonicalize({ b: { d: 1, c: 2 }, a: 3 }), '{"a":3,"b":{"c":2,"d":1}}');
  });

  test('mảng GIỮ NGUYÊN thứ tự — không sắp xếp', () => {
    assert.equal(canonicalize(['c', 'a', 'b']), '["c","a","b"]');
  });

  test('null giữ nguyên, không bị lược bỏ', () => {
    assert.equal(canonicalize({ a: null, b: 1 }), '{"a":null,"b":1}');
  });

  test('không có khoảng trắng sau dấu : và ,', () => {
    const s = canonicalize({ a: 1, b: 2 });
    assert.equal(s, '{"a":1,"b":2}');
    assert.ok(!s.includes(' '));
  });

  test('tiếng Việt giữ UTF-8 thô, không escape \\uXXXX', () => {
    const s = canonicalize({ n: 'Nguyễn Ngọc Hoàng' });
    assert.equal(s, '{"n":"Nguyễn Ngọc Hoàng"}');
    assert.ok(!s.includes('\\u'));
  });

  test('ký tự điều khiển dùng chuỗi thoát ngắn', () => {
    assert.equal(canonicalize({ a: '\n\t\r\b\f' }), '{"a":"\\n\\t\\r\\b\\f"}');
    assert.equal(canonicalize({ a: '' }), '{"a":"\\u0001"}');
  });

  test('NaN và Infinity bị từ chối', () => {
    assert.throws(() => canonicalize({ a: NaN }), /hữu hạn/);
    assert.throws(() => canonicalize({ a: Infinity }), /hữu hạn/);
  });

  test('undefined bị từ chối (không im lặng bỏ qua như JSON.stringify)', () => {
    assert.throws(() => canonicalize({ a: undefined }), /không hỗ trợ/);
  });

  test('payload thiếu nonce bị từ chối ở mọi miền', () => {
    for (const d of DOMAINS) {
      assert.throws(() => leafHash(d, { x: 1 }), /nonce/);
    }
  });

  test('cùng payload, khác miền → khác leaf', () => {
    const p = { x: 1, nonce };
    const seen = new Set(DOMAINS.map((d) => leafHash(d, p)));
    assert.equal(seen.size, DOMAINS.length, 'miền neo phải phân tách được các cây');
  });

  test('đổi một byte trong payload → đổi leaf', () => {
    assert.notEqual(leafHash('ATTEND', { x: 1, nonce }), leafHash('ATTEND', { x: 2, nonce }));
  });

  test('trường vắng mặt KHÁC trường null — có chủ ý, ghi trong đặc tả', () => {
    assert.notEqual(leafHash('ATTEND', { nonce }), leafHash('ATTEND', { a: null, nonce }));
  });
});
