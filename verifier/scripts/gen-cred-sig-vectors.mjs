/**
 * Sinh `backend/src/test/resources/cred-signature-vectors.json`.
 *
 * Chạy: `npm run gen-cred-sig-vectors` (trong thư mục verifier/)
 *
 * BỘ VECTOR THỨ BA. Hai bộ trước chốt việc hai phía tính ra CÙNG MỘT leaf
 * (`canonical-vectors.json`) và CÙNG MỘT cây Merkle (`merkle-vectors.json`). Bộ này chốt
 * mắt xích còn lại: hai phía đọc CÙNG MỘT chữ ký ra CÙNG MỘT địa chỉ ví.
 *
 * Vì sao cần bộ riêng thay vì tin là "ECDSA thì ở đâu cũng thế": chữ ký secp256k1 có ít nhất
 * bốn chỗ hai thư viện lệch nhau được, và cả bốn đều fail im lặng vì phục hồi địa chỉ luôn
 * trả về MỘT địa chỉ nào đó chứ không báo lỗi —
 *
 *   1. có băm lại thông điệp trước khi ký hay không (web3j: cờ `needToHash`)
 *   2. có thêm tiền tố EIP-191 "\x19Ethereum Signed Message:\n32" hay không
 *   3. `v` là 27/28 hay 0/1 hay đã cộng chainId theo EIP-155
 *   4. `s` có được chuẩn hóa về nửa dưới của đường cong hay không
 *
 * Chọn sai bất kỳ cái nào là verifier phục hồi ra một địa chỉ rác, rồi hỏi IssuerRegistry về
 * địa chỉ rác đó và nhận về "không có quyền" — trông y hệt như credential giả.
 */
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { SigningKey, computeAddress, recoverAddress, Signature } from 'ethers';
import { leafHash } from '../src/leaf.mjs';
import { normalizeCredPayload } from '../src/cred.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT = resolve(HERE, '../../backend/src/test/resources/cred-signature-vectors.json');

/**
 * Khóa TEST công khai — tài khoản #0 mặc định của Hardhat, ai cũng biết.
 * Cố ý dùng khóa công khai để file này commit được mà không phải nghĩ ngợi.
 * KHÔNG BAO GIỜ dùng cho ISSUER_PRIVATE_KEY thật.
 */
const TEST_KEY = '0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80';

/** Payload phải khớp `cred-payload-*` trong canonical-vectors.json — cùng lược đồ. */
const PAYLOADS = [
  {
    id: 'sig-cred-day-du',
    why: 'Credential đầy đủ, tên có dấu tiếng Việt. Chữ ký trên chính leaf hash, không băm lại, không tiền tố EIP-191.',
    payload: {
      credentialId: 17,
      type: 'HOAT_DONG',
      studentCode: 'B21DCCN042',
      studentName: 'Nguyễn Ngọc Hoàng',
      issuerOrgId: 3,
      issuerAddress: '0xf32728c5c2d0575ea406ad37e2467916c89f529f',
      issuedAt: '2026-08-06T10:00:00Z',
      expiresAt: '2031-08-06T10:00:00Z',
      statusListIndex: 731542,
      claims: { totalPoints: 85, semester: '2026-1', activityCount: 12 },
      nonce: '0x5c1d8e4a2f6b90c37e15a8d4b2f60931',
    },
  },
  {
    id: 'sig-cred-khong-han',
    why: 'Bản không hạn dùng, các số bằng 0. Leaf khác nên chữ ký khác — chốt rằng chữ ký gắn với NỘI DUNG, không phải với khóa.',
    payload: {
      credentialId: 18,
      type: 'HOAT_DONG',
      studentCode: 'B21DCCN199',
      studentName: 'Lê Thị Ánh Nguyệt',
      issuerOrgId: 3,
      issuerAddress: '0xf32728c5c2d0575ea406ad37e2467916c89f529f',
      issuedAt: '2026-08-06T10:00:00Z',
      expiresAt: null,
      statusListIndex: 4,
      claims: { totalPoints: 0, semester: '2026-1', activityCount: 0 },
      nonce: '0xb7e34c1908af52d6e0c94b73a815f2d0',
    },
  },
];

const key = new SigningKey(TEST_KEY);
const signerAddress = computeAddress(key.publicKey);

const vectors = PAYLOADS.map((v) => {
  const leaf = leafHash('CRED', normalizeCredPayload(v.payload));
  const sig = key.sign(leaf);

  // Dạng nối 65 byte r||s||v mà backend lưu vào cột `signature`.
  const packed = Signature.from(sig).serialized;

  const recovered = recoverAddress(leaf, sig);
  if (recovered !== signerAddress) {
    throw new Error(`Phuc hoi dia chi sai ngay o phia JS: ${recovered} != ${signerAddress}`);
  }

  return {
    id: v.id,
    why: v.why,
    domain: 'CRED',
    payload: v.payload,
    expected: {
      leaf,
      signature: packed,
      signatureBytes: packed.length / 2 - 1,
      r: sig.r,
      s: sig.s,
      v: sig.v,
      signerAddress: signerAddress.toLowerCase(),
    },
  };
});

const doc = {
  $schema: 'khong-phai-JSON-Schema — bo test vector, doc docs/canonicalization.md truoc khi sua',
  spec: 'docs/canonicalization.md §12',
  formula: 'sig = ECDSA_secp256k1( leaf ), 65 byte r||s||v, v thuoc {27,28}. KHONG bam lai, KHONG tien to EIP-191.',
  warning:
    'File nay la HOP DONG giua backend Java va verifier JS. Test do = mot trong hai phia sai, ' +
    'KHONG phai file nay sai. Chi sinh lai bang `npm run gen-cred-sig-vectors` khi co y doi dac ta.',
  testPrivateKey: TEST_KEY,
  testPrivateKeyNote:
    'Tai khoan #0 mac dinh cua Hardhat — khoa CONG KHAI, ai cung biet. Dung de hai phia ky cung ' +
    'mot thu va so tung byte. KHONG BAO GIO dung lam ISSUER_PRIVATE_KEY that.',
  generatedBy: 'verifier/scripts/gen-cred-sig-vectors.mjs',
  vectors,
};

mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, JSON.stringify(doc, null, 2) + '\n', 'utf8');

console.log(`Da ghi ${vectors.length} vector chu ky -> ${OUT}\n`);
console.log(`  vi ky: ${signerAddress}\n`);
for (const v of vectors) {
  console.log(`  ${v.id.padEnd(20)} leaf ${v.expected.leaf}`);
  console.log(`  ${''.padEnd(20)} sig  ${v.expected.signature}`);
}
