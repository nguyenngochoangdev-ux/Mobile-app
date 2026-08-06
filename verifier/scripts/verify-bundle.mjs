#!/usr/bin/env node
/**
 * Xác minh một bundle credential từ dòng lệnh.
 *
 *   node scripts/verify-bundle.mjs <duong-dan-bundle.json>
 *   node scripts/verify-bundle.mjs bundle.json --offline
 *   node scripts/verify-bundle.mjs bundle.json --rpc https://...
 *
 * **Đây là mốc của tuần 4** (`PROJECT.md` §6): bundle verify được bằng script Node, không
 * chạm backend. Script này chỉ dùng `ethers` — không import gì từ `backend/`, không gọi HTTP
 * tới máy chủ của trường.
 *
 * Mã thoát: 0 khi xác minh được đầy đủ, 1 khi không. `--offline` không bao giờ trả 0 — nó
 * chứng minh bundle nhất quán về mặt mật mã, KHÔNG chứng minh credential đã neo hay còn
 * hiệu lực.
 */
import { readFileSync } from 'node:fs';
import { argv, exit, env } from 'node:process';

import { verifyBundle } from '../src/bundle.mjs';
import { chainReader } from '../src/chain.mjs';
import { trustedChainFor, DEFAULT_RPC_URL } from '../src/trusted-chain.mjs';

function usage(message) {
  if (message) console.error(`Lỗi: ${message}\n`);
  console.error('Cách dùng: node scripts/verify-bundle.mjs <bundle.json> [--offline] [--rpc URL]');
  exit(1);
}

const args = argv.slice(2);
const file = args.find((a) => !a.startsWith('--'));
const offline = args.includes('--offline');

const rpcIndex = args.indexOf('--rpc');
const rpcUrl = rpcIndex >= 0 ? args[rpcIndex + 1] : (env.RPC_URL || DEFAULT_RPC_URL);

if (!file) usage('thiếu đường dẫn tệp bundle.');

let bundle;
try {
  bundle = JSON.parse(readFileSync(file, 'utf8'));
} catch (e) {
  usage(`không đọc được ${file}: ${e.message}`);
}

// Danh sách tin cậy lấy từ MÃ NGUỒN của verifier, không lấy từ bundle. `bundle.chain.chainId`
// chỉ dùng để CHỌN cấu hình nào đem ra đối chiếu — bản thân nó không được tin, và nếu nó trỏ
// tới một mạng verifier không biết thì dừng luôn. Xem src/trusted-chain.mjs.
let trusted;
try {
  trusted = trustedChainFor(bundle?.chain?.chainId);
} catch (e) {
  console.error(`✗ ${e.message}`);
  exit(1);
}

const reader = offline ? null : chainReader(rpcUrl, trusted);

const result = await verifyBundle(bundle, trusted, reader);

// ------------------------------------------------------------------ in kết quả

const p = bundle.credential?.payload ?? {};
console.log('');
console.log(`  Credential #${p.credentialId} · ${p.type}`);
console.log(`  Cấp cho     ${p.studentName} (${p.studentCode})`);
console.log(`  Học kỳ      ${p.claims?.semester} · ${p.claims?.activityCount} hoạt động`
  + ` · ${p.claims?.totalPoints} điểm`);
console.log(`  Cấp ngày    ${p.issuedAt}${p.expiresAt ? ` · hết hạn ${p.expiresAt}` : ' · không hạn'}`);
console.log(`  Neo tại     ${bundle.anchor?.domain}/${bundle.anchor?.batchId}`
  + (bundle.anchor?.txHash ? ` · tx ${bundle.anchor.txHash}` : ''));
console.log(`  Mạng        ${trusted.name} (chainId ${trusted.chainId})`
  + (offline ? '' : ` · RPC ${rpcUrl}`));
console.log('');

for (const c of result.checks) {
  const dau = c.skipped ? '–' : (c.pass ? '✓' : '✗');
  console.log(`  ${dau} ${c.label}`);
  console.log(`      ${c.detail}`);
}

console.log('');
if (result.ok) {
  console.log(`  ✓ ${result.summary}`);
} else if (result.offline) {
  console.log(`  – ${result.summary}`);
  console.log('      Bỏ --offline để kiểm cả phần neo, quyền cấp và thu hồi.');
} else {
  console.log(`  ✗ ${result.summary}`);
}
console.log('');

exit(result.ok ? 0 : 1);
