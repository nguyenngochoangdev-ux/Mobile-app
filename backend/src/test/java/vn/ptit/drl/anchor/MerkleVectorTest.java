package vn.ptit.drl.anchor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.web3j.crypto.Hash;

/**
 * Test vector Merkle — NỬA JAVA.
 * Nửa JS: {@code verifier/test/merkle.test.mjs}.
 *
 * <p>Chạy: {@code ./mvnw test -Dtest=MerkleVectorTest}
 *
 * <p>Hai phía đọc CÙNG MỘT file {@code merkle-vectors.json}, sinh bởi
 * {@code verifier/scripts/gen-merkle-vectors.mjs} dùng {@code merkletreejs}. Java hiện thực
 * cây một cách độc lập ({@link MerkleService}) — nên xanh ở đây nghĩa là hai hiện thực khớp
 * nhau từng byte, không phải hai bản sao của cùng một đoạn mã.
 *
 * <p>Test đỏ = một trong hai phía sai, KHÔNG phải file vector sai. Đừng chạy
 * {@code gen-merkle-vectors} để "sửa" nó — làm thế là xóa mất bằng chứng.
 *
 * <p>Jackson chỉ xuất hiện ở đây để ĐỌC file vector; {@link MerkleService} không chạm tới nó.
 */
class MerkleVectorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HexFormat HEX = HexFormat.of();
  private static final JsonNode DOC = load();

  private static JsonNode load() {
    try (InputStream in = MerkleVectorTest.class.getResourceAsStream("/merkle-vectors.json")) {
      if (in == null) {
        throw new IllegalStateException(
            "Không thấy merkle-vectors.json. Sinh lại bằng:"
                + " cd verifier && npm run gen-merkle-vectors");
      }
      return MAPPER.readTree(in);
    } catch (Exception e) {
      throw new IllegalStateException("Không đọc được bộ test vector Merkle", e);
    }
  }

  private static byte[] bytes(String hex) {
    return HEX.parseHex(hex.startsWith("0x") ? hex.substring(2) : hex);
  }

  private static String hex(byte[] b) {
    return "0x" + HEX.formatHex(b);
  }

  private static List<byte[]> leavesOf(JsonNode tree) {
    List<byte[]> out = new ArrayList<>();
    for (JsonNode leaf : tree.get("leaves")) {
      out.add(bytes(leaf.textValue()));
    }
    return out;
  }

  // ------------------------------------------------------------------ root

  @TestFactory
  @DisplayName("Root khớp vector do merkletreejs sinh")
  List<DynamicTest> root() {
    List<DynamicTest> tests = new ArrayList<>();
    for (JsonNode tree : DOC.get("trees")) {
      String id = tree.get("id").textValue();
      int n = tree.get("leafCount").intValue();
      tests.add(
          dynamicTest(
              id + " (n=" + n + ")",
              () ->
                  assertEquals(
                      tree.get("root").textValue(),
                      hex(MerkleService.root(leavesOf(tree))),
                      tree.get("why").textValue())));
    }
    return tests;
  }

  // ----------------------------------------------------------------- proof

  @TestFactory
  @DisplayName("Proof khớp vector, và verify được về root")
  List<DynamicTest> proof() {
    List<DynamicTest> tests = new ArrayList<>();
    for (JsonNode tree : DOC.get("trees")) {
      String id = tree.get("id").textValue();
      List<byte[]> leaves = leavesOf(tree);
      byte[] root = bytes(tree.get("root").textValue());

      for (JsonNode p : tree.get("proofs")) {
        int index = p.get("index").intValue();

        tests.add(
            dynamicTest(
                id + " index " + index + ": siblings khớp vector",
                () -> {
                  MerkleService.Proof actual = MerkleService.proof(leaves, index);
                  List<String> expected = new ArrayList<>();
                  for (JsonNode s : p.get("siblings")) expected.add(s.textValue());

                  assertEquals(expected.size(), actual.depth(), "số tầng của proof lệch");
                  for (int i = 0; i < expected.size(); i++) {
                    assertEquals(expected.get(i), hex(actual.siblings().get(i)), "sibling " + i);
                  }
                }));

        tests.add(
            dynamicTest(
                id + " index " + index + ": verify được về root",
                () -> {
                  List<byte[]> siblings = new ArrayList<>();
                  for (JsonNode s : p.get("siblings")) siblings.add(bytes(s.textValue()));
                  assertTrue(
                      MerkleService.verify(bytes(p.get("leaf").textValue()), siblings, root));
                }));
      }
    }
    return tests;
  }

  // -------------------------------------------------- proof phải thất bại

  @Test
  @DisplayName("Proof bị sửa thì KHÔNG verify được")
  void proofBiSua() {
    // Một hàm verify luôn trả true cũng làm mọi test trên xanh. Phần này chứng minh nó
    // thật sự kiểm tra.
    JsonNode tree = null;
    for (JsonNode t : DOC.get("trees")) {
      if (t.get("leafCount").intValue() >= 4) {
        tree = t;
        break;
      }
    }
    assertNotEquals(null, tree, "cần ít nhất một cây từ 4 lá trở lên trong bộ vector");

    List<byte[]> leaves = leavesOf(tree);
    byte[] root = bytes(tree.get("root").textValue());
    JsonNode p = tree.get("proofs").get(0);
    byte[] leaf = bytes(p.get("leaf").textValue());

    List<byte[]> siblings = new ArrayList<>();
    for (JsonNode s : p.get("siblings")) siblings.add(bytes(s.textValue()));

    byte[] otherLeaf = null;
    for (byte[] l : leaves) {
      if (!java.util.Arrays.equals(l, leaf)) {
        otherLeaf = l;
        break;
      }
    }
    assertFalse(MerkleService.verify(otherLeaf, siblings, root), "sai lá mà vẫn qua");

    byte[] wrongRoot = new byte[MerkleService.HASH_BYTES];
    java.util.Arrays.fill(wrongRoot, (byte) 0xFF);
    assertFalse(MerkleService.verify(leaf, siblings, wrongRoot), "sai root mà vẫn qua");

    List<byte[]> tampered = new ArrayList<>(siblings);
    byte[] first = tampered.get(0).clone();
    first[0] ^= 0x01;
    tampered.set(0, first);
    assertFalse(MerkleService.verify(leaf, tampered, root), "sibling bị sửa mà vẫn qua");

    assertFalse(
        MerkleService.verify(leaf, siblings.subList(1, siblings.size()), root),
        "thiếu sibling mà vẫn qua");
  }

  // ------------------------------------------------------ phải bị từ chối

  @TestFactory
  @DisplayName("Trường hợp bắt buộc bị từ chối")
  List<DynamicTest> rejects() {
    List<DynamicTest> tests = new ArrayList<>();
    for (JsonNode r : DOC.get("rejects")) {
      String id = r.get("id").textValue();
      tests.add(
          dynamicTest(
              id,
              () -> {
                List<byte[]> leaves = new ArrayList<>();
                for (JsonNode leaf : r.get("leaves")) {
                  leaves.add(bytes(leaf.textValue()));
                }
                // Dù lô rỗng, lá trùng hay lá sai độ dài — đều phải ném, tức vỡ ồn ào.
                assertThrows(
                    IllegalArgumentException.class,
                    () -> MerkleService.root(leaves),
                    r.get("why").textValue());
              }));
    }
    return tests;
  }

  @Test
  @DisplayName("index ngoài phạm vi bị từ chối")
  void indexNgoaiPhamVi() {
    List<byte[]> leaves = leavesOf(DOC.get("trees").get(1));
    assertThrows(
        IllegalArgumentException.class, () -> MerkleService.proof(leaves, leaves.size()));
    assertThrows(IllegalArgumentException.class, () -> MerkleService.proof(leaves, -1));
  }

  // ------------------------------------------- ba quy ước, chốt trực tiếp

  private static byte[] k(int b) {
    return Hash.sha3(new byte[] {(byte) b});
  }

  @Test
  @DisplayName("Nút nội bộ là keccak256(min || max), so sánh KHÔNG DẤU")
  void nutNoiBo() {
    byte[] a = k(1);
    byte[] b = k(2);
    byte[] first = java.util.Arrays.compareUnsigned(a, b) <= 0 ? a : b;
    byte[] second = first == a ? b : a;

    byte[] joined = new byte[64];
    System.arraycopy(first, 0, joined, 0, 32);
    System.arraycopy(second, 0, joined, 32, 32);

    assertArrayEquals(Hash.sha3(joined), MerkleService.root(List.of(a, b)));
  }

  @Test
  @DisplayName("Đảo thứ tự hai lá KHÔNG đổi root — hệ quả của việc sắp xếp cặp")
  void daoThuTuHaiLa() {
    assertArrayEquals(
        MerkleService.root(List.of(k(1), k(2))), MerkleService.root(List.of(k(2), k(1))));
  }

  @Test
  @DisplayName("Cây một lá: root chính là lá, proof rỗng")
  void motLa() {
    assertArrayEquals(k(1), MerkleService.root(List.of(k(1))));
    assertEquals(0, MerkleService.proof(List.of(k(1)), 0).depth());
  }

  @Test
  @DisplayName("Nút lẻ được ĐẨY LÊN, không nhân đôi kiểu Bitcoin")
  void nutLeDayLen() {
    // Nếu quy ước là nhân đôi thì root của cây 3 lá sẽ bằng root khi thay lá cuối bằng
    // cặp (C, C) — công thức dưới đây dựng đúng cây "nhân đôi" để đối chứng.
    byte[] a = k(1);
    byte[] b = k(2);
    byte[] c = k(3);

    byte[] ab = MerkleService.root(List.of(a, b));
    byte[] rootDayLen = MerkleService.root(List.of(ab, c)); // C đi lên nguyên vẹn
    assertArrayEquals(rootDayLen, MerkleService.root(List.of(a, b, c)));

    byte[] cc = new byte[64];
    System.arraycopy(c, 0, cc, 0, 32);
    System.arraycopy(c, 0, cc, 32, 32);
    byte[] rootNhanDoi = MerkleService.root(List.of(ab, Hash.sha3(cc)));
    assertNotEquals(
        HEX.formatHex(rootNhanDoi),
        HEX.formatHex(MerkleService.root(List.of(a, b, c))),
        "cây 3 lá không được dùng quy ước nhân đôi");
  }

  @Test
  @DisplayName("Lá bị đẩy lên có proof NGẮN HƠN các lá khác")
  void proofNganHon() {
    List<byte[]> leaves = List.of(k(1), k(2), k(3));
    assertEquals(2, MerkleService.proof(leaves, 0).depth());
    assertEquals(1, MerkleService.proof(leaves, 2).depth());
  }

  @Test
  @DisplayName("Thứ tự lá được giữ nguyên, không sắp xếp")
  void giuThuTuLa() {
    assertNotEquals(
        HEX.formatHex(MerkleService.root(List.of(k(1), k(2), k(3)))),
        HEX.formatHex(MerkleService.root(List.of(k(3), k(2), k(1)))));
  }

  @Test
  @DisplayName("Arrays.compare CÓ DẤU cho ra root khác — bẫy phải chặn được")
  void bayCompareCoDau() {
    // Lấy đúng cây bẫy trong bộ vector: một lá có byte đầu >= 0x80, lá kia < 0x80.
    JsonNode trap = null;
    for (JsonNode t : DOC.get("trees")) {
      if ("bay-so-sanh-co-dau".equals(t.get("id").textValue())) trap = t;
    }
    assertNotEquals(null, trap, "bộ vector phải có cây bay-so-sanh-co-dau");

    List<byte[]> leaves = leavesOf(trap);
    byte[] a = leaves.get(0);
    byte[] b = leaves.get(1);

    // Chứng minh hai cách so sánh THẬT SỰ khác nhau trên cặp này — nếu không, cây bẫy
    // đã mất tác dụng và cần sinh lại bộ vector.
    assertNotEquals(
        Integer.signum(java.util.Arrays.compare(a, b)),
        Integer.signum(java.util.Arrays.compareUnsigned(a, b)),
        "cặp bẫy không còn phân biệt được hai cách so sánh");

    assertEquals(trap.get("root").textValue(), hex(MerkleService.root(leaves)));
  }
}
