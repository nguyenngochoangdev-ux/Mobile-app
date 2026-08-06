/**
 * Điểm rèn luyện và bộ quy tắc — NỬA JS.
 * Nửa Java: `ScoringVectorTest.java`.
 */
import test, { describe } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { keccak256, toUtf8Bytes } from 'ethers';

import {
  scoreLeafHash, rulesetLeafHash, normalizeScorePayload, normalizeRulesetPayload,
  evidenceHash, rulesetHash, kiemDiem, SCORE_FIELDS, RULESET_FIELDS, TRAN_TIEU_CHI,
} from '../src/score.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const RES = resolve(HERE, '../../backend/src/test/resources');
const CANONICAL = JSON.parse(readFileSync(resolve(RES, 'canonical-vectors.json'), 'utf8'));

const scoreVectors = CANONICAL.vectors.filter((v) => v.id.startsWith('score-payload'));
const rulesetVectors = CANONICAL.vectors.filter((v) => v.id.startsWith('ruleset-payload'));
const clone = (o) => JSON.parse(JSON.stringify(o));

const LA = (n) => '0x' + String(n).padStart(2, '0').repeat(32).slice(0, 64);

// ------------------------------------------------------------------ payload

describe('Payload SCORE — hợp đồng backend↔verifier', () => {
  test('có hai vector lược đồ thật', () => {
    assert.equal(scoreVectors.length, 2);
  });

  for (const v of scoreVectors) {
    test(`${v.id} — leaf khớp vector`, () => {
      assert.equal(scoreLeafHash(v.payload), v.expected.leaf, v.why);
    });
  }

  test('đúng 14 trường, khớp ScorePayload.of() phía Java', () => {
    assert.equal(SCORE_FIELDS.length, 14);
    for (const v of scoreVectors) {
      assert.deepEqual(Object.keys(v.payload).sort(), [...SCORE_FIELDS].sort());
    }
  });

  test('trần từng tiêu chí đúng Thông tư 16/2015: 20/25/20/25/10', () => {
    assert.deepEqual(TRAN_TIEU_CHI, { c1: 20, c2: 25, c3: 20, c4: 25, c5: 10 });
    assert.equal(Object.values(TRAN_TIEU_CHI).reduce((a, b) => a + b), 100);
  });

  test('sàn của bộ quy tắc: không hoạt động nào vẫn 40 điểm nhờ điểm mặc định', () => {
    const rong = scoreVectors.find((v) => v.id.includes('khong-hoat-dong'));
    assert.equal(rong.payload.total, 40);
    assert.equal(rong.payload.c2, 25, 'C2 là điểm mặc định, không chấm từ dữ liệu');
    assert.equal(rong.payload.c4, 15, 'C4 điểm nền, phần không đo được');
    assert.equal(rong.payload.classification, 'YEU');
  });
});

describe('Payload SCORE — phải bị TỪ CHỐI', () => {
  const base = () => clone(scoreVectors[0].payload);

  test('total không bằng tổng năm tiêu chí', () => {
    // Phép kiểm rẻ nhất mà bắt được nhiều nhất: ai đó sửa `total` để nâng điểm mà quên sửa
    // từng tiêu chí thì hỏng ngay, trước cả khi cần tới Merkle proof.
    const p = base();
    p.total = 95;
    assert.throws(() => normalizeScorePayload(p), /tổng năm tiêu chí/);
  });

  test('tiêu chí vượt trần Thông tư 16', () => {
    const p = base();
    p.c5 = 11;
    p.total = p.c1 + p.c2 + p.c3 + p.c4 + p.c5;
    assert.throws(() => normalizeScorePayload(p), /ngoài \[0, 10\]/);
  });

  test('tiêu chí âm', () => {
    const p = base();
    p.c1 = -1;
    p.total = p.c1 + p.c2 + p.c3 + p.c4 + p.c5;
    assert.throws(() => normalizeScorePayload(p), /ngoài \[0, 20\]/);
  });

  test('xếp loại không có trong Thông tư 16', () => {
    const p = base();
    p.classification = 'GIOI';
    assert.throws(() => normalizeScorePayload(p), /classification không hợp lệ/);
  });

  test('thiếu evidenceHash — điểm không có bằng chứng đầu vào thì neo nó vô nghĩa', () => {
    const p = base();
    delete p.evidenceHash;
    assert.throws(() => normalizeScorePayload(p), /Thiếu: evidenceHash/);
  });

  test('thiếu rulesetHash — không biết chấm bằng quy tắc nào', () => {
    const p = base();
    delete p.rulesetHash;
    assert.throws(() => normalizeScorePayload(p), /Thiếu: rulesetHash/);
  });

  test('scoredAt mang mili giây', () => {
    const p = base();
    p.scoredAt = '2026-08-06T08:00:00.500Z';
    assert.throws(() => normalizeScorePayload(p), /độ chính xác giây/);
  });
});

describe('Payload RULESET', () => {
  test('có vector lược đồ thật', () => {
    assert.ok(rulesetVectors.length >= 1);
  });

  for (const v of rulesetVectors) {
    test(`${v.id} — leaf khớp vector`, () => {
      assert.equal(rulesetLeafHash(v.payload), v.expected.leaf, v.why);
    });
  }

  test('đúng 5 trường', () => {
    assert.equal(RULESET_FIELDS.length, 5);
    assert.deepEqual(Object.keys(rulesetVectors[0].payload).sort(), [...RULESET_FIELDS].sort());
  });

  test('thiếu rulesetHash bị từ chối', () => {
    const p = clone(rulesetVectors[0].payload);
    delete p.rulesetHash;
    assert.throws(() => normalizeRulesetPayload(p), /Thiếu: rulesetHash/);
  });
});

// ------------------------------------------------------------------ bằng chứng

describe('evidenceHash — đóng góp học thuật của đề tài', () => {
  test('thứ tự đầu vào KHÔNG ảnh hưởng kết quả', () => {
    // Thứ tự duyệt bản ghi là chi tiết của truy vấn CSDL. Không sắp xếp thì đổi một mệnh đề
    // ORDER BY là đổi mọi evidence_hash dù dữ liệu y hệt.
    const a = evidenceHash([LA(11), LA(22), LA(33)]);
    const b = evidenceHash([LA(33), LA(11), LA(22)]);
    assert.equal(a, b);
  });

  test('đổi MỘT bản ghi là đổi hash', () => {
    assert.notEqual(evidenceHash([LA(11), LA(22)]), evidenceHash([LA(11), LA(23)]));
  });

  test('thêm một bản ghi là đổi hash', () => {
    assert.notEqual(evidenceHash([LA(11)]), evidenceHash([LA(11), LA(22)]));
  });

  test('danh sách RỖNG hợp lệ — sinh viên không tham gia hoạt động nào', () => {
    const h = evidenceHash([]);
    assert.match(h, /^0x[0-9a-f]{64}$/);
    // Và nó khác hash của một danh sách có phần tử — "rỗng" là một phát biểu, không phải
    // thiếu dữ liệu.
    assert.notEqual(h, evidenceHash([LA(11)]));
  });

  test('lá TRÙNG bị từ chối — một bản ghi bị đếm hai lần thì điểm sai', () => {
    assert.throws(() => evidenceHash([LA(11), LA(11)]), /TRÙNG/);
  });

  test('lá sai độ dài bị từ chối', () => {
    assert.throws(() => evidenceHash(['0xabcd']), /0x/);
  });

  test('lá hex CHỮ HOA bị từ chối — cùng họ lỗi với nonce chữ hoa', () => {
    // Phải dùng hex CÓ CHỮ CÁI. Bản đầu của test này viết LA(11).toUpperCase() và không bao
    // giờ đỏ được: "1111…" toàn chữ số, mà chữ số không có dạng hoa — chuỗi không đổi.
    const coChuCai = '0x' + 'ab'.repeat(32);
    assert.equal(coChuCai.length, 66);
    assert.throws(() => evidenceHash([coChuCai.toUpperCase().replace('0X', '0x')]),
      /chữ thường/);
    assert.doesNotThrow(() => evidenceHash([coChuCai]));
  });

  test('công thức khớp đặc tả — JCS của {domain, leaves đã sắp}', () => {
    const mong = keccak256(toUtf8Bytes(
      `{"domain":"ATTEND","leaves":["${LA(11)}","${LA(22)}"]}`,
    ));
    assert.equal(evidenceHash([LA(22), LA(11)]), mong);
  });
});

describe('rulesetHash — băm BYTE THÔ, không qua JCS', () => {
  test('khớp keccak của chính byte UTF-8', () => {
    const json = '{ "version": "x",  "a": 1 }';
    assert.equal(rulesetHash(json), keccak256(toUtf8Bytes(json)));
  });

  test('đổi MỘT khoảng trắng là đổi hash', () => {
    // Đúng như mong muốn với một văn bản quy chế: bản đã công bố là bản có hiệu lực, không
    // phải "một bản tương đương về ngữ nghĩa".
    assert.notEqual(rulesetHash('{"a":1}'), rulesetHash('{ "a": 1 }'));
  });

  test('tiếng Việt có dấu đi bằng byte UTF-8 thô', () => {
    const json = '{"ten":"Ý thức học tập"}';
    assert.equal(rulesetHash(json), keccak256(toUtf8Bytes(json)));
  });

  test('nội dung rỗng bị từ chối', () => {
    assert.throws(() => rulesetHash(''), /rỗng/);
    assert.throws(() => rulesetHash('   '), /rỗng/);
  });
});

describe('kiemDiem — ba mắt xích', () => {
  const leaves = [LA(11), LA(22), LA(33)];
  const rulesetJson = '{"version":"2026-1.v1"}';

  const payload = () => {
    const p = clone(scoreVectors[0].payload);
    p.evidenceHash = evidenceHash(leaves);
    p.rulesetHash = rulesetHash(rulesetJson);
    return p;
  };

  test('bằng chứng và bộ quy tắc đều khớp', () => {
    const kq = kiemDiem(payload(), leaves, rulesetJson);
    assert.equal(kq.bangChungKhop, true);
    assert.equal(kq.boQuyTacKhop, true);
  });

  test('đưa thiếu một bản ghi điểm danh → bằng chứng KHÔNG khớp', () => {
    const kq = kiemDiem(payload(), leaves.slice(0, 2), rulesetJson);
    assert.equal(kq.bangChungKhop, false);
  });

  test('đưa thừa một bản ghi → bằng chứng KHÔNG khớp', () => {
    const kq = kiemDiem(payload(), [...leaves, LA(44)], rulesetJson);
    assert.equal(kq.bangChungKhop, false);
  });

  test('bộ quy tắc bị sửa sau khi công bố điểm → KHÔNG khớp', () => {
    // Đây chính là kịch bản miền RULESET sinh ra để chặn: con số đã neo vẫn nguyên, nhưng
    // câu chuyện giải thích nó thì đổi.
    const kq = kiemDiem(payload(), leaves, '{"version":"2026-1.v1","suaTrom":true}');
    assert.equal(kq.boQuyTacKhop, false);
    assert.equal(kq.bangChungKhop, true, 'phần bằng chứng vẫn đúng — hai thứ độc lập');
  });

  test('nói rõ nó KHÔNG chạy lại phép tính', () => {
    // Trung thực về giới hạn: verifier chỉ có ethers + merkletreejs, không có bộ đánh giá
    // SpEL. Nói "verify điểm" mà thực ra chỉ verify đầu vào là thổi phồng.
    assert.match(kiemDiem(payload(), leaves, rulesetJson).ghiChu, /SpEL/);
  });
});
