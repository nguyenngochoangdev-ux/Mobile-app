/**
 * Sinh `backend/src/test/resources/audit-chain-vectors.json`.
 *
 * Chạy: `npm run gen-audit-chain-vectors` (trong thư mục verifier/)
 *
 * BỘ VECTOR THỨ NĂM. Nó chốt thứ mà bốn bộ trước không chạm tới: **mắt xích** của chuỗi băm
 * nhật ký, tức công thức
 *
 *     hash_i = keccak256( prevHash_i(32 byte) ‖ UTF-8(JCS(record_i)) )
 *
 * Khác `leaf`, mắt xích nối các bản ghi **với nhau**. Lệch công thức làm cả chuỗi đứt ngay từ
 * bản ghi thứ hai — nhưng lệch **im lặng**: mỗi bản ghi vẫn có một `hash` trông hợp lệ, chỉ
 * là không khớp `prevHash` của bản sau. Và vì chuỗi này là thứ hiện thực luận điểm 1, lệch ở
 * đây nghĩa là mất luôn khả năng chứng minh dữ liệu không bị sửa hồi tố.
 *
 * File này chứa **một chuỗi 5 bản ghi liền lạc** cộng **các biến thể đã bị phá**, để cả hai
 * phía chứng minh được là chúng thật sự PHÁT HIỆN ra chứ không chỉ tính đúng khi mọi thứ ổn.
 */
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { canonicalize } from '../src/jcs.mjs';
import { leafHash } from '../src/leaf.mjs';
import { chainHash, hashOfJson, normalizeAuditPayload, verifyChain } from '../src/audit.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT = resolve(HERE, '../../backend/src/test/resources/audit-chain-vectors.json');

/**
 * Năm sự kiện, đúng thứ tự xảy ra trong một luồng thật của đề tài.
 *
 * `before`/`after` là chuỗi JSON **nguyên văn** — chính chúng, chứ không phải một dạng chuẩn
 * hoá nào, là thứ được băm. Cố ý viết một cái có khoảng trắng lạ và một cái có tiếng Việt có
 * dấu để chốt rằng byte thô mới là thứ quan trọng.
 */
const SU_KIEN = [
  {
    action: 'DEVICE_APPROVE',
    entity: 'student_devices',
    entityId: 12,
    actorId: 3,
    at: '2026-08-04T02:10:00Z',
    before: '{"status":"PENDING"}',
    after: '{"status":"ACTIVE","approvedBy":3}',
    nonce: '0x9f86d081884c7d659a2feaa0c55ad015',
  },
  {
    action: 'ATTENDANCE_MANUAL',
    entity: 'attendances',
    entityId: 5,
    actorId: 3,
    at: '2026-08-04T10:57:04Z',
    before: null,
    // Tiếng Việt có dấu: byte UTF-8 thô đi thẳng vào keccak, không escape.
    after: '{"method":"MANUAL","verified":false,"ghiChu":"Sinh viên quên điện thoại"}',
    nonce: '0x3b8c1f42a7d90e5643f1c2b8a90d7e64',
  },
  {
    action: 'CREDENTIAL_ISSUE',
    entity: 'credentials',
    entityId: 81,
    // Hệ thống tự làm — actorId null.
    actorId: null,
    at: '2026-08-06T03:28:20Z',
    before: null,
    after: '{"studentCode":"B21DCCN002","activityCount":3,"totalPoints":15}',
    nonce: '0x74a40e0f493b112d51f09ade5cab9d44',
  },
  {
    action: 'ANCHOR_BATCH',
    entity: 'anchor_batches',
    entityId: 2,
    actorId: null,
    at: '2026-08-06T03:37:01Z',
    before: null,
    // Khoảng trắng lạ, cố ý: nó phải được giữ nguyên vì keccak băm byte thô.
    after: '{ "domain": "CRED",  "batchId": 2026080601 }',
    nonce: '0x0403c6481c937c9f1ffacbf727fbe58b',
  },
  {
    action: 'CREDENTIAL_REVOKE',
    entity: 'credentials',
    entityId: 81,
    actorId: 3,
    at: '2026-08-06T05:00:00Z',
    before: '{"revokedAt":null}',
    after: '{"revokedAt":"2026-08-06T05:00:00Z","reason":"Cap nham hoc ky"}',
    nonce: '0xdeadbeefcafebabe0123456789abcdef',
  },
];

// ------------------------------------------------------------------ dựng chuỗi

const chuoi = [];
let prevHash = null;

for (let i = 0; i < SU_KIEN.length; i++) {
  const e = SU_KIEN[i];

  const payload = {
    seq: i + 1,
    action: e.action,
    entity: e.entity,
    entityId: e.entityId,
    actorId: e.actorId,
    at: e.at,
    beforeHash: hashOfJson(e.before),
    afterHash: hashOfJson(e.after),
    prevHash,
    // Chỗ giữ chỗ; điền ngay dưới. `hash` không phụ thuộc chính nó nên không có vòng tròn.
    hash: null,
    nonce: e.nonce,
  };

  payload.hash = chainHash(payload);
  prevHash = payload.hash;

  chuoi.push({
    seq: payload.seq,
    beforeJson: e.before,
    afterJson: e.after,
    payload,
    expected: {
      chainRecordJcs: canonicalize({
        action: payload.action,
        actorId: payload.actorId,
        afterHash: payload.afterHash,
        at: payload.at,
        beforeHash: payload.beforeHash,
        entity: payload.entity,
        entityId: payload.entityId,
      }),
      hash: payload.hash,
      leaf: leafHash('AUDIT', normalizeAuditPayload(payload)),
    },
  });
}

const kiemTra = verifyChain(chuoi.map((c) => c.payload));
if (!kiemTra.nguyenVen) {
  throw new Error('Chuoi vua sinh da khong nguyen ven: ' + JSON.stringify(kiemTra.loi));
}

// ------------------------------------------------------------------ biến thể bị phá

const clone = (o) => JSON.parse(JSON.stringify(o));
const goc = () => chuoi.map((c) => clone(c.payload));

/**
 * Mỗi biến thể phải bị `verifyChain` TỪ CHỐI. Phần này quan trọng ngang phần chuỗi hợp lệ:
 * một hàm `verifyChain` luôn trả `nguyenVen: true` cũng làm mọi test chuỗi hợp lệ xanh.
 */
const PHA = [
  {
    id: 'sua-noi-dung',
    why: 'Sửa `entityId` của bản ghi giữa chuỗi mà quên tính lại hash. Đây là kiểu sửa vụng về nhất, và cũng là kiểu hay gặp nhất khi ai đó sửa thẳng bằng SQL.',
    payloads: (() => { const c = goc(); c[2].entityId = 999; return c; })(),
    expectedError: 'NỘI DUNG BỊ SỬA',
  },
  {
    id: 'sua-noi-dung-va-tinh-lai-hash',
    why: 'Sửa nội dung VÀ tính lại hash cho bản ghi đó — hash của chính nó khớp, nhưng prevHash của bản ghi SAU thì không. Đây là lý do phải kiểm cả hai thứ, không chỉ hash.',
    payloads: (() => {
      const c = goc();
      c[2].entityId = 999;
      c[2].hash = chainHash(c[2]);
      return c;
    })(),
    expectedError: 'ĐỨT XÍCH',
  },
  {
    id: 'xoa-mot-ban-ghi-giua-chuoi',
    why: 'Gỡ hẳn một bản ghi ra. Không có phép kiểm prevHash thì việc này hoàn toàn vô hình.',
    payloads: (() => { const c = goc(); c.splice(2, 1); return c; })(),
    expectedError: 'ĐỨT XÍCH',
  },
  {
    id: 'chen-ban-ghi-la',
    why: 'Chèn thêm một bản ghi giả vào giữa.',
    payloads: (() => {
      const c = goc();
      const gia = clone(c[1]);
      gia.seq = 25;
      gia.action = 'CREDENTIAL_ISSUE';
      gia.hash = chainHash(gia);
      c.splice(2, 0, gia);
      return c;
    })(),
    expectedError: 'ĐỨT XÍCH',
  },
  {
    id: 'cat-chuoi-roi-bat-dau-lai',
    why: 'Bỏ hai bản ghi đầu rồi đặt prevHash của bản ghi mới đầu tiên thành null, làm như nó là gốc chuỗi. Bắt được vì bên gọi biết đây có phải đầu chuỗi thật hay không.',
    payloads: (() => {
      const c = goc().slice(2);
      c[0].prevHash = null;
      c[0].hash = chainHash(c[0]);
      return c;
    })(),
    expectedError: 'ĐỨT XÍCH',
    laDauChuoi: false,
  },
  {
    id: 'doi-thu-tu',
    why: 'Đảo hai bản ghi liền nhau.',
    payloads: (() => { const c = goc(); [c[1], c[2]] = [c[2], c[1]]; return c; })(),
    expectedError: 'ĐỨT XÍCH',
  },
];

for (const p of PHA) {
  const kq = verifyChain(p.payloads, p.laDauChuoi !== false);
  if (kq.nguyenVen) {
    throw new Error(`Bien the "${p.id}" LE RA phai bi tu choi nhung verifyChain bao nguyen ven.`);
  }
}

// ------------------------------------------------------------------ ghi file

const doc = {
  $schema: 'khong-phai-JSON-Schema — bo test vector, doc docs/canonicalization.md truoc khi sua',
  spec: 'docs/canonicalization.md §14',
  formula: 'hash = keccak256( prevHash(32 byte) || UTF-8(JCS(record)) ); prevHash NULL -> 32 byte 0x00',
  warning:
    'File nay la HOP DONG giua backend Java va verifier JS. Test do = mot trong hai phia sai, '
    + 'KHONG phai file nay sai. Chi sinh lai bang `npm run gen-audit-chain-vectors` khi co y doi dac ta.',
  generatedBy: 'verifier/scripts/gen-audit-chain-vectors.mjs',
  genesisPrevHash: '0x' + '00'.repeat(32),
  chuoi,
  pha: PHA.map((p) => ({
    id: p.id,
    why: p.why,
    laDauChuoi: p.laDauChuoi !== false,
    expectedError: p.expectedError,
    payloads: p.payloads,
  })),
};

mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, JSON.stringify(doc, null, 2) + '\n', 'utf8');

console.log(`Da ghi chuoi ${chuoi.length} mat xich + ${PHA.length} bien the bi pha -> ${OUT}\n`);
for (const c of chuoi) {
  console.log(`  #${String(c.seq).padEnd(2)} ${c.payload.action.padEnd(20)} hash ${c.expected.hash}`);
}
