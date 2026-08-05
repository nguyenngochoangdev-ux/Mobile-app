/**
 * Sinh `backend/src/test/resources/merkle-vectors.json`.
 *
 * Chạy: `npm run gen-merkle-vectors` (trong thư mục verifier/)
 *
 * File sinh ra là HỢP ĐỒNG thứ hai giữa Java và JS — bộ thứ nhất
 * (`canonical-vectors.json`) chốt *leaf*, bộ này chốt *cây*. Chỉ chạy lại khi cố ý đổi đặc
 * tả, và khi đó phải chạy lại test CẢ HAI phía (`/canonical-hash`).
 * Đừng chạy lại để "sửa" một test đang đỏ: test đỏ nghĩa là một trong hai phía sai.
 */
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { keccak256, toUtf8Bytes, getBytes } from 'ethers';
import { merkleRoot, merkleProof, verifyProof } from '../src/merkle.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const RESOURCES = resolve(HERE, '../../backend/src/test/resources');
const OUT = resolve(RESOURCES, 'merkle-vectors.json');
const CANONICAL = resolve(RESOURCES, 'canonical-vectors.json');

/** Lá tổng hợp, tất định — cùng hạt giống luôn cho cùng giá trị. */
const synth = (i) => keccak256(toUtf8Bytes(`drl-merkle-vector-${i}`));
const synthLeaves = (n) => Array.from({ length: n }, (_, i) => synth(i));

/**
 * Tìm một cặp lá mà byte đầu của một cái ≥ 0x80 còn cái kia < 0x80.
 *
 * Đây là cặp bắt được bẫy Java kinh điển: kiểu `byte` của Java CÓ DẤU, nên
 * `Arrays.compare` coi 0xFF là −1 và xếp nó TRƯỚC 0x00, trong khi JS
 * (`Buffer.compare`) so sánh không dấu. Với cặp này, dùng nhầm `Arrays.compare` thay vì
 * `Arrays.compareUnsigned` sẽ đảo thứ tự nối → root khác hoàn toàn, và không có gì báo lỗi.
 */
function findSignedCompareTrap() {
  let high, low;
  for (let i = 0; i < 5000 && !(high && low); i++) {
    const h = synth(i);
    const first = getBytes(h)[0];
    if (first >= 0x80 && !high) high = h;
    if (first < 0x80 && !low) low = h;
  }
  if (!high || !low) throw new Error('Không tìm được cặp bẫy so sánh có dấu');
  // Đặt cái byte-cao TRƯỚC: so sánh không dấu giữ nguyên thứ tự, so sánh có dấu đảo lại.
  return [high, low];
}

/** Sáu leaf hash thật từ bộ vector thứ nhất — nối hai bộ vector lại với nhau. */
function canonicalLeaves() {
  const doc = JSON.parse(readFileSync(CANONICAL, 'utf8'));
  return doc.vectors.map((v) => v.expected.leaf);
}

const TREES = [
  {
    id: 'n1-mot-la',
    why: 'Cây một lá: root CHÍNH LÀ lá đó, không băm thêm lần nào, và proof rỗng. Nếu một phía băm thêm một vòng thì lệch ngay từ lô nhỏ nhất.',
    leaves: synthLeaves(1),
    proofIndices: [0],
  },
  {
    id: 'n2-can-bang',
    why: 'Cây nhỏ nhất có một phép ghép: chốt công thức nút nội bộ keccak256(min||max).',
    leaves: synthLeaves(2),
    proofIndices: [0, 1],
  },
  {
    id: 'n3-nut-le-day-len',
    why: 'BA LÁ — trường hợp quan trọng nhất của cả bộ. Lá thứ 3 lẻ ra, phải được ĐẨY LÊN nguyên vẹn (không nhân đôi, không băm lại). Proof của nó NGẮN HƠN proof của hai lá kia. Bitcoin nhân đôi nút cuối; chọn nhầm quy ước là lệch root ở mọi lô có số lá lẻ.',
    leaves: synthLeaves(3),
    proofIndices: [0, 1, 2],
  },
  {
    id: 'n4-can-bang-day-du',
    why: 'Cây nhị phân đầy đủ hai tầng — không có nút lẻ ở bất kỳ tầng nào.',
    leaves: synthLeaves(4),
    proofIndices: [0, 3],
  },
  {
    id: 'n5-nut-le-nhieu-tang',
    why: 'Năm lá: tầng 0 có 5 nút (lẻ), tầng 1 có 3 nút (lẻ nữa). Chốt việc đẩy nút lẻ xảy ra ĐỘC LẬP ở từng tầng, không chỉ tầng lá.',
    leaves: synthLeaves(5),
    proofIndices: [0, 4],
  },
  {
    id: 'n7-le-o-moi-tang',
    why: 'Bảy lá: 7 -> 4 -> 2 -> 1. Lá cuối bị đẩy lên qua nhiều tầng liên tiếp.',
    leaves: synthLeaves(7),
    proofIndices: [0, 5, 6],
  },
  {
    id: 'n8-can-bang-day-du',
    why: 'Tám lá, cây đầy đủ ba tầng: mọi proof dài đúng 3.',
    leaves: synthLeaves(8),
    proofIndices: [0, 7],
  },
  {
    id: 'bay-so-sanh-co-dau',
    why: 'Hai lá được CHỌN CỐ Ý: byte đầu của lá thứ nhất >= 0x80, của lá thứ hai < 0x80. Java `Arrays.compare` (có dấu) đảo thứ tự cặp này, `Arrays.compareUnsigned` thì không. Dùng nhầm hàm là lệch root ở khoảng một nửa số cặp mà không có gì báo lỗi.',
    leaves: findSignedCompareTrap(),
    proofIndices: [0, 1],
  },
  {
    id: 'canonical-6-la-that',
    why: 'Dựng từ ĐÚNG 6 leaf hash của canonical-vectors.json. Nối hai bộ vector: nếu tầng leaf hash lệch thì cây cũng lệch, nên bộ này bảo vệ luôn cả bộ kia.',
    leaves: canonicalLeaves(),
    proofIndices: [0, 2, 5],
  },
  {
    id: 'n100-quy-mo-that',
    why: 'Lô 100 bản ghi — cỡ một buổi điểm danh thật. Proof dài 7 tầng. Cũng là lô lớn nhất còn đọc được bằng mắt trong file vector.',
    leaves: synthLeaves(100),
    proofIndices: [0, 1, 49, 98, 99],
  },
];

/** Trường hợp BẮT BUỘC bị từ chối. Cả hai phía phải ném lỗi. */
const REJECTS = [
  {
    id: 'lo-rong',
    why: 'Không dựng được cây từ 0 lá. AnchorRegistry cũng chặn leafCount = 0.',
    leaves: [],
    expectedError: 'rỗng',
  },
  {
    id: 'la-trung',
    why: 'Hai lá giống hệt làm bằng chứng nhập nhằng — một proof hợp lệ cho hai vị trí. Mỗi payload có nonce 16 byte riêng nên trùng lá nghĩa là lô chứa bản ghi lặp.',
    leaves: [synth(0), synth(1), synth(0)],
    expectedError: 'trùng',
  },
  {
    id: 'la-sai-do-dai',
    why: 'Lá phải đúng 32 byte. Nhận lá ngắn hơn là nhận cả một lớp lỗi im lặng ở tầng gọi.',
    leaves: [synth(0), '0x0011223344556677'],
    expectedError: '32 byte',
  },
];

// ------------------------------------------------------------------ sinh file

const trees = TREES.map((t) => {
  const root = merkleRoot(t.leaves);
  const proofs = t.proofIndices.map((index) => {
    const siblings = merkleProof(t.leaves, index);
    // Tự kiểm tra ngay lúc sinh: vector sai còn tệ hơn không có vector.
    if (!verifyProof(t.leaves[index], siblings, root)) {
      throw new Error(`Vector tự mâu thuẫn: ${t.id} index ${index} không verify được`);
    }
    return { index, leaf: t.leaves[index], siblings };
  });
  return { id: t.id, why: t.why, leafCount: t.leaves.length, leaves: t.leaves, root, proofs };
});

const doc = {
  $schema:
    'khong-phai-JSON-Schema — day la bo test vector, doc docs/canonicalization.md §8 truoc khi sua',
  spec: 'docs/canonicalization.md §8',
  conventions: {
    internalNode: 'keccak256( min(a,b) || max(a,b) ), so sanh byte KHONG DAU',
    sortPairs: true,
    duplicateOdd: false,
    sortLeaves: false,
    oddNode: 'day len nguyen ven, khong nhan doi va khong bam lai',
    singleLeaf: 'root = chinh la do',
  },
  warning:
    'File nay la HOP DONG giua backend Java va verifier JS. Test do = mot trong hai phia sai, ' +
    'KHONG phai file nay sai. Chi sinh lai bang `npm run gen-merkle-vectors` khi co y doi dac ta.',
  generatedBy: 'verifier/scripts/gen-merkle-vectors.mjs',
  trees,
  rejects: REJECTS,
};

mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, JSON.stringify(doc, null, 2) + '\n', 'utf8');

console.log(`Da ghi ${trees.length} cay + ${REJECTS.length} truong hop tu choi -> ${OUT}\n`);
for (const t of trees) {
  console.log(`  ${t.id.padEnd(26)} n=${String(t.leafCount).padStart(3)}  ${t.root}`);
}
