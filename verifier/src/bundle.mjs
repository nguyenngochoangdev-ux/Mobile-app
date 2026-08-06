/**
 * Xác minh bundle credential — **không gọi backend một dòng nào**.
 * Nửa Java: `backend/.../credential/CredentialBundleService.java`.
 *
 * Đây là chỗ hiện thực **luận điểm 2** (`PROJECT.md` §10): hồ sơ vẫn xác minh được sau khi
 * sinh viên rời trường, kể cả khi trường đã tắt máy chủ **và** ngừng trả tiền cho mọi dịch
 * vụ RPC. Toàn bộ dữ liệu cần thiết nằm trong tệp bundle cộng với ba `eth_call` trên một
 * endpoint công cộng không cần API key.
 *
 * ## Sáu phép kiểm, và thứ tự của chúng
 *
 * | # | Kiểm gì | Cần mạng |
 * |---|---|---|
 * | 1 | Bundle đúng định dạng và đúng phiên bản | không |
 * | 2 | `chain.*` khớp danh sách TIN CẬY của verifier | không |
 * | 3 | Leaf tính lại từ payload khớp `credential.leaf` | không |
 * | 4 | Chữ ký phục hồi ra đúng `issuerAddress` trong payload | không |
 * | 5 | Merkle proof dẫn về root **đọc từ chuỗi** | có |
 * | 6 | Bên cấp còn quyền, và credential chưa bị thu hồi | có |
 *
 * Bốn phép đầu chạy được hoàn toàn offline, không cần mạng. Chỉ 5 và 6 cần chuỗi.
 *
 * ## ⚠️ Điều quan trọng nhất trong file này
 *
 * **Địa chỉ contract KHÔNG được lấy từ bundle.** Người cầm bundle là người có động cơ sửa nó.
 * Nếu verifier đọc `getRoot` từ địa chỉ ghi trong bundle thì kẻ tấn công chỉ cần deploy một
 * contract của mình, cho nó trả về root khớp cây Merkle mình tự dựng, rồi ghi địa chỉ đó vào
 * bundle — mọi phép kiểm khác vẫn xanh. **Đây là cách phá hệ thống rẻ nhất nếu làm sai.**
 *
 * Verifier vì thế nhận `trustedChain` từ bên gọi (hằng số trong mã, hoặc người dùng nhập) và
 * phép kiểm #2 **từ chối** bundle nào khai khác. Mục `chain` trong bundle là thông tin để
 * chẩn đoán, không phải cấu hình.
 *
 * ## Vì sao trả về danh sách phép kiểm chứ không phải true/false
 *
 * "Không xác minh được" có ít nhất sáu nguyên nhân rất khác nhau, và người dùng cần biết là
 * cái nào: credential bị thu hồi là chuyện khác hẳn với bundle bị sửa. Một hàm trả `false`
 * biến cả sáu thành một.
 */
import { recoverAddress } from 'ethers';

import { canonicalize } from './jcs.mjs';
import { leafHash } from './leaf.mjs';
import { verifyProof } from './merkle.mjs';
import { normalizeCredPayload } from './cred.mjs';

export const BUNDLE_FORMAT = 'drl-credential-bundle';
export const BUNDLE_VERSION = 1;

/** Kết quả một phép kiểm. `pass` là true/false; `skipped` khi không chạy được. */
function check(id, label, pass, detail, skipped = false) {
  return { id, label, pass, detail, skipped };
}

/**
 * Xác minh một bundle.
 *
 * @param bundle       đối tượng đã `JSON.parse`
 * @param trustedChain `{ chainId, anchorRegistry, issuerRegistry, statusList }` — địa chỉ
 *                     TIN CẬY của verifier. **Không bao giờ lấy từ bundle.**
 * @param reader       bộ đọc chuỗi (`chainReader` từ `chain.mjs`), hoặc `null` để chạy chế độ
 *                     hoàn toàn offline — khi đó phép kiểm 5 và 6 bị đánh dấu `skipped`.
 * @returns `{ ok, offline, checks[], summary }`. `ok` chỉ true khi MỌI phép kiểm pass và
 *          không phép nào bị bỏ qua.
 */
export async function verifyBundle(bundle, trustedChain, reader = null) {
  const checks = [];
  const add = (c) => {
    checks.push(c);
    return c.pass;
  };

  // ---- 1. Định dạng -------------------------------------------------------
  if (!add(checkFormat(bundle))) {
    return finish(checks, reader);
  }

  // ---- 2. Địa chỉ contract khớp danh sách tin cậy --------------------------
  //
  // Chạy TRƯỚC mọi phép kiểm chạm chuỗi, và dừng hẳn nếu hỏng. Không phải để tiết kiệm lời
  // gọi mạng: nếu đi tiếp mà chỉ ghi nhận "phép kiểm 2 fail", có ngày ai đó đọc kết quả và
  // thấy 5 dấu xanh trên 6 rồi kết luận "gần như hợp lệ". Bundle khai sai địa chỉ contract
  // KHÔNG phải "gần như hợp lệ" — nó là dấu hiệu của đúng một thứ.
  if (!add(checkTrustedChain(bundle.chain, trustedChain))) {
    return finish(checks, reader);
  }

  // ---- 3. Leaf tính lại từ payload ----------------------------------------
  let leaf;
  try {
    const payload = normalizeCredPayload(bundle.credential.payload);
    leaf = leafHash('CRED', payload);

    // Kiểm phụ gần như miễn phí: payload nhúng trong bundle phải ĐÃ ở dạng chuẩn tắc, vì nó
    // là đúng chuỗi backend đã bam và đã ký. JCS là idempotent nên canonicalize lại phải ra
    // chính nó. Lệch nghĩa là bundle đi qua một công cụ nào đó đã in lại JSON.
    const lai = canonicalize(payload);
    const nhan = JSON.stringify(bundle.credential.payload);
    const canonicalOk = lai === nhan;

    add(check('leaf', 'Leaf hash tính lại từ payload',
      leaf === bundle.credential.leaf,
      leaf === bundle.credential.leaf
        ? `leaf = ${leaf}` + (canonicalOk ? '' : ' (payload không ở dạng chuẩn tắc — vẫn hợp lệ)')
        : `bundle khai ${bundle.credential.leaf} nhưng tính ra ${leaf}`));
  } catch (e) {
    add(check('leaf', 'Leaf hash tính lại từ payload', false, `payload không hợp lệ: ${e.message}`));
    return finish(checks, reader);
  }

  // ---- 4. Chữ ký ----------------------------------------------------------
  const issuerAddress = bundle.credential.payload.issuerAddress;
  let recovered = null;
  try {
    recovered = recoverAddress(leaf, bundle.credential.signature).toLowerCase();
  } catch (e) {
    add(check('signature', 'Chữ ký của bên cấp', false, `không đọc được chữ ký: ${e.message}`));
  }
  if (recovered !== null) {
    add(check('signature', 'Chữ ký của bên cấp',
      recovered === issuerAddress,
      recovered === issuerAddress
        ? `ký bởi ${recovered}`
        : `chữ ký thuộc về ${recovered} nhưng payload khai ${issuerAddress}`));
  }

  // ---- 5 và 6 cần chuỗi ---------------------------------------------------
  if (!reader) {
    checks.push(check('anchor', 'Merkle proof dẫn về root trên chuỗi', false,
      'bỏ qua — chạy ở chế độ offline', true));
    checks.push(check('issuer', 'Bên cấp còn quyền trong IssuerRegistry', false,
      'bỏ qua — chạy ở chế độ offline', true));
    checks.push(check('revocation', 'Credential chưa bị thu hồi', false,
      'bỏ qua — chạy ở chế độ offline', true));
    return finish(checks, reader);
  }

  // ---- 5. Proof dẫn về root ĐỌC TỪ CHUỖI ----------------------------------
  try {
    const { domain, batchId, proof } = bundle.anchor;
    const rootOnChain = await reader.getRoot(domain, batchId);

    if (rootOnChain === null) {
      add(check('anchor', 'Merkle proof dẫn về root trên chuỗi', false,
        `lô (${domain}, ${batchId}) chưa được neo — getRoot trả về rỗng`));
    } else {
      // Root dùng để đối chiếu là root ĐỌC TỪ CHUỖI, không phải bundle.anchor.merkleRoot.
      // Trường đó chỉ để chẩn đoán và được báo riêng bên dưới.
      const ok = verifyProof(leaf, proof, rootOnChain);
      const noiDoi = bundle.anchor.merkleRoot !== rootOnChain;

      add(check('anchor', 'Merkle proof dẫn về root trên chuỗi', ok,
        ok
          ? `root ${rootOnChain} · lô ${domain}/${batchId} · ${proof.length} sibling`
          : `proof không dẫn về root trên chuỗi (${rootOnChain})`
            + (noiDoi ? ` — và bundle khai root khác: ${bundle.anchor.merkleRoot}` : '')));
    }
  } catch (e) {
    add(check('anchor', 'Merkle proof dẫn về root trên chuỗi', false,
      `không đọc được chuỗi: ${e.message}`));
  }

  // ---- 6. Quyền của bên cấp, và trạng thái thu hồi -------------------------
  try {
    const active = await reader.isActiveIssuer(issuerAddress);
    add(check('issuer', 'Bên cấp còn quyền trong IssuerRegistry', active,
      active
        ? `${issuerAddress} đang được phép cấp`
        : `${issuerAddress} KHÔNG có quyền cấp (chưa đăng ký, hoặc đã bị tắt)`));
  } catch (e) {
    add(check('issuer', 'Bên cấp còn quyền trong IssuerRegistry', false,
      `không đọc được chuỗi: ${e.message}`));
  }

  try {
    const index = bundle.credential.payload.statusListIndex;
    const revoked = await reader.isRevoked(index);
    add(check('revocation', 'Credential chưa bị thu hồi', !revoked,
      revoked
        ? `ĐÃ THU HỒI — bit ${index} trên StatusList đã bật`
        : `còn hiệu lực — bit ${index} chưa bật`));
  } catch (e) {
    add(check('revocation', 'Credential chưa bị thu hồi', false,
      `không đọc được chuỗi: ${e.message}`));
  }

  return finish(checks, reader);
}

// ------------------------------------------------------------------ phép kiểm rời

function checkFormat(bundle) {
  if (bundle === null || typeof bundle !== 'object' || Array.isArray(bundle)) {
    return check('format', 'Định dạng bundle', false, 'không phải một object JSON');
  }
  if (bundle.format !== BUNDLE_FORMAT) {
    return check('format', 'Định dạng bundle', false,
      `không phải bundle của hệ thống này (format = ${JSON.stringify(bundle.format)})`);
  }
  if (bundle.version !== BUNDLE_VERSION) {
    // Từ chối thay vì "cố đọc xem sao": phiên bản khác nghĩa là lược đồ payload có thể đã
    // đổi, và đọc nhầm lược đồ cho ra leaf khác — tức là báo credential thật là giả.
    return check('format', 'Định dạng bundle', false,
      `phiên bản ${bundle.version} không đọc được; verifier này hiểu phiên bản ${BUNDLE_VERSION}`);
  }

  for (const [path, value] of [
    ['credential', bundle.credential],
    ['credential.payload', bundle.credential?.payload],
    ['credential.signature', bundle.credential?.signature],
    ['credential.leaf', bundle.credential?.leaf],
    ['anchor', bundle.anchor],
    ['anchor.domain', bundle.anchor?.domain],
    ['anchor.batchId', bundle.anchor?.batchId],
    ['anchor.proof', bundle.anchor?.proof],
    ['chain', bundle.chain],
  ]) {
    if (value === undefined || value === null) {
      return check('format', 'Định dạng bundle', false, `thiếu trường \`${path}\``);
    }
  }
  if (!Array.isArray(bundle.anchor.proof)) {
    return check('format', 'Định dạng bundle', false, '`anchor.proof` phải là một mảng');
  }
  if (bundle.anchor.domain !== 'CRED') {
    return check('format', 'Định dạng bundle', false,
      `bundle credential phải thuộc miền CRED, nhận được ${JSON.stringify(bundle.anchor.domain)}`);
  }

  return check('format', 'Định dạng bundle', true,
    `${BUNDLE_FORMAT} v${BUNDLE_VERSION}`);
}

/**
 * Địa chỉ contract trong bundle phải khớp danh sách tin cậy của verifier.
 *
 * Xem cảnh báo ở đầu file: đây là phép kiểm chặn đường tấn công rẻ nhất vào cả hệ thống.
 */
function checkTrustedChain(chain, trusted) {
  if (!trusted || !trusted.anchorRegistry || !trusted.issuerRegistry || !trusted.statusList) {
    return check('chain', 'Địa chỉ contract khớp danh sách tin cậy', false,
      'verifier chưa được cấu hình địa chỉ tin cậy — KHÔNG được lấy tạm từ bundle');
  }

  const lech = [];
  if (Number(chain.chainId) !== Number(trusted.chainId)) {
    lech.push(`chainId: bundle ${chain.chainId} ≠ tin cậy ${trusted.chainId}`);
  }
  for (const ten of ['anchorRegistry', 'issuerRegistry', 'statusList']) {
    const a = String(chain[ten] ?? '').toLowerCase();
    const b = String(trusted[ten]).toLowerCase();
    if (a !== b) {
      lech.push(`${ten}: bundle ${a || '(thiếu)'} ≠ tin cậy ${b}`);
    }
  }

  if (lech.length > 0) {
    return check('chain', 'Địa chỉ contract khớp danh sách tin cậy', false,
      'BUNDLE TRỎ SANG CONTRACT LẠ — ' + lech.join(' · '));
  }
  return check('chain', 'Địa chỉ contract khớp danh sách tin cậy', true,
    `chainId ${trusted.chainId} · AnchorRegistry ${String(trusted.anchorRegistry).toLowerCase()}`);
}

function finish(checks, reader) {
  const skipped = checks.filter((c) => c.skipped).length;
  const failed = checks.filter((c) => !c.pass && !c.skipped);

  return {
    // `ok` đòi MỌI phép kiểm pass VÀ không phép nào bị bỏ qua. Chế độ offline không bao giờ
    // cho ra `ok: true` — nó chứng minh bundle nội bộ nhất quán, KHÔNG chứng minh credential
    // đã được neo hay còn hiệu lực. Gộp hai thứ đó là nói dối người dùng.
    ok: failed.length === 0 && skipped === 0,
    offline: reader === null,
    checks,
    summary: failed.length === 0
      ? (skipped === 0
        ? 'Xác minh được đầy đủ.'
        : `Nhất quán về mặt mật mã, nhưng ${skipped} phép kiểm cần chuỗi chưa chạy.`)
      : `Không xác minh được: ${failed.map((c) => c.label).join(' · ')}.`,
  };
}
