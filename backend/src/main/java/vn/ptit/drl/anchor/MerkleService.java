package vn.ptit.drl.anchor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.web3j.crypto.Hash;

/**
 * Cây Merkle — nửa Java. Nửa JS: {@code verifier/src/merkle.mjs} (bọc quanh
 * {@code merkletreejs}). Đặc tả chốt: {@code docs/canonicalization.md} §8.
 *
 * <p>Đây là chỗ lệch Java↔JS <b>thứ hai</b>, và nó fail im lặng y hệt chỗ thứ nhất: root
 * vẫn tính ra bình thường, chỉ là không khớp. Bộ test vector
 * {@code backend/src/test/resources/merkle-vectors.json} tồn tại để chặn đúng chuyện đó.
 *
 * <h2>Ba quy ước, đã đối chiếu bằng thực nghiệm với {@code merkletreejs}</h2>
 *
 * <ol>
 *   <li><b>Cặp anh em được sắp xếp trước khi nối</b> — nút nội bộ là
 *       {@code keccak256( min(a,b) || max(a,b) )}, so sánh byte <b>không dấu</b>, big-endian.
 *       Nhờ vậy proof không cần mang bit trái/phải, và verifier đơn giản hơn hẳn.
 *       Tương ứng {@code sortPairs: true} của merkletreejs và cũng là quy ước của
 *       OpenZeppelin {@code MerkleProof}.
 *   <li><b>Nút lẻ được ĐẨY LÊN nguyên vẹn</b>, không nhân đôi. Bitcoin nhân đôi nút cuối;
 *       merkletreejs mặc định thì không ({@code duplicateOdd: false}). Chọn sai một trong hai
 *       là lệch root ở mọi lô có số lá lẻ — tức khoảng một nửa số lô.
 *   <li><b>Thứ tự lá được giữ nguyên</b>, không sắp xếp ({@code sortLeaves: false}). Thứ tự
 *       do backend cấp và là thứ tự trong lô.
 * </ol>
 *
 * <h2>Vì sao không nhận tham số {@code domain}</h2>
 *
 * <p>PROJECT.md §5 phác chữ ký là {@code (List<byte[]> leaves, domain)}. Bản hiện thực này
 * <b>cố ý bỏ {@code domain}</b>: mỗi lá đã là
 * {@code keccak256(bytes8(domain) || ':' || JCS(payload))} nên miền neo đã nằm sẵn trong
 * từng lá. Thêm một tham số mà hàm không dùng tới sẽ gợi ý sai rằng hai cây khác miền được
 * tách nhau bởi thứ gì đó ngoài chính các lá. Việc tách miền thuộc về {@link LeafHasher} và
 * về khóa {@code (domain, batchId)} của {@code AnchorRegistry}.
 *
 * <h2>Ranh giới module</h2>
 *
 * <p>Không import gì từ nghiệp vụ (PROJECT.md §5) — vào {@code byte[]}, ra {@code byte[]}.
 * Giữ được ranh giới này thì phần đo đạc tuần 7 chỉ là gọi một hàm với N khác nhau.
 */
public final class MerkleService {

  /** Độ dài một nút: keccak256 luôn cho 32 byte. */
  public static final int HASH_BYTES = 32;

  private MerkleService() {}

  /**
   * Bằng chứng cho một lá.
   *
   * @param index vị trí của lá trong lô — bundle của sinh viên mang theo giá trị này
   * @param siblings các hash anh em, từ dưới lên gốc. Không có bit trái/phải vì cặp anh em
   *     đã được sắp xếp (quy ước 1).
   */
  public record Proof(int index, List<byte[]> siblings) {

    public Proof {
      siblings = List.copyOf(siblings);
    }

    /** Số tầng phải đi từ lá lên gốc. */
    public int depth() {
      return siblings.size();
    }
  }

  // ------------------------------------------------------------------ dựng cây

  /**
   * Merkle root của một lô.
   *
   * @throws IllegalArgumentException nếu lô rỗng, có lá trùng, hoặc lá sai độ dài
   */
  public static byte[] root(List<byte[]> leaves) {
    List<byte[]> level = validated(leaves);
    while (level.size() > 1) {
      level = nextLevel(level);
    }
    return level.get(0).clone();
  }

  /**
   * Sinh bằng chứng cho lá thứ {@code index}.
   *
   * <p>Lá bị đẩy lên (nút lẻ ở cuối tầng) không có anh em ở tầng đó, nên proof của nó ngắn
   * hơn proof của lá khác trong cùng cây. Đó là hành vi đúng, không phải lỗi.
   */
  public static Proof proof(List<byte[]> leaves, int index) {
    List<byte[]> level = validated(leaves);
    if (index < 0 || index >= level.size()) {
      throw new IllegalArgumentException(
          "index ngoài phạm vi: " + index + ", lô có " + level.size() + " lá");
    }

    List<byte[]> siblings = new ArrayList<>();
    int i = index;
    while (level.size() > 1) {
      int sibling = i ^ 1;
      if (sibling < level.size()) {
        siblings.add(level.get(sibling).clone());
      }
      // Nếu `sibling` vượt ra ngoài thì `i` là nút cuối của một tầng lẻ → được đẩy lên
      // nguyên vẹn, không có anh em để ghi vào proof (quy ước 2).
      i /= 2;
      level = nextLevel(level);
    }
    return new Proof(index, siblings);
  }

  /**
   * Xác minh một bằng chứng. Đây chính là phép tính mà verifier tĩnh chạy trong trình duyệt,
   * viết lại ở phía Java để test vector đối chiếu được cả hai chiều.
   */
  public static boolean verify(byte[] leaf, List<byte[]> siblings, byte[] expectedRoot) {
    if (leaf == null || leaf.length != HASH_BYTES) return false;
    if (expectedRoot == null || expectedRoot.length != HASH_BYTES) return false;

    byte[] node = leaf.clone();
    for (byte[] sibling : siblings) {
      if (sibling == null || sibling.length != HASH_BYTES) return false;
      node = hashPair(node, sibling);
    }
    return Arrays.equals(node, expectedRoot);
  }

  // ------------------------------------------------------------------ nội bộ

  /**
   * Một tầng lên tầng trên: ghép từng cặp, đẩy nút cuối lên nếu số nút lẻ.
   *
   * <p>Nút được đẩy lên KHÔNG bị băm lại — nó đi lên nguyên vẹn.
   */
  private static List<byte[]> nextLevel(List<byte[]> level) {
    int n = level.size();
    List<byte[]> next = new ArrayList<>((n + 1) / 2);
    for (int i = 0; i < n; i += 2) {
      if (i + 1 < n) {
        next.add(hashPair(level.get(i), level.get(i + 1)));
      } else {
        next.add(level.get(i));
      }
    }
    return next;
  }

  /**
   * {@code keccak256( min(a,b) || max(a,b) )}.
   *
   * <p>Dùng {@link Arrays#compareUnsigned} chứ KHÔNG phải {@link Arrays#compare}: kiểu
   * {@code byte} của Java có dấu, nên {@code Arrays.compare} coi {@code 0xFF} là −1 và xếp
   * nó TRƯỚC {@code 0x00}. JavaScript so sánh không dấu. Dùng nhầm hàm ở đây làm root lệch ở
   * mọi cặp có một hash bắt đầu bằng byte ≥ 0x80 — tức khoảng một nửa số cặp — và không có
   * gì báo lỗi.
   */
  private static byte[] hashPair(byte[] a, byte[] b) {
    byte[] first = Arrays.compareUnsigned(a, b) <= 0 ? a : b;
    byte[] second = first == a ? b : a;

    byte[] joined = new byte[HASH_BYTES * 2];
    System.arraycopy(first, 0, joined, 0, HASH_BYTES);
    System.arraycopy(second, 0, joined, HASH_BYTES, HASH_BYTES);
    // web3j đặt tên là sha3 vì lý do lịch sử; bên trong là Keccak-256 của EVM.
    return Hash.sha3(joined);
  }

  /**
   * Kiểm tra lô trước khi dựng cây, và trả về bản sao phòng thủ.
   *
   * <p>Từ chối lá trùng là có chủ ý: hai lá giống hệt nhau làm bằng chứng trở nên nhập nhằng
   * (một proof hợp lệ cho hai vị trí), và với {@code nonce} 16 byte bắt buộc trong mọi
   * payload thì trùng lá nghĩa là backend đã ghi trùng bản ghi — một lỗi cần vỡ ồn ào.
   */
  private static List<byte[]> validated(List<byte[]> leaves) {
    if (leaves == null || leaves.isEmpty()) {
      throw new IllegalArgumentException("Lô rỗng: không dựng được cây Merkle từ 0 lá.");
    }

    List<byte[]> copy = new ArrayList<>(leaves.size());
    Set<String> seen = new HashSet<>(leaves.size() * 2);

    for (int i = 0; i < leaves.size(); i++) {
      byte[] leaf = leaves.get(i);
      if (leaf == null || leaf.length != HASH_BYTES) {
        throw new IllegalArgumentException(
            "Lá thứ " + i + " phải dài đúng " + HASH_BYTES + " byte, nhận được "
                + (leaf == null ? "null" : leaf.length + " byte"));
      }
      if (!seen.add(Arrays.toString(leaf))) {
        throw new IllegalArgumentException(
            "Lá thứ " + i + " trùng với một lá trước đó. Mỗi payload có nonce 16 byte riêng"
                + " nên trùng lá nghĩa là lô chứa bản ghi lặp — sửa ở tầng gọi.");
      }
      copy.add(leaf.clone());
    }
    return copy;
  }
}
