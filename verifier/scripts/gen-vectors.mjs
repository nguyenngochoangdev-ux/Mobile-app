/**
 * Sinh `backend/src/test/resources/canonical-vectors.json`.
 *
 * Chạy: `npm run gen-vectors` (trong thư mục verifier/)
 *
 * File sinh ra là HỢP ĐỒNG giữa Java và JS. Chỉ chạy lại script này khi cố ý đổi
 * đặc tả — và khi đó phải chạy lại test CẢ HAI phía, xem `/canonical-hash`.
 * Đừng chạy lại để "sửa" một test đang đỏ: test đỏ nghĩa là một trong hai phía sai.
 */
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { hexlify } from 'ethers';
import { canonicalize } from '../src/jcs.mjs';
import { leafHash, domainBytes8, leafPreimage } from '../src/leaf.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT = resolve(HERE, '../../backend/src/test/resources/canonical-vectors.json');

/**
 * Khóa của các payload dưới đây được viết CỐ Ý KHÔNG THEO THỨ TỰ ALPHABET.
 * Nếu một phía quên sắp xếp, test sẽ đỏ ngay thay vì tình cờ đúng.
 */
const VECTORS = [
  {
    id: 'attend-basic',
    why: 'Bản ghi điểm danh điển hình: số nguyên + số thực + timestamp + boolean trong cùng payload.',
    domain: 'ATTEND',
    payload: {
      studentCode: 'B21DCCN123',
      eventId: 128,
      checkInAt: '2026-08-05T09:30:00Z',
      lat: 21.0285,
      lng: 105.8542,
      verified: true,
      geofenceOk: false,
      deviceFp: '6f1c2a90-3b4d-4e5f-8a91-2c3d4e5f6a7b',
      nonce: '0x9f86d081884c7d659a2feaa0c55ad015',
    },
  },
  {
    id: 'attend-tieng-viet',
    why: 'Tiếng Việt có dấu giữ nguyên UTF-8 (không escape \\uXXXX); dấu nháy kép, xuống dòng, tab phải escape; ký tự ngoài BMP (emoji) đi bằng cặp thay thế.',
    domain: 'ATTEND',
    payload: {
      fullName: 'Nguyễn Ngọc Hoàng',
      note: 'Điểm danh "muộn"\n\tlý do: kẹt xe — đã xác nhận 🎓',
      unit: 'Đoàn Thanh niên · Khoa CNTT1',
      studentCode: 'B21DCCN123',
      nonce: '0x3b8c1f42a7d90e5643f1c2b8a90d7e64',
    },
  },
  {
    id: 'score-nested',
    why: 'Object lồng nhau — chứng minh sắp xếp khóa là ĐỆ QUY, không chỉ ở cấp ngoài cùng. Thang 20/25/20/25/10 của Thông tư 16/2015/TT-BGDĐT.',
    domain: 'SCORE',
    payload: {
      total: 87.5,
      semester: '2026-1',
      studentCode: 'B21DCCN123',
      rulesetVersion: 3,
      criteria: {
        ynThucHocTap: 18.5,
        chapHanhNoiQuy: 24,
        hoatDongXaHoi: 17,
        quanHeCongDong: 20,
        congTacCanBo: 8,
      },
      nonce: '0xa1b2c3d4e5f60718293a4b5c6d7e8f90',
    },
  },
  {
    id: 'cred-null-va-mang',
    why: 'Trường null giữ nguyên literal `null` (KHÔNG bị lược bỏ); mảng giữ nguyên thứ tự phần tử (không sắp xếp); mảng object cũng sắp xếp khóa đệ quy.',
    domain: 'CRED',
    payload: {
      subject: 'B21DCCN123',
      expiresAt: null,
      issuedAt: '2026-08-05T00:00:00Z',
      revokedAt: null,
      scopes: ['SCORE', 'ATTEND', 'CRED'],
      evidence: [
        { weight: 2, kind: 'attendance' },
        { weight: 1, kind: 'award' },
      ],
      statusListIndex: 40317,
      nonce: '0x0011223344556677889900aabbccddee',
    },
  },
  {
    id: 'audit-bien',
    why: 'Các giá trị biên: 0, số âm, object rỗng, mảng rỗng, chuỗi rỗng, số nguyên lớn sát MAX_SAFE_INTEGER, và số thực ở hai đầu vùng an toàn [1e-3, 1e7).',
    domain: 'AUDIT',
    payload: {
      seq: 0,
      delta: -3,
      meta: {},
      changes: [],
      reason: '',
      prevHash: '0x0000000000000000000000000000000000000000000000000000000000000000',
      bigCounter: 9007199254740991,
      nearZero: 0.001,
      nearMax: 9999999.5,
      nonce: '0xdeadbeefcafebabe0123456789abcdef',
    },
  },
  {
    id: 'attend-payload-day-du',
    why: 'LƯỢC ĐỒ ATTEND THẬT mà job neo dựng ra — đúng 11 trường của AttendancePayload.of(). Bản ghi QR_SCAN đầy đủ: có toạ độ, có deviceFp, có check-out. Đây là hợp đồng backend↔verifier, không phải một payload minh hoạ.',
    domain: 'ATTEND',
    payload: {
      studentCode: 'B21DCCN042',
      eventId: 7,
      method: 'QR_SCAN',
      checkInAt: '2026-08-04T17:55:58Z',
      checkOutAt: '2026-08-04T19:30:00Z',
      deviceFp: '6f1c2a90-3b4d-4e5f-8a91-2c3d4e5f6a7b',
      lat: 21.0285,
      lng: 105.8542,
      verified: true,
      geofenceOk: true,
      nonce: '0x74a40e0f493b112d51f09ade5cab9d44',
    },
  },
  {
    id: 'attend-payload-toan-null',
    why: 'Cùng lược đồ nhưng là bản ghi MANUAL do cán bộ nhập tay: không toạ độ, không thiết bị, chưa check-out, geofenceOk không xác định. Bốn trường null phải GIỮ NGUYÊN literal `null` — trường vắng mặt cho ra hash khác. Đây là dạng bản ghi mà bảng threat model gọi là "vấn đề oracle".',
    domain: 'ATTEND',
    payload: {
      studentCode: 'B21DCCN042',
      eventId: 3,
      method: 'MANUAL',
      checkInAt: '2026-08-04T17:57:04Z',
      checkOutAt: null,
      deviceFp: null,
      lat: null,
      lng: null,
      verified: false,
      geofenceOk: null,
      nonce: '0x0403c6481c937c9f1ffacbf727fbe58b',
    },
  },
  {
    id: 'cred-payload-day-du',
    why: 'LƯỢC ĐỒ CRED THẬT mà luồng cấp credential dựng ra — đúng 11 trường cấp ngoài của CredentialPayload.of(), trong đó `claims` là object LỒNG nên chốt luôn việc sắp xếp khóa là đệ quy. Bản có hạn dùng, tên sinh viên có dấu tiếng Việt. Đây là hợp đồng backend↔verifier, không phải payload minh hoạ.',
    domain: 'CRED',
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
      claims: {
        totalPoints: 85,
        semester: '2026-1',
        activityCount: 12,
      },
      nonce: '0x5c1d8e4a2f6b90c37e15a8d4b2f60931',
    },
  },
  {
    id: 'cred-payload-khong-han-va-rong',
    why: 'Cùng lược đồ nhưng KHÔNG có hạn dùng (`expiresAt` null phải giữ nguyên literal `null` — trường vắng mặt cho ra hash khác), và là sinh viên chưa tham gia hoạt động nào: activityCount = 0, totalPoints = 0. Số 0 trong object lồng là chỗ dễ bị lược bỏ nhất nếu ai đó lỡ dùng serializer bỏ giá trị rỗng.',
    domain: 'CRED',
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
      claims: {
        totalPoints: 0,
        semester: '2026-1',
        activityCount: 0,
      },
      nonce: '0xb7e34c1908af52d6e0c94b73a815f2d0',
    },
  },
  {
    id: 'audit-payload-day-du',
    why: 'LƯỢC ĐỒ AUDIT THẬT — đúng 10 trường của AuditPayload.of(). Bản ghi SỬA một credential: có cả beforeHash lẫn afterHash, có prevHash (không phải bản ghi đầu chuỗi). Đây là hợp đồng backend↔verifier.',
    domain: 'AUDIT',
    payload: {
      seq: 42,
      action: 'CREDENTIAL_REVOKE',
      entity: 'credentials',
      entityId: 81,
      actorId: 7,
      at: '2026-08-06T04:12:33Z',
      beforeHash: '0x1f2e3d4c5b6a798807162534435261708f9eadbc0d1e2f30415263748596a7b8',
      afterHash: '0x9a8b7c6d5e4f30211203344556677889aabbccddeeff00112233445566778899',
      prevHash: '0x0d1c2b3a49586776859493a2b1c0dfee0f1e2d3c4b5a69788796a5b4c3d2e1f0',
      hash: '0xfedcba98765432100123456789abcdeffedcba98765432100123456789abcdef',
      nonce: '0x1a2b3c4d5e6f708192a3b4c5d6e7f809',
    },
  },
  {
    id: 'audit-payload-dau-chuoi',
    why: 'Bản ghi ĐẦU TIÊN của cả chuỗi: prevHash null (giữ nguyên literal null, KHÔNG thay bằng 64 số 0 như lúc băm mắt xích — hai chỗ hai quy ước, có chủ ý). Là hành động TẠO MỚI nên beforeHash cũng null. actorId null vì hệ thống tự làm.',
    domain: 'AUDIT',
    payload: {
      seq: 1,
      action: 'CREDENTIAL_ISSUE',
      entity: 'credentials',
      entityId: 81,
      actorId: null,
      at: '2026-08-06T03:28:20Z',
      beforeHash: null,
      afterHash: '0x2b3c4d5e6f708192a3b4c5d6e7f8091a0b1c2d3e4f506172839405a6b7c8d9e0',
      prevHash: null,
      hash: '0x0123456789abcdeffedcba98765432100123456789abcdeffedcba9876543210',
      nonce: '0x9f86d081884c7d659a2feaa0c55ad015',
    },
  },
  {
    id: 'ruleset-sap-xep-khoa',
    why: 'Tra tấn thứ tự khóa theo ĐƠN VỊ MÃ UTF-16: chữ số < chữ hoa < chữ thường < ký tự có dấu. Java String.compareTo và JS Array.sort() đều dùng đúng thứ tự này.',
    domain: 'RULESET',
    payload: {
      b: 1,
      á: 2,
      A: 3,
      ab: 4,
      a: 5,
      Z: 6,
      1: 7,
      nonce: '0xfedcba98765432100123456789abcdef',
    },
  },
];

/**
 * Payload BẮT BUỘC phải bị từ chối. Test cả hai phía phải ném lỗi ở đây.
 * Chặn được im lặng là mục đích của cả bộ này — thà vỡ ồn ào còn hơn lệch hash.
 */
const REJECTS = [
  {
    id: 'thieu-nonce',
    why: 'Không có nonce → payload vét cạn được từ leaf hash (PROJECT.md §2.3).',
    domain: 'ATTEND',
    payload: { studentCode: 'B21DCCN123', eventId: 1 },
    expectedError: 'nonce',
  },
  {
    id: 'nonce-sai-do-dai',
    why: 'Nonce 8 byte thay vì 16 byte.',
    domain: 'ATTEND',
    payload: { studentCode: 'B21DCCN123', nonce: '0x0011223344556677' },
    expectedError: 'nonce',
  },
  {
    id: 'nonce-chu-hoa',
    why: 'Hex chữ hoa — hai phía dễ chuẩn hóa khác nhau nên chốt chữ thường và từ chối phần còn lại.',
    domain: 'ATTEND',
    payload: { studentCode: 'B21DCCN123', nonce: '0xDEADBEEFCAFEBABE0123456789ABCDEF' },
    expectedError: 'nonce',
  },
  {
    id: 'so-thuc-qua-nho',
    why: 'JS in "0.0001", Java in "1.0E-4" → lệch hash im lặng. Phải ném lỗi.',
    domain: 'SCORE',
    payload: { v: 0.0001, nonce: '0x9f86d081884c7d659a2feaa0c55ad015' },
    expectedError: 'an toàn',
  },
  {
    id: 'so-thuc-qua-lon',
    why: 'Vượt 1e7: JS in "12345678.5", Java in "1.23456785E7" → lệch hash im lặng.',
    domain: 'SCORE',
    payload: { v: 12345678.5, nonce: '0x9f86d081884c7d659a2feaa0c55ad015' },
    expectedError: 'an toàn',
  },
  {
    id: 'so-nguyen-vuot-safe-integer',
    why: 'Vượt 2^53-1: Java `long` biểu diễn chính xác, JS `number` thì không. Bất kỳ giá trị nào trong vùng này đều có nguy cơ hai phía đọc ra hai số khác nhau.',
    domain: 'AUDIT',
    payload: { v: 9007199254740994, nonce: '0x9f86d081884c7d659a2feaa0c55ad015' },
    expectedError: 'MAX_SAFE_INTEGER',
  },
  {
    id: 'mien-khong-hop-le',
    why: 'Chỉ có đúng 5 miền neo. Thêm miền thứ sáu là đổi lược đồ AnchorRegistry.',
    domain: 'DIEMDANH',
    payload: { nonce: '0x9f86d081884c7d659a2feaa0c55ad015' },
    expectedError: 'Miền neo',
  },
];

const vectors = VECTORS.map((v) => ({
  id: v.id,
  why: v.why,
  domain: v.domain,
  payload: v.payload,
  expected: {
    domainBytes8: hexlify(domainBytes8(v.domain)),
    jcs: canonicalize(v.payload),
    preimageBytes: hexlify(leafPreimage(v.domain, v.payload)).length / 2 - 1,
    leaf: leafHash(v.domain, v.payload),
  },
}));

const rejects = REJECTS;

const doc = {
  $schema: 'khong-phai-JSON-Schema — day la bo test vector, doc docs/canonicalization.md truoc khi sua',
  spec: 'docs/canonicalization.md',
  formula: 'leaf = keccak256( bytes8(domain) || 0x3A || UTF-8(JCS(payload)) )',
  warning:
    'File nay la HOP DONG giua backend Java va verifier JS. Test do = mot trong hai phia sai, ' +
    'KHONG phai file nay sai. Chi sinh lai bang `npm run gen-vectors` khi co y doi dac ta.',
  generatedBy: 'verifier/scripts/gen-vectors.mjs',
  vectors,
  rejects,
};

mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, JSON.stringify(doc, null, 2) + '\n', 'utf8');

console.log(`Da ghi ${vectors.length} vector + ${rejects.length} truong hop tu choi -> ${OUT}\n`);
for (const v of vectors) {
  console.log(`  ${v.id.padEnd(26)} ${v.domain.padEnd(8)} ${v.expected.leaf}`);
}
