package vn.ptit.drl.anchor;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.web3j.crypto.Hash;

/**
 * Test vector canonicalization — NỬA JAVA.
 * Nửa JS: {@code verifier/test/canonical.test.mjs}.
 *
 * <p>Chạy: {@code ./mvnw test -Dtest=CanonicalVectorTest}
 *
 * <p>Hai phía đọc CÙNG MỘT file {@code canonical-vectors.json}. Xanh một phía không có
 * nghĩa gì — {@code /canonical-hash} yêu cầu xanh cả hai. Nếu test này đỏ, đừng sinh
 * lại file vector: file vector là hợp đồng, đỏ nghĩa là một trong hai phía sai.
 *
 * <p>Jackson chỉ xuất hiện ở đây để ĐỌC file vector. Đường đi canonicalization thật
 * ({@link Jcs}) không chạm tới Jackson — xem ghi chú trong lớp đó.
 */
class CanonicalVectorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final JsonNode DOC = load();

  private static JsonNode load() {
    try (InputStream in =
        CanonicalVectorTest.class.getResourceAsStream("/canonical-vectors.json")) {
      if (in == null) {
        throw new IllegalStateException(
            "Không thấy canonical-vectors.json. Sinh lại bằng: cd verifier && npm run gen-vectors");
      }
      return MAPPER.readTree(in);
    } catch (Exception e) {
      throw new IllegalStateException("Không đọc được bộ test vector", e);
    }
  }

  /**
   * Chuyển {@link JsonNode} về cây giá trị thuần Java mà {@link Jcs} nhận.
   *
   * <p>Cố ý KHÔNG dùng {@code MAPPER.convertValue(node, Map.class)}: cách đó để Jackson
   * tự quyết kiểu số, đúng thứ lớp này tồn tại để tránh. Ở đây ánh xạ tường minh —
   * số nguyên sang {@code Long}/{@code BigInteger}, số thực sang {@code Double}.
   */
  private static Object toJava(JsonNode n) {
    if (n == null || n.isNull()) return null;
    if (n.isTextual()) return n.textValue();
    if (n.isBoolean()) return n.booleanValue();
    if (n.isIntegralNumber()) {
      return n.canConvertToLong() ? (Object) n.longValue() : (Object) n.bigIntegerValue();
    }
    if (n.isNumber()) return n.doubleValue();
    if (n.isArray()) {
      List<Object> list = new ArrayList<>();
      n.forEach(child -> list.add(toJava(child)));
      return list;
    }
    if (n.isObject()) {
      Map<String, Object> map = new LinkedHashMap<>();
      n.fields().forEachRemaining(e -> map.put(e.getKey(), toJava(e.getValue())));
      return map;
    }
    throw new IllegalStateException("Nút JSON không hỗ trợ: " + n.getNodeType());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> payloadOf(JsonNode v) {
    return (Map<String, Object>) toJava(v.get("payload"));
  }

  // ===================================================================================
  // Bộ vector dùng chung
  // ===================================================================================

  @TestFactory
  @DisplayName("Vector dùng chung — chuỗi JCS, bytes8 của miền, leaf hash")
  List<DynamicTest> vectors() {
    List<DynamicTest> tests = new ArrayList<>();
    for (JsonNode v : DOC.get("vectors")) {
      String id = v.get("id").asText();
      AnchorDomain domain = AnchorDomain.of(v.get("domain").asText());
      Map<String, Object> payload = payloadOf(v);
      JsonNode expected = v.get("expected");
      String why = v.get("why").asText();

      tests.add(
          dynamicTest(
              id + " — chuỗi JCS khớp",
              () -> assertEquals(expected.get("jcs").asText(), Jcs.canonicalize(payload), why)));
      tests.add(
          dynamicTest(
              id + " — bytes8 của miền khớp",
              () ->
                  assertEquals(
                      expected.get("domainBytes8").asText(),
                      "0x" + HexFormat.of().formatHex(domain.toBytes8()))));
      tests.add(
          dynamicTest(
              id + " — số byte tiền ảnh khớp",
              () ->
                  assertEquals(
                      expected.get("preimageBytes").asInt(),
                      LeafHasher.preimage(domain, payload).length)));
      tests.add(
          dynamicTest(
              id + " — leaf hash khớp",
              () -> assertEquals(expected.get("leaf").asText(), LeafHasher.leafHex(domain, payload))));
    }
    return tests;
  }

  @TestFactory
  @DisplayName("Payload BẮT BUỘC bị từ chối — chặn lệch hash im lặng")
  List<DynamicTest> rejects() {
    List<DynamicTest> tests = new ArrayList<>();
    for (JsonNode r : DOC.get("rejects")) {
      String id = r.get("id").asText();
      String domainName = r.get("domain").asText();
      String expectedError = r.get("expectedError").asText();
      String why = r.get("why").asText();

      tests.add(
          dynamicTest(
              "từ chối: " + id,
              () -> {
                Exception e =
                    assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                          AnchorDomain d = AnchorDomain.of(domainName); // có thể ném ở đây
                          LeafHasher.leafHex(d, payloadOf(r));
                        },
                        why);
                assertTrue(
                    e.getMessage().contains(expectedError),
                    "mong đợi lỗi chứa \"" + expectedError + "\", nhận được: " + e.getMessage());
              }));
    }
    return tests;
  }

  // ===================================================================================
  // Bẫy Java↔JS đã biết — đối xứng với verifier/test/canonical.test.mjs
  // ===================================================================================

  private static final String NONCE = "0x9f86d081884c7d659a2feaa0c55ad015";

  @Test
  @DisplayName("web3j Hash.sha3 là Keccak-256 chứ không phải SHA3-256 của NIST")
  void keccakKhongPhaiSha3() {
    // Nếu dòng này đỏ thì toàn bộ leaf hash sai so với EVM. keccak256("") theo EVM:
    assertEquals(
        "c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470",
        HexFormat.of().formatHex(Hash.sha3(new byte[0])));
  }

  @Test
  @DisplayName("số thực nguyên vẹn KHÔNG có đuôi .0 — Double.toString(20.0) cho \"20.0\"")
  void soThucNguyenVen() {
    assertAll(
        () -> assertEquals("{\"a\":20}", Jcs.canonicalize(Map.of("a", 20.0d))),
        () -> assertEquals("{\"a\":100}", Jcs.canonicalize(Map.of("a", 100.0d))),
        () -> assertEquals("{\"a\":100}", Jcs.canonicalize(Map.of("a", 100L))),
        () -> assertEquals("{\"a\":100}", Jcs.canonicalize(Map.of("a", new BigInteger("100")))));
  }

  @Test
  @DisplayName("-0.0 gộp về \"0\"")
  void amKhong() {
    assertEquals("{\"a\":0}", Jcs.canonicalize(Map.of("a", -0.0d)));
  }

  @Test
  @DisplayName("sắp xếp khóa là đệ quy ở mọi cấp")
  void sapXepDeQuy() {
    Map<String, Object> inner = new LinkedHashMap<>();
    inner.put("d", 1L);
    inner.put("c", 2L);
    Map<String, Object> outer = new LinkedHashMap<>();
    outer.put("b", inner);
    outer.put("a", 3L);
    assertEquals("{\"a\":3,\"b\":{\"c\":2,\"d\":1}}", Jcs.canonicalize(outer));
  }

  @Test
  @DisplayName("mảng GIỮ NGUYÊN thứ tự — không sắp xếp")
  void mangGiuThuTu() {
    assertEquals("[\"c\",\"a\",\"b\"]", Jcs.canonicalize(List.of("c", "a", "b")));
  }

  @Test
  @DisplayName("null giữ nguyên, không bị lược bỏ")
  void giuNull() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("a", null);
    m.put("b", 1L);
    assertEquals("{\"a\":null,\"b\":1}", Jcs.canonicalize(m));
  }

  @Test
  @DisplayName("tiếng Việt giữ UTF-8 thô, không escape \\uXXXX")
  void tiengVietUtf8Tho() {
    String s = Jcs.canonicalize(Map.of("n", "Nguyễn Ngọc Hoàng"));
    assertAll(
        () -> assertEquals("{\"n\":\"Nguyễn Ngọc Hoàng\"}", s),
        () -> assertTrue(!s.contains("\\u"), "không được escape ký tự có dấu"),
        // Chốt luôn ở tầng byte: chuỗi phải ra UTF-8 nhiều byte, không phải ASCII escape.
        () -> assertTrue(s.getBytes(StandardCharsets.UTF_8).length > s.length()));
  }

  @Test
  @DisplayName("ký tự điều khiển dùng chuỗi thoát ngắn, còn lại là \\u00xx chữ thường")
  void kyTuDieuKhien() {
    assertAll(
        () -> assertEquals("{\"a\":\"\\n\\t\\r\\b\\f\"}", Jcs.canonicalize(Map.of("a", "\n\t\r\b\f"))),
        () -> assertEquals("{\"a\":\"\\u0001\"}", Jcs.canonicalize(Map.of("a", "\u0001"))));
  }

  @Test
  @DisplayName("NaN và Infinity bị từ chối")
  void soKhongHuuHan() {
    assertAll(
        () ->
            assertTrue(
                assertThrows(
                        IllegalArgumentException.class,
                        () -> Jcs.canonicalize(Map.of("a", Double.NaN)))
                    .getMessage()
                    .contains("hữu hạn")),
        () ->
            assertTrue(
                assertThrows(
                        IllegalArgumentException.class,
                        () -> Jcs.canonicalize(Map.of("a", Double.POSITIVE_INFINITY)))
                    .getMessage()
                    .contains("hữu hạn")));
  }

  @Test
  @DisplayName("số thực ngoài vùng [1e-3, 1e7) bị từ chối thay vì lệch âm thầm")
  void soThucNgoaiVung() {
    assertAll(
        () ->
            assertThrows(
                IllegalArgumentException.class, () -> Jcs.canonicalize(Map.of("a", 0.0001d))),
        () ->
            assertThrows(
                IllegalArgumentException.class, () -> Jcs.canonicalize(Map.of("a", 12345678.5d))));
  }

  @Test
  @DisplayName("hai đầu vùng an toàn vẫn phải serialize được")
  void bienVungAnToan() {
    assertAll(
        () -> assertEquals("{\"a\":0.001}", Jcs.canonicalize(Map.of("a", 0.001d))),
        () -> assertEquals("{\"a\":9999999.5}", Jcs.canonicalize(Map.of("a", 9999999.5d))));
  }

  @Test
  @DisplayName("payload thiếu nonce bị từ chối ở mọi miền")
  void thieuNonce() {
    for (AnchorDomain d : AnchorDomain.values()) {
      assertTrue(
          assertThrows(IllegalArgumentException.class, () -> LeafHasher.leafHex(d, Map.of("x", 1L)))
              .getMessage()
              .contains("nonce"),
          "miền " + d + " phải từ chối payload thiếu nonce");
    }
  }

  @Test
  @DisplayName("nonce sinh ra tự nó phải hợp lệ và không trùng nhau")
  void sinhNonce() {
    Set<String> seen =
        java.util.stream.IntStream.range(0, 1000)
            .mapToObj(i -> LeafHasher.newNonce())
            .collect(Collectors.toSet());
    assertAll(
        () -> assertEquals(1000, seen.size(), "1000 lần sinh không được trùng nhau"),
        () ->
            assertTrue(
                seen.stream().allMatch(n -> n.matches("^0x[0-9a-f]{32}$")),
                "nonce phải là hex chữ thường 16 byte, tiền tố 0x"),
        // Nonce tự sinh phải qua được chính bộ kiểm tra của LeafHasher.
        () -> LeafHasher.leafHex(AnchorDomain.ATTEND, Map.of("nonce", LeafHasher.newNonce())));
  }

  @Test
  @DisplayName("cùng payload, khác miền → khác leaf")
  void mienPhanTachCay() {
    Map<String, Object> p = Map.of("x", 1L, "nonce", NONCE);
    Set<String> seen =
        java.util.Arrays.stream(AnchorDomain.values())
            .map(d -> LeafHasher.leafHex(d, p))
            .collect(Collectors.toSet());
    assertEquals(AnchorDomain.values().length, seen.size(), "miền neo phải phân tách được các cây");
  }

  @Test
  @DisplayName("trường vắng mặt KHÁC trường null — có chủ ý, ghi trong đặc tả")
  void vangMatKhacNull() {
    Map<String, Object> coNull = new LinkedHashMap<>();
    coNull.put("a", null);
    coNull.put("nonce", NONCE);
    assertNotEquals(
        LeafHasher.leafHex(AnchorDomain.ATTEND, Map.of("nonce", NONCE)),
        LeafHasher.leafHex(AnchorDomain.ATTEND, coNull));
  }
}
