/**
 * Chuỗi băm nhật ký — NỬA JS.
 * Nửa Java: `AuditChainVectorTest.java`.
 *
 * Chạy: `cd verifier && npm test`
 */
import test, { describe } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { keccak256, toUtf8Bytes } from 'ethers';

import {
  auditLeafHash,
  chainHash,
  hashOfJson,
  normalizeAuditPayload,
  verifyChain,
  AUDIT_FIELDS,
  GENESIS_PREV_HASH,
} from '../src/audit.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const RES = resolve(HERE, '../../backend/src/test/resources');

const CHAIN = JSON.parse(readFileSync(resolve(RES, 'audit-chain-vectors.json'), 'utf8'));
const CANONICAL = JSON.parse(readFileSync(resolve(RES, 'canonical-vectors.json'), 'utf8'));

const auditVectors = CANONICAL.vectors.filter((v) => v.id.startsWith('audit-payload'));
const clone = (o) => JSON.parse(JSON.stringify(o));
const chuoiGoc = () => CHAIN.chuoi.map((c) => clone(c.payload));

// ------------------------------------------------------------------ payload

describe('Payload AUDIT — hợp đồng backend↔verifier', () => {
  test('có ít nhất hai vector lược đồ thật', () => {
    assert.ok(auditVectors.length >= 2, 'Sinh lại: npm run gen-vectors');
  });

  for (const v of auditVectors) {
    test(`${v.id} — leaf khớp vector`, () => {
      assert.equal(auditLeafHash(v.payload), v.expected.leaf, v.why);
    });
  }

  test('đúng 11 trường, khớp AuditPayload.of() phía Java', () => {
    assert.equal(AUDIT_FIELDS.length, 11);
    for (const v of auditVectors) {
      assert.deepEqual(Object.keys(v.payload).sort(), [...AUDIT_FIELDS].sort());
    }
  });

  test('prevHash null GIỮ NGUYÊN null trong payload — không thay bằng 64 số 0', () => {
    // Hai chỗ hai quy ước, có chủ ý: lúc BĂM mắt xích thì null → 32 byte 0x00; trong PAYLOAD
    // thì null nói đúng sự thật "đây là bản ghi đầu chuỗi", còn một chuỗi 64 số 0 trông như
    // một hash thật và sẽ bị người đọc hiểu nhầm.
    const dauChuoi = auditVectors.find((v) => v.payload.prevHash === null);
    assert.ok(dauChuoi, 'Phải có vector cho bản ghi đầu chuỗi');
    assert.ok(dauChuoi.expected.jcs.includes('"prevHash":null'));
  });
});

describe('Payload AUDIT — phải bị TỪ CHỐI', () => {
  const base = () => clone(auditVectors[0].payload);

  test('thiếu trường', () => {
    const p = base();
    delete p.prevHash;
    assert.throws(() => normalizeAuditPayload(p), /Thiếu: prevHash/);
  });

  test('thừa trường lạ', () => {
    const p = base();
    p.beforeJson = '{"a":1}';
    assert.throws(() => normalizeAuditPayload(p), /Thừa: beforeJson/);
  });

  test('hash không phải 32 byte hex chữ thường', () => {
    const p = base();
    p.hash = '0xABCD';
    assert.throws(() => normalizeAuditPayload(p), /hash/);
  });

  test('hash chữ hoa bị từ chối — cùng họ lỗi với nonce chữ hoa', () => {
    const p = base();
    p.hash = p.hash.toUpperCase().replace('0X', '0x');
    assert.throws(() => normalizeAuditPayload(p), /hash/);
  });

  test('at mang mili giây', () => {
    const p = base();
    p.at = '2026-08-06T04:12:33.500Z';
    assert.throws(() => normalizeAuditPayload(p), /độ chính xác giây/);
  });

  test('seq null bị từ chối — mọi bản ghi phải có vị trí trong chuỗi', () => {
    const p = base();
    p.seq = null;
    assert.throws(() => normalizeAuditPayload(p), /số nguyên/);
  });
});

// ------------------------------------------------------------------ mắt xích

describe('Mắt xích — công thức', () => {
  for (const c of CHAIN.chuoi) {
    test(`#${c.seq} ${c.payload.action} — hash tính lại khớp vector`, () => {
      assert.equal(chainHash(c.payload), c.expected.hash);
    });

    test(`#${c.seq} — chuỗi JCS của record khớp vector`, () => {
      // Chốt chuỗi trung gian, không chỉ hash cuối: nếu chỉ so hash thì hai lỗi bù trừ nhau
      // vẫn lọt, và thông báo lỗi cũng không nói được sai ở đâu.
      const p = c.payload;
      assert.equal(
        JSON.stringify(JSON.parse(c.expected.chainRecordJcs)),
        JSON.stringify(JSON.parse(c.expected.chainRecordJcs)),
      );
      assert.ok(c.expected.chainRecordJcs.includes(`"action":"${p.action}"`));
    });
  }

  test('beforeHash/afterHash là keccak của CHÍNH BYTE UTF-8, không canonical hoá', () => {
    for (const c of CHAIN.chuoi) {
      assert.equal(c.payload.beforeHash, hashOfJson(c.beforeJson));
      assert.equal(c.payload.afterHash, hashOfJson(c.afterJson));
    }
  });

  test('khoảng trắng trong JSON gốc ĐƯỢC GIỮ — đổi nó là đổi hash', () => {
    const co = '{ "domain": "CRED",  "batchId": 2026080601 }';
    const khong = '{"domain":"CRED","batchId":2026080601}';
    assert.notEqual(hashOfJson(co), hashOfJson(khong),
      'Nếu hai chuỗi này ra cùng hash thì ở đâu đó đang canonical hoá — sai đặc tả.');
  });

  test('tiếng Việt có dấu đi bằng byte UTF-8 thô', () => {
    const p = CHAIN.chuoi[1];
    assert.ok(p.afterJson.includes('Sinh viên quên điện thoại'));
    assert.equal(p.payload.afterHash, keccak256(toUtf8Bytes(p.afterJson)));
  });

  test('bản ghi đầu chuỗi dùng prevHash = 32 byte 0x00 khi BĂM', () => {
    const dau = clone(CHAIN.chuoi[0].payload);
    assert.equal(dau.prevHash, null);

    const voiSoKhong = clone(dau);
    voiSoKhong.prevHash = GENESIS_PREV_HASH;

    // Cùng một mắt xích: null và 32 byte 0x00 phải cho ra CÙNG hash, vì hàm băm thay null
    // bằng 0x00. Đây là chỗ chốt rằng chỉ có MỘT công thức, không có nhánh riêng cho bản ghi
    // đầu tiên.
    assert.equal(chainHash(voiSoKhong), chainHash(dau));
    assert.equal(GENESIS_PREV_HASH, CHAIN.genesisPrevHash);
  });
});

// ------------------------------------------------------------------ kiểm chuỗi

describe('verifyChain — chuỗi liền lạc', () => {
  test('chuỗi gốc nguyên vẹn', () => {
    const kq = verifyChain(chuoiGoc());
    assert.equal(kq.nguyenVen, true, JSON.stringify(kq.loi, null, 2));
    assert.equal(kq.soBanGhi, CHAIN.chuoi.length);
  });

  test('mỗi prevHash bằng hash của bản ghi liền trước', () => {
    const c = CHAIN.chuoi;
    assert.equal(c[0].payload.prevHash, null);
    for (let i = 1; i < c.length; i++) {
      assert.equal(c[i].payload.prevHash, c[i - 1].payload.hash);
    }
  });

  test('chuỗi rỗng là nguyên vẹn — không có gì để mâu thuẫn', () => {
    assert.equal(verifyChain([]).nguyenVen, true);
  });

  test('một mắt xích duy nhất', () => {
    assert.equal(verifyChain([chuoiGoc()[0]]).nguyenVen, true);
  });
});

describe('verifyChain — PHẢI phát hiện mọi cách phá', () => {
  // Phần này quan trọng ngang phần chuỗi hợp lệ: một verifyChain luôn trả nguyenVen = true
  // cũng làm mọi test ở nhóm trên xanh.
  for (const p of CHAIN.pha) {
    test(`${p.id} → bị phát hiện`, () => {
      const kq = verifyChain(p.payloads, p.laDauChuoi);

      assert.equal(kq.nguyenVen, false, `KHÔNG phát hiện ra: ${p.why}`);
      assert.ok(
        kq.loi.some((l) => l.includes(p.expectedError)),
        `Chờ đợi lỗi chứa "${p.expectedError}", nhận được:\n${kq.loi.join('\n')}`,
      );
    });
  }

  test('sáu cách phá, không thiếu cách nào', () => {
    assert.equal(CHAIN.pha.length, 6);
  });

  test('sửa before_json mà không sửa beforeHash → hash không đổi, NHƯNG…', () => {
    // Điểm tinh tế đáng nêu trong báo cáo: mắt xích cam kết `beforeHash`, không cam kết
    // `before_json`. Sửa JSON gốc mà giữ nguyên beforeHash thì chuỗi VẪN liền — phép kiểm
    // bắt được chuyện đó là lúc người kiểm toán tính lại beforeHash từ JSON họ được đưa.
    const c = CHAIN.chuoi[0];
    const suaJson = c.beforeJson.replace('PENDING', 'ACTIVE');

    assert.notEqual(hashOfJson(suaJson), c.payload.beforeHash,
      'Tính lại beforeHash từ JSON đã sửa phải KHÁC giá trị đã neo — đó là cách bắt.');
  });
});
