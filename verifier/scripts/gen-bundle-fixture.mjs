/**
 * Sinh `backend/src/test/resources/bundle-fixture.json`.
 *
 * Chạy: `npm run gen-bundle-fixture` (trong thư mục verifier/)
 *
 * BỘ VECTOR THỨ TƯ — và là bộ duy nhất mô tả một **tệp hoàn chỉnh** thay vì một phép tính.
 * Ba bộ trước chốt leaf, cây Merkle, và chữ ký. Bộ này chốt cái vỏ gói tất cả lại:
 *
 *   - phía JS: `verifier/test/bundle.test.mjs` xác minh fixture đầu-cuối và thử sửa từng chỗ
 *   - phía Java: `CredentialBundleDbTest` gieo đúng dữ liệu này xuống MySQL, gọi
 *     `CredentialBundleService`, và khẳng định bundle dựng ra KHỚP TỪNG TRƯỜNG với fixture
 *
 * Nếu backend dựng bundle khác đi một chút — thiếu trường, đổi tên trường, in số khác — test
 * Java đỏ. Không có bộ này thì hai phía trôi khỏi nhau và chỉ phát hiện lúc nhà tuyển dụng
 * mở tệp ra.
 *
 * ## Vì sao cây có 4 lá, và ba lá kia là gì
 *
 * Lô một lá là trường hợp biên (root chính là lá, proof rỗng) nên nó KHÔNG kiểm được phần
 * Merkle. Cây 4 lá cho proof dài 2 và có cả hai chiều ghép cặp.
 *
 * Ba lá còn lại là credential **của sinh viên khác** — đúng như trong lô thật. Đây chính là
 * chỗ `PROJECT.md` §2.3 nói tới: proof của một sinh viên chứa hash bản ghi người khác, và
 * chỉ có `nonce` 16 byte trong mỗi payload mới ngăn người cầm bundle vét cạn khôi phục
 * chúng.
 */
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { SigningKey, Signature } from 'ethers';

import { canonicalize } from '../src/jcs.mjs';
import { leafHash } from '../src/leaf.mjs';
import { normalizeCredPayload } from '../src/cred.mjs';
import { merkleRoot, merkleProof } from '../src/merkle.mjs';
import { AMOY } from '../src/trusted-chain.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT = resolve(HERE, '../../backend/src/test/resources/bundle-fixture.json');

/** Cùng khóa test công khai với `gen-cred-sig-vectors.mjs` — tài khoản #0 của Hardhat. */
const TEST_KEY = '0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80';
const ISSUER_ADDRESS = '0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266';

/** Lô 4 credential. Phần tử [0] là chủ thể của bundle; ba cái còn lại là sinh viên khác. */
const LO = [
  {
    credentialId: 41,
    type: 'HOAT_DONG',
    studentCode: 'B21DCCN042',
    studentName: 'Nguyễn Ngọc Hoàng',
    issuerOrgId: 3,
    issuerAddress: ISSUER_ADDRESS,
    issuedAt: '2026-08-06T02:00:00Z',
    expiresAt: null,
    statusListIndex: 731542,
    claims: { semester: '2026-1', activityCount: 12, totalPoints: 85 },
    nonce: '0x5c1d8e4a2f6b90c37e15a8d4b2f60931',
  },
  {
    credentialId: 42,
    type: 'HOAT_DONG',
    studentCode: 'B21DCCN043',
    studentName: 'Lê Thị Ánh Nguyệt',
    issuerOrgId: 3,
    issuerAddress: ISSUER_ADDRESS,
    issuedAt: '2026-08-06T02:00:00Z',
    expiresAt: null,
    statusListIndex: 18904,
    claims: { semester: '2026-1', activityCount: 7, totalPoints: 52 },
    nonce: '0xb7e34c1908af52d6e0c94b73a815f2d0',
  },
  {
    credentialId: 43,
    type: 'HOAT_DONG',
    studentCode: 'B21DCCN044',
    studentName: 'Trần Quốc Đạt',
    issuerOrgId: 3,
    issuerAddress: ISSUER_ADDRESS,
    issuedAt: '2026-08-06T02:00:00Z',
    expiresAt: null,
    statusListIndex: 950231,
    claims: { semester: '2026-1', activityCount: 3, totalPoints: 20 },
    nonce: '0x2f9a4b6c8d0e1f30425364758697a8b9',
  },
  {
    credentialId: 44,
    type: 'HOAT_DONG',
    studentCode: 'B21DCCN045',
    studentName: 'Phạm Vũ Hà My',
    issuerOrgId: 3,
    issuerAddress: ISSUER_ADDRESS,
    issuedAt: '2026-08-06T02:00:00Z',
    expiresAt: '2031-08-06T02:00:00Z',
    statusListIndex: 402317,
    claims: { semester: '2026-1', activityCount: 21, totalPoints: 98 },
    nonce: '0xc3d4e5f60718293a4b5c6d7e8f901122',
  },
];

const key = new SigningKey(TEST_KEY);

const leaves = LO.map((p) => leafHash('CRED', normalizeCredPayload(p)));
const root = merkleRoot(leaves);
const proof = merkleProof(leaves, 0);

const chuThe = LO[0];
const leaf = leaves[0];
const signature = Signature.from(key.sign(leaf)).serialized;

/** batchId theo quy ước YYYYMMDDnn — xem `AnchorBatchId.java`. */
const BATCH_ID = 2026080601;

const bundle = {
  format: 'drl-credential-bundle',
  version: 1,
  exportedAt: '2026-08-06T03:15:00Z',
  credential: {
    // Nhúng payload đã canonical hóa rồi phân tích lại: bảo đảm fixture mang ĐÚNG dạng mà
    // backend nhúng (`payload_json`), chứ không phải dạng viết tay ở trên.
    payload: JSON.parse(canonicalize(chuThe)),
    signature,
    leaf,
  },
  anchor: {
    domain: 'CRED',
    batchId: BATCH_ID,
    proof,
    merkleRoot: root,
    txHash: '0x' + 'ab'.repeat(32),
    blockNumber: 26150413,
    anchoredAt: '2026-08-06T02:05:00Z',
  },
  chain: {
    chainId: AMOY.chainId,
    anchorRegistry: AMOY.anchorRegistry,
    issuerRegistry: AMOY.issuerRegistry,
    statusList: AMOY.statusList,
  },
  doc: 'Xac minh doc lap: cd verifier && node scripts/verify-bundle.mjs <tep-nay>.'
    + ' Dia chi contract trong muc `chain` la THONG TIN, verifier dung danh sach'
    + ' tin cay cua rieng no.',
};

/**
 * Phần Java cần để gieo dữ liệu xuống MySQL rồi dựng lại đúng bundle này.
 *
 * Tách khỏi `bundle` để tệp fixture vẫn là một bundle HỢP LỆ nếu ai đó cắt phần này ra —
 * và để rõ ràng rằng nó không phải một trường của định dạng bundle.
 */
const duLieuGieo = {
  ghiChu: 'Java dung phan nay de gieo credentials + anchor_batches + anchor_leaves,'
    + ' roi khang dinh CredentialBundleService dung ra dung `bundle` o tren.',
  testPrivateKey: TEST_KEY,
  issuerAddress: ISSUER_ADDRESS,
  batchIdOnChain: BATCH_ID,
  merkleRoot: root,
  txHash: bundle.anchor.txHash,
  blockNumber: bundle.anchor.blockNumber,
  anchoredAt: bundle.anchor.anchoredAt,
  loDayDu: LO.map((p, i) => ({
    payload: JSON.parse(canonicalize(p)),
    payloadJcs: canonicalize(p),
    leaf: leaves[i],
    proof: merkleProof(leaves, i),
  })),
};

const doc = {
  $schema: 'khong-phai-JSON-Schema — bo fixture, doc docs/canonicalization.md truoc khi sua',
  spec: 'docs/canonicalization.md §13',
  warning:
    'File nay la HOP DONG giua backend Java va verifier JS. Test do = mot trong hai phia sai, '
    + 'KHONG phai file nay sai. Chi sinh lai bang `npm run gen-bundle-fixture` khi co y doi dac ta.',
  generatedBy: 'verifier/scripts/gen-bundle-fixture.mjs',
  bundle,
  duLieuGieo,
};

mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, JSON.stringify(doc, null, 2) + '\n', 'utf8');

console.log(`Da ghi fixture bundle -> ${OUT}\n`);
console.log(`  lo         ${LO.length} credential (chu the la #${chuThe.credentialId})`);
console.log(`  leaf       ${leaf}`);
console.log(`  proof      ${bundle.anchor.proof.length} sibling`);
console.log(`  root       ${root}`);
console.log(`  batchId    ${BATCH_ID}`);
console.log(`  signature  ${signature}`);
