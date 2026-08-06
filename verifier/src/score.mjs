/**
 * Điểm rèn luyện và bộ quy tắc — nửa JavaScript.
 * Nửa Java: `backend/.../scoring/{ScorePayload,RulesetPayload,EvidenceHasher}.java`.
 *
 * Ba công thức, ba mục đích:
 *
 *   evidenceHash = keccak256( UTF-8(JCS({"domain":"ATTEND","leaves":[...đã sắp xếp...]})) )
 *   rulesetHash  = keccak256( UTF-8(chính byte của tệp bộ quy tắc) )
 *   leaf         = keccak256( bytes8('SCORE'|'RULESET') ‖ ':' ‖ UTF-8(JCS(payload)) )
 *
 * `evidenceHash` trả lời "chấm trên dữ liệu nào"; `rulesetHash` trả lời "chấm bằng quy tắc
 * nào". Có cả hai thì một điểm số **tái tính lại được bởi người ngoài** mà không cần máy chủ
 * của trường — đó là đóng góp học thuật rõ nhất của đề tài.
 *
 * Test vector chung: `canonical-vectors.json`, tiền tố `score-payload` và `ruleset-payload`.
 * **Sửa file này phải đi kèm `/canonical-hash` và chạy lại test CẢ HAI phía.**
 */
import { keccak256, toUtf8Bytes } from 'ethers';

import { canonicalize } from './jcs.mjs';
import { leafHash } from './leaf.mjs';

/** Đúng 14 trường. Danh sách này là đặc tả — dùng để TỪ CHỐI bản ghi sai tập trường. */
export const SCORE_FIELDS = Object.freeze([
  'c1', 'c2', 'c3', 'c4', 'c5',
  'classification',
  'evidenceHash',
  'nonce',
  'rulesetHash',
  'rulesetVersion',
  'scoredAt',
  'semester',
  'studentCode',
  'total',
]);

/** Đúng 5 trường. */
export const RULESET_FIELDS = Object.freeze([
  'effectiveFrom', 'nonce', 'rulesetHash', 'semester', 'version',
]);

/** Trần điểm từng tiêu chí theo Thông tư 16/2015/TT-BGDĐT. */
export const TRAN_TIEU_CHI = Object.freeze({ c1: 20, c2: 25, c3: 20, c4: 25, c5: 10 });

const XEP_LOAI = Object.freeze(['XUAT_SAC', 'TOT', 'KHA', 'TRUNG_BINH', 'YEU', 'KEM']);

// ------------------------------------------------------------------ bằng chứng

/**
 * `evidenceHash` từ danh sách leaf hash miền ATTEND.
 *
 * Tự sắp xếp: thứ tự duyệt bản ghi là chi tiết của truy vấn CSDL, không phải của bằng chứng.
 * Không sắp thì đổi một mệnh đề `ORDER BY` là đổi mọi `evidence_hash` dù dữ liệu y hệt.
 *
 * Danh sách rỗng **hợp lệ** và có nghĩa riêng: sinh viên không tham gia hoạt động nào, điểm
 * của họ hoàn toàn từ điểm nền mặc định của bộ quy tắc.
 */
export function evidenceHash(leavesHex) {
  const sorted = [...leavesHex].sort();

  for (const h of sorted) {
    if (typeof h !== 'string' || !/^0x[0-9a-f]{64}$/.test(h)) {
      throw new Error(`Leaf phải là 32 byte hex chữ thường có tiền tố 0x, nhận được: ${h}`);
    }
  }
  if (new Set(sorted).size !== sorted.length) {
    throw new Error('Danh sách bằng chứng có lá TRÙNG — một bản ghi bị đếm hai lần.');
  }

  return keccak256(toUtf8Bytes(canonicalize({ domain: 'ATTEND', leaves: sorted })));
}

/**
 * `rulesetHash` từ nội dung tệp bộ quy tắc.
 *
 * Băm **byte thô**, không qua JCS. Hệ quả là sinh viên tải tệp quy tắc về, băm nguyên văn, và
 * so với giá trị đã neo — **không cần biết JCS là gì**. Đổi lại: đổi một khoảng trắng trong
 * tệp là đổi hash, đúng như mong muốn với một văn bản quy chế đã công bố.
 */
export function rulesetHash(jsonBody) {
  if (typeof jsonBody !== 'string' || jsonBody.trim() === '') {
    throw new Error('Nội dung bộ quy tắc rỗng — không băm được.');
  }
  return keccak256(toUtf8Bytes(jsonBody));
}

// ------------------------------------------------------------------ payload

export function normalizeScorePayload(raw) {
  requireExact(raw, SCORE_FIELDS, 'SCORE');

  requireString(raw, 'studentCode');
  requireString(raw, 'semester');
  requireString(raw, 'rulesetVersion');
  requireHash32(raw, 'rulesetHash');
  requireIsoSeconds(raw, 'scoredAt');
  requireHash32(raw, 'evidenceHash');
  requireString(raw, 'nonce');

  let tong = 0;
  for (const [k, tran] of Object.entries(TRAN_TIEU_CHI)) {
    requireInteger(raw, k);
    if (raw[k] < 0 || raw[k] > tran) {
      throw new Error(`Trường \`${k}\` = ${raw[k]} nằm ngoài [0, ${tran}] của Thông tư 16/2015.`);
    }
    tong += raw[k];
  }

  requireInteger(raw, 'total');
  // Phép kiểm rẻ nhất mà bắt được nhiều nhất: tổng phải bằng tổng năm tiêu chí. Nếu ai đó
  // sửa `total` để nâng điểm mà quên sửa từng tiêu chí, chỗ này bắt ngay — trước cả khi cần
  // tới Merkle proof.
  if (raw.total !== tong) {
    throw new Error(`total = ${raw.total} nhưng tổng năm tiêu chí = ${tong}.`);
  }

  if (raw.classification !== null && !XEP_LOAI.includes(raw.classification)) {
    throw new Error(
      `classification không hợp lệ: ${JSON.stringify(raw.classification)}.`
        + ` Chỉ có: ${XEP_LOAI.join(', ')} hoặc null.`,
    );
  }
  return raw;
}

export function normalizeRulesetPayload(raw) {
  requireExact(raw, RULESET_FIELDS, 'RULESET');
  requireString(raw, 'version');
  requireString(raw, 'semester');
  requireHash32(raw, 'rulesetHash');
  requireIsoSeconds(raw, 'effectiveFrom');
  requireString(raw, 'nonce');
  return raw;
}

export function scoreLeafHash(raw) {
  return leafHash('SCORE', normalizeScorePayload(raw));
}

export function rulesetLeafHash(raw) {
  return leafHash('RULESET', normalizeRulesetPayload(raw));
}

/**
 * Kiểm một điểm số đã neo — **ba mắt xích, thiếu một là không kết luận được**.
 *
 * @param scorePayload   payload SCORE lấy từ bundle
 * @param leavesHex      leaf ATTEND của các bản ghi điểm danh sinh viên đưa ra
 * @param rulesetJson    nội dung tệp bộ quy tắc tải về
 */
export function kiemDiem(scorePayload, leavesHex, rulesetJson) {
  normalizeScorePayload(scorePayload);

  const evidence = evidenceHash(leavesHex);
  const rs = rulesetHash(rulesetJson);

  return {
    bangChungKhop: evidence === scorePayload.evidenceHash,
    boQuyTacKhop: rs === scorePayload.rulesetHash,
    evidenceTinhLai: evidence,
    rulesetTinhLai: rs,
    // Chỉ nói được "đúng dữ liệu, đúng quy tắc". Việc CHẠY LẠI phép tính để đối chiếu con số
    // cần một bộ đánh giá SpEL — nằm ngoài ràng buộc `chỉ ethers + merkletreejs` của verifier.
    // Ghi vào hạn chế thay vì giả vờ verifier làm được.
    ghiChu: 'Kiểm được ĐÚNG DỮ LIỆU và ĐÚNG QUY TẮC. Chạy lại phép tính cần bộ đánh giá SpEL.',
  };
}

// ------------------------------------------------------------------ kiểm tra

function requireExact(o, fields, what) {
  if (o === null || typeof o !== 'object' || Array.isArray(o)) {
    throw new Error(`Payload ${what} phải là một object.`);
  }
  const keys = Object.keys(o).sort();
  const expected = [...fields].sort();
  if (keys.length !== expected.length || keys.some((k, i) => k !== expected[i])) {
    const thua = keys.filter((k) => !expected.includes(k));
    const thieu = expected.filter((k) => !keys.includes(k));
    throw new Error(
      `Payload ${what} sai tập trường.`
        + (thieu.length ? ` Thiếu: ${thieu.join(', ')}.` : '')
        + (thua.length ? ` Thừa: ${thua.join(', ')}.` : ''),
    );
  }
}

function requireString(o, k) {
  if (typeof o[k] !== 'string') {
    throw new Error(`Trường \`${k}\` phải là chuỗi, nhận được ${typeof o[k]}.`);
  }
}

function requireInteger(o, k) {
  if (!Number.isInteger(o[k])) {
    throw new Error(`Trường \`${k}\` phải là số nguyên.`);
  }
}

function requireHash32(o, k) {
  if (typeof o[k] !== 'string' || !/^0x[0-9a-f]{64}$/.test(o[k])) {
    throw new Error(`Trường \`${k}\` phải là 32 byte hex chữ thường, nhận được ${o[k]}.`);
  }
}

function requireIsoSeconds(o, k) {
  const v = o[k];
  if (typeof v !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/.test(v)) {
    throw new Error(
      `Trường \`${k}\` phải là ISO-8601 UTC độ chính xác giây, nhận được: ${JSON.stringify(v)}`,
    );
  }
}
