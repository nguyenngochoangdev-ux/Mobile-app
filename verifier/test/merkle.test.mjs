/**
 * Test vector Merkle — NỬA JS.
 * Nửa Java: `backend/src/test/java/vn/ptit/drl/anchor/MerkleVectorTest.java`.
 *
 * Chạy: `npm test` (trong thư mục verifier/)
 *
 * Hai phía đọc CÙNG MỘT file `merkle-vectors.json`. Xanh một phía không có nghĩa gì —
 * `/canonical-hash` yêu cầu xanh cả hai. Test đỏ = một trong hai phía sai, KHÔNG phải file
 * vector sai; đừng chạy `gen-merkle-vectors` để "sửa" nó.
 */
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { getBytes, keccak256, concat } from 'ethers';

import { merkleRoot, merkleProof, verifyProof, buildTree } from '../src/merkle.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const DOC = JSON.parse(
  readFileSync(resolve(HERE, '../../backend/src/test/resources/merkle-vectors.json'), 'utf8'),
);

describe('Bộ vector Merkle — root', () => {
  for (const tree of DOC.trees) {
    it(`${tree.id} (n=${tree.leafCount}) cho đúng root đã chốt`, () => {
      assert.equal(merkleRoot(tree.leaves), tree.root, tree.why);
    });
  }
});

describe('Bộ vector Merkle — proof', () => {
  for (const tree of DOC.trees) {
    for (const p of tree.proofs) {
      it(`${tree.id} index ${p.index}: proof khớp vector`, () => {
        assert.deepEqual(merkleProof(tree.leaves, p.index), p.siblings);
      });

      it(`${tree.id} index ${p.index}: proof verify được về root`, () => {
        assert.equal(verifyProof(p.leaf, p.siblings, tree.root), true);
      });
    }
  }
});

describe('Bộ vector Merkle — proof phải THẤT BẠI khi bị sửa', () => {
  // Quan trọng ngang phần happy path: một hàm verify luôn trả true cũng làm mọi test trên
  // xanh. Phần này chứng minh nó thật sự kiểm tra.
  const tree = DOC.trees.find((t) => t.leafCount >= 4);
  const p = tree.proofs[0];

  it('sai lá → không verify được', () => {
    const otherLeaf = tree.leaves.find((l) => l !== p.leaf);
    assert.equal(verifyProof(otherLeaf, p.siblings, tree.root), false);
  });

  it('sai root → không verify được', () => {
    const wrongRoot = '0x' + 'ff'.repeat(32);
    assert.equal(verifyProof(p.leaf, p.siblings, wrongRoot), false);
  });

  it('đổi một byte trong sibling → không verify được', () => {
    const tampered = [...p.siblings];
    const b = getBytes(tampered[0]);
    b[0] ^= 0x01;
    tampered[0] = '0x' + Buffer.from(b).toString('hex');
    assert.equal(verifyProof(p.leaf, tampered, tree.root), false);
  });

  it('bỏ bớt một sibling → không verify được', () => {
    assert.equal(verifyProof(p.leaf, p.siblings.slice(1), tree.root), false);
  });

  it('proof của lá khác → không verify được', () => {
    const other = tree.proofs[1] ?? tree.proofs[0];
    if (other === p) return;
    assert.equal(verifyProof(p.leaf, other.siblings, tree.root), false);
  });
});

describe('Bộ vector Merkle — trường hợp bắt buộc bị từ chối', () => {
  for (const r of DOC.rejects) {
    it(`${r.id} bị từ chối`, () => {
      assert.throws(() => merkleRoot(r.leaves), new RegExp(r.expectedError), r.why);
    });
  }

  it('index ngoài phạm vi bị từ chối', () => {
    const leaves = DOC.trees[1].leaves;
    assert.throws(() => merkleProof(leaves, leaves.length), /ngoài phạm vi/);
    assert.throws(() => merkleProof(leaves, -1), /ngoài phạm vi/);
  });
});

describe('Ba quy ước của cây — chốt trực tiếp, không qua vector', () => {
  const A = keccak256(Buffer.from([1]));
  const B = keccak256(Buffer.from([2]));

  it('nút nội bộ là keccak256(min || max), so sánh KHÔNG DẤU', () => {
    const [a, b] = [Buffer.from(getBytes(A)), Buffer.from(getBytes(B))];
    const [min, max] = Buffer.compare(a, b) <= 0 ? [a, b] : [b, a];
    assert.equal(merkleRoot([A, B]), keccak256(concat([min, max])));
  });

  it('đảo thứ tự hai lá KHÔNG đổi root — hệ quả của việc sắp xếp cặp', () => {
    assert.equal(merkleRoot([A, B]), merkleRoot([B, A]));
  });

  it('cây một lá: root chính là lá, proof rỗng', () => {
    assert.equal(merkleRoot([A]), A);
    assert.deepEqual(merkleProof([A], 0), []);
  });

  it('nút lẻ được ĐẨY LÊN nguyên vẹn, không nhân đôi', () => {
    const C = keccak256(Buffer.from([3]));
    const layers = buildTree([A, B, C]).getLayers();

    assert.equal(layers[0].length, 3);
    assert.equal(layers[1].length, 2);
    // Nút thứ ba đi lên tầng trên KHÔNG bị băm lại. Nếu quy ước là nhân đôi (kiểu Bitcoin)
    // thì giá trị này sẽ là keccak256(C || C), khác hẳn.
    assert.equal('0x' + layers[1][1].toString('hex'), C);
  });

  it('lá bị đẩy lên có proof NGẮN HƠN các lá khác', () => {
    const C = keccak256(Buffer.from([3]));
    assert.equal(merkleProof([A, B, C], 0).length, 2);
    assert.equal(merkleProof([A, B, C], 2).length, 1);
  });

  it('thứ tự lá được giữ nguyên, không sắp xếp', () => {
    // Nếu sortLeaves bật, hai cây dưới đây sẽ cho cùng root ở mọi n. Với n=3, thứ tự lá
    // quyết định lá nào bị đẩy lên, nên root phải khác nhau.
    const C = keccak256(Buffer.from([3]));
    assert.notEqual(merkleRoot([A, B, C]), merkleRoot([C, B, A]));
  });
});
