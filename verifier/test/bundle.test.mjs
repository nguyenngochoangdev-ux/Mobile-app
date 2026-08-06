/**
 * Xác minh bundle — NỬA JS.
 * Nửa Java: `backend/src/test/java/vn/ptit/drl/credential/CredentialBundleDbTest.java`.
 *
 * Chạy: `cd verifier && npm test`
 *
 * Hai phía đọc cùng `backend/src/test/resources/bundle-fixture.json`.
 */
import test, { describe } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { verifyBundle, BUNDLE_FORMAT, BUNDLE_VERSION } from '../src/bundle.mjs';
import { AMOY } from '../src/trusted-chain.mjs';
import { canonicalize } from '../src/jcs.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const FIXTURE = JSON.parse(
  readFileSync(resolve(HERE, '../../backend/src/test/resources/bundle-fixture.json'), 'utf8'),
);

const clone = (o) => JSON.parse(JSON.stringify(o));
const bundle = () => clone(FIXTURE.bundle);

/**
 * Chuỗi giả — trả về đúng những gì fixture mong đợi.
 *
 * Cùng hình dạng với `chainReader` thật (`chain.mjs`) nên phần LOGIC xác minh được test đầy
 * đủ mà không phụ thuộc Amoy còn sống hay không, và không cần neo thật một lô CRED chỉ để
 * chạy test.
 */
function stubReader(overrides = {}) {
  return {
    async getRoot(domain, batchId) {
      if (domain !== 'CRED' || Number(batchId) !== FIXTURE.bundle.anchor.batchId) return null;
      return overrides.root ?? FIXTURE.bundle.anchor.merkleRoot;
    },
    async isActiveIssuer() {
      return overrides.activeIssuer ?? true;
    },
    async isRevoked() {
      return overrides.revoked ?? false;
    },
  };
}

const passed = (r, id) => r.checks.find((c) => c.id === id)?.pass === true;
const failed = (r, id) => {
  const c = r.checks.find((x) => x.id === id);
  return c !== undefined && !c.pass && !c.skipped;
};

// ------------------------------------------------------------------ luồng chính

describe('Bundle hợp lệ', () => {
  test('xác minh được đầy đủ khi chuỗi trả về đúng', async () => {
    const r = await verifyBundle(bundle(), AMOY, stubReader());
    assert.equal(r.ok, true, r.summary + '\n' + JSON.stringify(r.checks, null, 2));
    assert.equal(r.offline, false);
    assert.equal(r.checks.filter((c) => c.skipped).length, 0);
  });

  test('sáu phép kiểm, đúng thứ tự đã đặc tả', async () => {
    const r = await verifyBundle(bundle(), AMOY, stubReader());
    assert.deepEqual(r.checks.map((c) => c.id),
      ['format', 'chain', 'leaf', 'signature', 'anchor', 'issuer', 'revocation']);
  });

  test('payload nhúng trong bundle ĐÃ ở dạng chuẩn tắc', () => {
    // Backend nhúng nguyên văn `payload_json` — đúng chuỗi đã bam và đã ký. JCS là idempotent
    // nên canonical hóa lại phải ra chính nó. Lệch nghĩa là bundle đã đi qua một công cụ in
    // lại JSON, và tuy verifier vẫn xác minh được, ta mất một phép kiểm rất rẻ.
    const p = FIXTURE.bundle.credential.payload;
    assert.equal(canonicalize(p), JSON.stringify(p));
  });

  test('fixture khai đúng format và version mà verifier hiểu', () => {
    assert.equal(FIXTURE.bundle.format, BUNDLE_FORMAT);
    assert.equal(FIXTURE.bundle.version, BUNDLE_VERSION);
  });

  test('proof có 2 sibling — cây 4 lá, không phải trường hợp biên một lá', () => {
    assert.equal(FIXTURE.bundle.anchor.proof.length, 2);
  });
});

describe('Chế độ offline', () => {
  test('không bao giờ trả ok:true — nó KHÔNG chứng minh đã neo hay còn hiệu lực', async () => {
    const r = await verifyBundle(bundle(), AMOY, null);
    assert.equal(r.ok, false);
    assert.equal(r.offline, true);
    assert.equal(r.checks.filter((c) => c.skipped).length, 3);
  });

  test('bốn phép kiểm không cần mạng vẫn chạy và vẫn pass', async () => {
    const r = await verifyBundle(bundle(), AMOY, null);
    for (const id of ['format', 'chain', 'leaf', 'signature']) {
      assert.ok(passed(r, id), `${id} phải pass ở chế độ offline`);
    }
  });

  test('offline vẫn bắt được payload bị sửa', async () => {
    const b = bundle();
    b.credential.payload.claims.totalPoints = 100;
    const r = await verifyBundle(b, AMOY, null);
    assert.ok(failed(r, 'leaf'));
  });
});

// ------------------------------------------------------------------ giả mạo

describe('Sửa bundle — mỗi trường phải bị bắt', () => {
  test('sửa điểm trong payload → leaf đổi', async () => {
    const b = bundle();
    b.credential.payload.claims.totalPoints = 100;
    const r = await verifyBundle(b, AMOY, stubReader());
    assert.equal(r.ok, false);
    assert.ok(failed(r, 'leaf'));
  });

  test('sửa tên sinh viên → leaf đổi', async () => {
    const b = bundle();
    b.credential.payload.studentName = 'Người Khác';
    const r = await verifyBundle(b, AMOY, stubReader());
    assert.ok(failed(r, 'leaf'));
  });

  test('sửa payload VÀ sửa luôn `leaf` cho khớp → proof không dẫn về root', async () => {
    // Kẻ tấn công thông minh hơn: sửa cả hai chỗ để phép kiểm 3 xanh. Bước chặn tiếp theo là
    // Merkle proof — leaf mới không nằm trong cây đã neo.
    const b = bundle();
    b.credential.payload.claims.totalPoints = 100;
    b.credential.leaf =
      '0x' + '11'.repeat(32);
    const r = await verifyBundle(b, AMOY, stubReader());
    assert.equal(r.ok, false);
    assert.ok(failed(r, 'leaf') || failed(r, 'anchor'));
  });

  test('sửa chữ ký → phục hồi ra địa chỉ khác', async () => {
    const b = bundle();
    const s = b.credential.signature;
    b.credential.signature = s.slice(0, 20) + (s[20] === 'a' ? 'b' : 'a') + s.slice(21);
    const r = await verifyBundle(b, AMOY, stubReader());
    assert.ok(failed(r, 'signature'));
  });

  test('sửa proof → không dẫn về root', async () => {
    const b = bundle();
    b.anchor.proof[0] = '0x' + '22'.repeat(32);
    const r = await verifyBundle(b, AMOY, stubReader());
    assert.ok(failed(r, 'anchor'));
  });

  test('bỏ bớt một sibling → không dẫn về root', async () => {
    const b = bundle();
    b.anchor.proof.pop();
    const r = await verifyBundle(b, AMOY, stubReader());
    assert.ok(failed(r, 'anchor'));
  });

  test('trỏ sang lô khác → lô đó chưa neo', async () => {
    const b = bundle();
    b.anchor.batchId = 2026080699;
    const r = await verifyBundle(b, AMOY, stubReader());
    assert.ok(failed(r, 'anchor'));
  });

  test('sửa `anchor.merkleRoot` KHÔNG giúp gì — verifier đọc root từ chuỗi', async () => {
    // Điểm cốt lõi: `merkleRoot` trong bundle chỉ để chẩn đoán. Sửa nó không đổi kết quả vì
    // phép đối chiếu dùng root ĐỌC TỪ CHUỖI. Bundle vẫn hợp lệ.
    const b = bundle();
    b.anchor.merkleRoot = '0x' + '33'.repeat(32);
    const r = await verifyBundle(b, AMOY, stubReader());
    assert.equal(r.ok, true, 'sửa trường chẩn đoán không được làm bundle hợp lệ thành không hợp lệ');
  });

  test('sửa statusListIndex sang bit chưa bật → leaf đổi', async () => {
    // Kịch bản thật: credential ĐÃ BỊ THU HỒI, người cầm nó trỏ verifier sang bit khác.
    // Chặn được vì statusListIndex nằm TRONG payload được ký và được neo.
    const b = bundle();
    b.credential.payload.statusListIndex = 999999;
    const r = await verifyBundle(b, AMOY, stubReader());
    assert.ok(failed(r, 'leaf'));
  });

  test('sửa issuerAddress sang ví của mình → leaf đổi', async () => {
    const b = bundle();
    b.credential.payload.issuerAddress = '0x1111111111111111111111111111111111111111';
    const r = await verifyBundle(b, AMOY, stubReader());
    assert.ok(failed(r, 'leaf'));
  });
});

// ------------------------------------------------------------------ đường tấn công rẻ nhất

describe('⚠️ Địa chỉ contract trong bundle KHÔNG được tin', () => {
  test('trỏ AnchorRegistry sang contract của kẻ tấn công → TỪ CHỐI', async () => {
    // Nếu phép kiểm này hỏng thì cả hệ thống hỏng: kẻ tấn công tự dựng cây Merkle chứa
    // credential giả, deploy một contract trả về root đó, rồi ghi địa chỉ vào bundle. Mọi
    // phép kiểm mật mã khác vẫn xanh.
    const b = bundle();
    b.chain.anchorRegistry = '0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef';

    // Reader "trung thực" theo nghĩa nó trả về root khớp proof — mô phỏng chính contract giả.
    const r = await verifyBundle(b, AMOY, stubReader());

    assert.equal(r.ok, false);
    assert.ok(failed(r, 'chain'));
    assert.match(r.checks.find((c) => c.id === 'chain').detail, /CONTRACT LẠ/);
  });

  test('đổi IssuerRegistry → TỪ CHỐI', async () => {
    const b = bundle();
    b.chain.issuerRegistry = '0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef';
    assert.ok(failed(await verifyBundle(b, AMOY, stubReader()), 'chain'));
  });

  test('đổi StatusList → TỪ CHỐI (nếu không thì thu hồi vô nghĩa)', async () => {
    const b = bundle();
    b.chain.statusList = '0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef';
    assert.ok(failed(await verifyBundle(b, AMOY, stubReader()), 'chain'));
  });

  test('đổi chainId → TỪ CHỐI', async () => {
    const b = bundle();
    b.chain.chainId = 1;
    assert.ok(failed(await verifyBundle(b, AMOY, stubReader()), 'chain'));
  });

  test('DỪNG NGAY sau khi chain fail — không in ra 5 dấu xanh trên 6', async () => {
    const b = bundle();
    b.chain.anchorRegistry = '0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef';
    const r = await verifyBundle(b, AMOY, stubReader());
    assert.deepEqual(r.checks.map((c) => c.id), ['format', 'chain']);
  });

  test('verifier không có danh sách tin cậy → TỪ CHỐI, không lấy tạm từ bundle', async () => {
    const r = await verifyBundle(bundle(), null, stubReader());
    assert.ok(failed(r, 'chain'));
  });

  test('viết hoa EIP-55 không làm phép so sánh fail', async () => {
    const b = bundle();
    b.chain.anchorRegistry = '0x4aC296Ad010233799bA3B91b8505269213503fAF';
    assert.ok(passed(await verifyBundle(b, AMOY, stubReader()), 'chain'));
  });
});

// ------------------------------------------------------------------ trạng thái chuỗi

describe('Trạng thái đọc từ chuỗi', () => {
  test('lô chưa neo → không xác minh được', async () => {
    const r = await verifyBundle(bundle(), AMOY, { ...stubReader(), getRoot: async () => null });
    assert.ok(failed(r, 'anchor'));
    assert.match(r.checks.find((c) => c.id === 'anchor').detail, /chưa được neo/);
  });

  test('bên cấp bị tắt quyền → không xác minh được', async () => {
    const r = await verifyBundle(bundle(), AMOY, stubReader({ activeIssuer: false }));
    assert.equal(r.ok, false);
    assert.ok(failed(r, 'issuer'));
    // Nhưng phần mật mã vẫn xanh — người đọc phải phân biệt được "bundle bị sửa" với
    // "trường đã rút quyền cấp của đơn vị này".
    assert.ok(passed(r, 'leaf') && passed(r, 'signature') && passed(r, 'anchor'));
  });

  test('credential đã bị thu hồi → không xác minh được, nhưng phần còn lại vẫn xanh', async () => {
    const r = await verifyBundle(bundle(), AMOY, stubReader({ revoked: true }));
    assert.equal(r.ok, false);
    assert.ok(failed(r, 'revocation'));
    assert.match(r.checks.find((c) => c.id === 'revocation').detail, /ĐÃ THU HỒI/);
    assert.ok(passed(r, 'leaf') && passed(r, 'signature') && passed(r, 'anchor'));
  });

  test('RPC chết → báo lỗi mạng, KHÔNG báo credential giả', async () => {
    const r = await verifyBundle(bundle(), AMOY, {
      async getRoot() { throw new Error('ECONNREFUSED'); },
      async isActiveIssuer() { throw new Error('ECONNREFUSED'); },
      async isRevoked() { throw new Error('ECONNREFUSED'); },
    });
    assert.equal(r.ok, false);
    for (const id of ['anchor', 'issuer', 'revocation']) {
      assert.match(r.checks.find((c) => c.id === id).detail, /không đọc được chuỗi/);
    }
    // Phần mật mã vẫn xanh: mất mạng không phải bằng chứng credential có vấn đề.
    assert.ok(passed(r, 'leaf') && passed(r, 'signature'));
  });
});

// ------------------------------------------------------------------ định dạng

describe('Định dạng phải vỡ ồn ào', () => {
  const cases = [
    ['không phải object', 'khong-phai-object'],
    ['format lạ', { ...FIXTURE.bundle, format: 'mot-thu-khac' }],
    ['version tương lai', { ...FIXTURE.bundle, version: 2 }],
    ['miền không phải CRED', (() => { const b = clone(FIXTURE.bundle); b.anchor.domain = 'ATTEND'; return b; })()],
    ['proof không phải mảng', (() => { const b = clone(FIXTURE.bundle); b.anchor.proof = '0xabc'; return b; })()],
    ['thiếu chữ ký', (() => { const b = clone(FIXTURE.bundle); delete b.credential.signature; return b; })()],
    ['thiếu mục anchor', (() => { const b = clone(FIXTURE.bundle); delete b.anchor; return b; })()],
  ];

  for (const [ten, b] of cases) {
    test(`${ten} → từ chối ở phép kiểm định dạng`, async () => {
      const r = await verifyBundle(b, AMOY, stubReader());
      assert.equal(r.ok, false);
      assert.ok(failed(r, 'format'), JSON.stringify(r.checks));
      assert.deepEqual(r.checks.map((c) => c.id), ['format'], 'phải dừng ngay');
    });
  }

  test('payload thừa trường lạ → từ chối ở phép kiểm leaf', async () => {
    const b = bundle();
    b.credential.payload.ghiChu = 'thêm cho vui';
    const r = await verifyBundle(b, AMOY, stubReader());
    assert.ok(failed(r, 'leaf'));
  });
});
