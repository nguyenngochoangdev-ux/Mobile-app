/**
 * Test vector credential — NỬA JS.
 * Nửa Java: `backend/src/test/java/vn/ptit/drl/credential/{CredentialPayloadVectorTest,
 * IssuerSignerVectorTest}.java`.
 *
 * Chạy: `cd verifier && npm test`
 *
 * Hai phía đọc CÙNG hai file trong `backend/src/test/resources/`. Verifier đọc chúng CHỈ
 * LÚC TEST — bản build tĩnh không chạm gì tới backend (PROJECT.md §4).
 */
import test, { describe } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { SigningKey, recoverAddress, Signature, computeAddress } from 'ethers';

import { credLeafHash, normalizeCredPayload, CRED_FIELDS, CRED_TYPES } from '../src/cred.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const RES = resolve(HERE, '../../backend/src/test/resources');

const CANONICAL = JSON.parse(readFileSync(resolve(RES, 'canonical-vectors.json'), 'utf8'));
const SIGS = JSON.parse(readFileSync(resolve(RES, 'cred-signature-vectors.json'), 'utf8'));

const credVectors = CANONICAL.vectors.filter((v) => v.id.startsWith('cred-payload'));

/** Bản sao sâu — để sửa vào bản sao mà không làm hỏng vector cho test sau. */
const clone = (o) => JSON.parse(JSON.stringify(o));

describe('Payload CRED — hợp đồng backend↔verifier', () => {
  test('bộ vector có ít nhất hai lược đồ CRED thật', () => {
    assert.ok(
      credVectors.length >= 2,
      'Thiếu vector cred-payload-*. Sinh lại: npm run gen-vectors',
    );
  });

  for (const v of credVectors) {
    test(`${v.id} — leaf khớp vector`, () => {
      assert.equal(credLeafHash(v.payload), v.expected.leaf, v.why);
    });

    test(`${v.id} — qua được vòng kiểm tập trường`, () => {
      assert.doesNotThrow(() => normalizeCredPayload(v.payload));
    });
  }

  test('đúng 11 trường ở cấp ngoài, khớp CredentialPayload.of() phía Java', () => {
    assert.equal(CRED_FIELDS.length, 11);
    for (const v of credVectors) {
      assert.deepEqual(Object.keys(v.payload).sort(), [...CRED_FIELDS].sort());
    }
  });

  test('chỉ có đúng một loại credential — DIEM_REN_LUYEN là việc của tuần 5', () => {
    assert.deepEqual(CRED_TYPES, ['HOAT_DONG']);
  });

  test('claims là object LỒNG — chốt việc sắp xếp khóa là đệ quy', () => {
    for (const v of credVectors) {
      assert.equal(typeof v.payload.claims, 'object');
      assert.ok(!Array.isArray(v.payload.claims));
    }
  });
});

describe('Payload CRED — phải bị TỪ CHỐI', () => {
  const base = () => clone(credVectors[0].payload);

  test('thiếu một trường', () => {
    const p = base();
    delete p.statusListIndex;
    assert.throws(() => normalizeCredPayload(p), /Thiếu: statusListIndex/);
  });

  test('thừa một trường lạ', () => {
    const p = base();
    p.ghiChu = 'thêm cho vui';
    assert.throws(() => normalizeCredPayload(p), /Thừa: ghiChu/);
  });

  test('expiresAt VẮNG MẶT khác expiresAt null — không tự điền hộ', () => {
    const p = base();
    delete p.expiresAt;
    assert.throws(() => normalizeCredPayload(p), /Thiếu: expiresAt/);
  });

  test('issuerAddress dạng checksum EIP-55 bị từ chối', () => {
    const p = base();
    p.issuerAddress = '0xf32728c5c2D0575ea406Ad37e2467916c89F529F';
    assert.throws(() => normalizeCredPayload(p), /CHỮ THƯỜNG/);
  });

  test('issuerAddress sai độ dài bị từ chối', () => {
    const p = base();
    p.issuerAddress = '0xf32728c5c2d0575ea406ad37e2467916c89f529';
    assert.throws(() => normalizeCredPayload(p), /issuerAddress/);
  });

  test('type không có trong danh sách bị từ chối', () => {
    const p = base();
    p.type = 'DIEM_REN_LUYEN';
    assert.throws(() => normalizeCredPayload(p), /type không hợp lệ/);
  });

  test('claims thiếu trường bị từ chối', () => {
    const p = base();
    delete p.claims.totalPoints;
    assert.throws(() => normalizeCredPayload(p), /claims.*Thiếu: totalPoints/s);
  });

  test('claims thừa trường bị từ chối', () => {
    const p = base();
    p.claims.diemRenLuyen = 90;
    assert.throws(() => normalizeCredPayload(p), /claims.*Thừa: diemRenLuyen/s);
  });

  test('issuedAt mang mili giây bị từ chối — không đi ra từ đường chuẩn', () => {
    const p = base();
    p.issuedAt = '2026-08-06T10:00:00.123Z';
    assert.throws(() => normalizeCredPayload(p), /độ chính xác giây/);
  });

  test('statusListIndex âm bị từ chối', () => {
    const p = base();
    p.statusListIndex = -1;
    assert.throws(() => normalizeCredPayload(p), /không được âm/);
  });

  test('statusListIndex dạng chuỗi bị từ chối — "4" và 4 cho hai hash khác nhau', () => {
    const p = base();
    p.statusListIndex = '4';
    assert.throws(() => normalizeCredPayload(p), /số nguyên/);
  });

  test('thiếu nonce bị từ chối ở tầng leaf', () => {
    const p = base();
    delete p.nonce;
    assert.throws(() => normalizeCredPayload(p), /Thiếu: nonce/);
  });
});

describe('Chữ ký issuer — bộ vector chung với Java', () => {
  const key = new SigningKey(SIGS.testPrivateKey);
  const signerAddress = computeAddress(key.publicKey).toLowerCase();

  for (const v of SIGS.vectors) {
    test(`${v.id} — leaf dựng lại từ payload khớp vector`, () => {
      assert.equal(credLeafHash(v.payload), v.expected.leaf, v.why);
    });

    test(`${v.id} — phục hồi ra đúng địa chỉ đã ký`, () => {
      const recovered = recoverAddress(v.expected.leaf, v.expected.signature);
      assert.equal(recovered.toLowerCase(), v.expected.signerAddress);
    });

    test(`${v.id} — ký lại ra ĐÚNG TỪNG BYTE`, () => {
      const mine = Signature.from(key.sign(v.expected.leaf)).serialized;
      assert.equal(mine, v.expected.signature);
    });
  }

  test('chữ ký đúng 65 byte, v thuộc {27,28}', () => {
    for (const v of SIGS.vectors) {
      assert.equal(v.expected.signatureBytes, 65);
      assert.ok([27, 28].includes(v.expected.v), `v = ${v.expected.v}`);
    }
  });

  test('địa chỉ trong vector là CHỮ THƯỜNG — checksum EIP-55 làm lệch leaf hash', () => {
    for (const v of SIGS.vectors) {
      assert.match(v.expected.signerAddress, /^0x[0-9a-f]{40}$/);
    }
  });

  test('khóa test đúng là khóa đã ký', () => {
    assert.equal(SIGS.vectors[0].expected.signerAddress, signerAddress);
  });

  test('leaf trong vector chữ ký khớp leaf trong canonical-vectors — nối hai bộ lại', () => {
    // Nếu tầng leaf hash lệch thì cả hai bộ cùng lệch, nên phép so này bảo vệ chéo:
    // không thể sửa một bộ mà quên bộ kia.
    const leavesCanonical = credVectors.map((v) => v.expected.leaf).sort();
    const leavesSig = SIGS.vectors.map((v) => v.expected.leaf).sort();
    assert.deepEqual(leavesSig, leavesCanonical);
  });
});

describe('Chữ ký — sửa vào là hỏng', () => {
  const v = SIGS.vectors[0];

  test('đổi một byte của leaf → địa chỉ KHÁC (KHÔNG ném lỗi)', () => {
    const leaf = '0x' + (BigInt(v.expected.leaf) ^ 1n).toString(16).padStart(64, '0');
    const recovered = recoverAddress(leaf, v.expected.signature);

    // Đây là điểm quan trọng nhất của cả nhóm: phục hồi luôn TRẢ VỀ một địa chỉ hợp lệ.
    // Nó không phải phép kiểm chữ ký. Phép kiểm thật là so địa chỉ đó với issuerAddress
    // trong payload — verifier BẮT BUỘC phải làm bước đó.
    assert.notEqual(recovered.toLowerCase(), v.expected.signerAddress);
  });

  test('sửa payload rồi giữ nguyên chữ ký → leaf đổi → địa chỉ KHÁC', () => {
    const p = clone(v.payload);
    p.claims.totalPoints = 100; // tự nâng điểm

    const leafGia = credLeafHash(p);
    assert.notEqual(leafGia, v.expected.leaf);

    const recovered = recoverAddress(leafGia, v.expected.signature);
    assert.notEqual(recovered.toLowerCase(), v.expected.signerAddress);
  });

  test('đổi statusListIndex sang bit chưa bật → leaf đổi → địa chỉ KHÁC', () => {
    // Kịch bản thật: credential ĐÃ BỊ THU HỒI, người cầm nó trỏ verifier sang một bit khác.
    // Chặn được vì statusListIndex nằm TRONG payload được ký và được neo.
    const p = clone(v.payload);
    p.statusListIndex = 999999;

    const recovered = recoverAddress(credLeafHash(p), v.expected.signature);
    assert.notEqual(recovered.toLowerCase(), v.expected.signerAddress);
  });

  test('đổi issuerAddress sang ví của mình → leaf đổi → chữ ký không còn khớp', () => {
    // Kịch bản thật: kẻ tấn công tự ký credential bằng ví của mình rồi sửa issuerAddress.
    // Leaf đổi nên proof Merkle fail; và nếu chỉ có chữ ký thì IssuerRegistry chặn tiếp.
    const p = clone(v.payload);
    p.issuerAddress = '0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266';

    assert.notEqual(credLeafHash(p), v.expected.leaf);
  });
});
