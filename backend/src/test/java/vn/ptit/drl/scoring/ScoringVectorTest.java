package vn.ptit.drl.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.web3j.crypto.Hash;
import vn.ptit.drl.anchor.AnchorDomain;
import vn.ptit.drl.anchor.LeafHasher;

/**
 * Payload {@code SCORE} / {@code RULESET} và {@code evidence_hash} — NỬA JAVA.
 * Nửa JS: {@code verifier/test/score.test.mjs}.
 *
 * <p>Chạy: {@code .\scripts\test-backend.ps1 ScoringVectorTest}
 */
class ScoringVectorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HexFormat HEX = HexFormat.of();
  private static final JsonNode DOC = load();

  private static JsonNode load() {
    try (InputStream in = ScoringVectorTest.class.getResourceAsStream("/canonical-vectors.json")) {
      if (in == null) {
        throw new IllegalStateException(
            "Không thấy canonical-vectors.json. Sinh lại: cd verifier && npm run gen-vectors");
      }
      return MAPPER.readTree(in);
    } catch (Exception e) {
      throw new IllegalStateException("Không đọc được bộ test vector", e);
    }
  }

  private static List<JsonNode> vectors(String tienTo) {
    List<JsonNode> out = new ArrayList<>();
    DOC.get("vectors").forEach(v -> {
      if (v.get("id").textValue().startsWith(tienTo)) {
        out.add(v);
      }
    });
    return out;
  }

  private static byte[] hex(String s) {
    return HEX.parseHex(s.startsWith("0x") ? s.substring(2) : s);
  }

  /** Lá giả có chữ cái, để phép kiểm chữ thường có tác dụng thật. */
  private static String la(String hai) {
    return "0x" + hai.repeat(32);
  }

  // ------------------------------------------------------------------ SCORE

  @TestFactory
  @DisplayName("ScorePayload → leaf khớp vector do JS sinh")
  List<DynamicTest> scoreKhopVector() {
    List<DynamicTest> tests = new ArrayList<>();
    for (JsonNode v : vectors("score-payload")) {
      tests.add(dynamicTest(v.get("id").textValue(), () -> {
        JsonNode p = v.get("payload");

        Map<String, Object> payload = ScorePayload.of(
            p.get("studentCode").textValue(),
            p.get("semester").textValue(),
            p.get("rulesetVersion").textValue(),
            hex(p.get("rulesetHash").textValue()),
            Instant.parse(p.get("scoredAt").textValue()),
            Map.of("C1", p.get("c1").intValue(), "C2", p.get("c2").intValue(),
                "C3", p.get("c3").intValue(), "C4", p.get("c4").intValue(),
                "C5", p.get("c5").intValue()),
            p.get("total").intValue(),
            p.get("classification").isNull() ? null : p.get("classification").textValue(),
            hex(p.get("evidenceHash").textValue()),
            hex(p.get("nonce").textValue()));

        assertEquals(v.get("expected").get("jcs").textValue(),
            vn.ptit.drl.anchor.Jcs.canonicalize(payload));
        assertEquals(v.get("expected").get("leaf").textValue(),
            LeafHasher.leafHex(AnchorDomain.SCORE, payload));
      }));
    }
    return tests;
  }

  @Test
  @DisplayName("Đúng 14 trường, khớp SCORE_FIELDS phía JS")
  void scoreDu14Truong() {
    assertEquals(14, vectors("score-payload").get(0).get("payload").size());
  }

  @Test
  @DisplayName("Sàn của bộ quy tắc: không hoạt động nào vẫn 40 điểm nhờ điểm mặc định")
  void sanCuaBoQuyTac() {
    JsonNode p = vectors("score-payload").stream()
        .filter(v -> v.get("id").textValue().contains("khong-hoat-dong"))
        .findFirst().orElseThrow().get("payload");

    assertEquals(40, p.get("total").intValue());
    assertEquals(25, p.get("c2").intValue(), "C2 là điểm mặc định, không chấm từ dữ liệu");
    assertEquals(15, p.get("c4").intValue(), "C4 điểm nền, phần không đo được");
    assertEquals("YEU", p.get("classification").textValue());
  }

  // ------------------------------------------------------------------ RULESET

  @TestFactory
  @DisplayName("RulesetPayload → leaf khớp vector")
  List<DynamicTest> rulesetKhopVector() {
    List<DynamicTest> tests = new ArrayList<>();
    for (JsonNode v : vectors("ruleset-payload")) {
      tests.add(dynamicTest(v.get("id").textValue(), () -> {
        JsonNode p = v.get("payload");

        Map<String, Object> payload = RulesetPayload.of(
            p.get("version").textValue(),
            p.get("semester").textValue(),
            hex(p.get("rulesetHash").textValue()),
            Instant.parse(p.get("effectiveFrom").textValue()),
            hex(p.get("nonce").textValue()));

        assertEquals(v.get("expected").get("leaf").textValue(),
            LeafHasher.leafHex(AnchorDomain.RULESET, payload));
      }));
    }
    return tests;
  }

  @Test
  @DisplayName("rulesetHash băm BYTE THÔ — đổi một khoảng trắng là đổi hash")
  void rulesetHashByteTho() {
    String a = "{\"a\":1}";
    String b = "{ \"a\": 1 }";

    assertEquals("0x" + HEX.formatHex(Hash.sha3(a.getBytes(StandardCharsets.UTF_8))),
        RulesetPayload.rulesetHashHex(a));
    assertNotEquals(RulesetPayload.rulesetHashHex(a), RulesetPayload.rulesetHashHex(b),
        "Nếu hai chuỗi này ra cùng hash thì ở đâu đó đang canonical hóa — sai đặc tả.");
  }

  @Test
  @DisplayName("Tiếng Việt có dấu trong bộ quy tắc đi bằng byte UTF-8 thô")
  void rulesetTiengViet() {
    String json = "{\"ten\":\"Ý thức học tập\"}";
    assertEquals("0x" + HEX.formatHex(Hash.sha3(json.getBytes(StandardCharsets.UTF_8))),
        RulesetPayload.rulesetHashHex(json));
  }

  // ------------------------------------------------------------------ evidence

  @Nested
  @DisplayName("evidenceHash — đóng góp học thuật của đề tài")
  class Evidence {

    @Test
    @DisplayName("Thứ tự đầu vào KHÔNG ảnh hưởng kết quả")
    void thuTuKhongAnhHuong() {
      assertEquals(
          EvidenceHasher.hashHex(List.of(la("11"), la("22"), la("33"))),
          EvidenceHasher.hashHex(List.of(la("33"), la("11"), la("22"))));
    }

    @Test
    @DisplayName("Đổi hoặc thêm một bản ghi là đổi hash")
    void doiBanGhiThiDoiHash() {
      String goc = EvidenceHasher.hashHex(List.of(la("11"), la("22")));
      assertNotEquals(goc, EvidenceHasher.hashHex(List.of(la("11"), la("23"))));
      assertNotEquals(goc, EvidenceHasher.hashHex(List.of(la("11"), la("22"), la("33"))));
    }

    @Test
    @DisplayName("Danh sách RỖNG hợp lệ — và khác hash của danh sách có phần tử")
    void rongHopLe() {
      String rong = EvidenceHasher.hashHex(List.of());
      assertTrue(rong.matches("^0x[0-9a-f]{64}$"));
      assertNotEquals(rong, EvidenceHasher.hashHex(List.of(la("11"))));
    }

    @Test
    @DisplayName("Lá TRÙNG bị từ chối — một bản ghi bị đếm hai lần thì điểm sai")
    void laTrung() {
      assertThrows(IllegalArgumentException.class,
          () -> EvidenceHasher.hash(List.of(la("11"), la("11"))));
    }

    @Test
    @DisplayName("Lá chữ HOA hoặc sai độ dài bị từ chối")
    void laSaiDinhDang() {
      assertThrows(IllegalArgumentException.class,
          () -> EvidenceHasher.hash(List.of("0xabcd")));
      assertThrows(IllegalArgumentException.class,
          () -> EvidenceHasher.hash(List.of(la("ab").toUpperCase(java.util.Locale.ROOT)
              .replace("0X", "0x"))));
    }

    @Test
    @DisplayName("Công thức khớp đặc tả — JCS của {domain, leaves đã sắp}")
    void congThuc() {
      String mong = "{\"domain\":\"ATTEND\",\"leaves\":[\"" + la("11") + "\",\"" + la("22")
          + "\"]}";
      assertEquals("0x" + HEX.formatHex(Hash.sha3(mong.getBytes(StandardCharsets.UTF_8))),
          EvidenceHasher.hashHex(List.of(la("22"), la("11"))));
    }
  }

  // ------------------------------------------------------------------ từ chối

  @Nested
  @DisplayName("Payload phải vỡ ồn ào")
  class TuChoi {

    private final Map<String, Integer> diem =
        Map.of("C1", 8, "C2", 25, "C3", 12, "C4", 20, "C5", 0);

    @Test
    @DisplayName("Thiếu evidenceHash — điểm không có bằng chứng thì neo nó vô nghĩa")
    void thieuEvidence() {
      assertThrows(IllegalStateException.class, () -> ScorePayload.of(
          "B21DCCN002", "2026-1", "v1", new byte[32], Instant.now(), diem, 65, "KHA",
          null, new byte[16]));
    }

    @Test
    @DisplayName("Thiếu rulesetHash")
    void thieuRuleset() {
      assertThrows(IllegalStateException.class, () -> ScorePayload.of(
          "B21DCCN002", "2026-1", "v1", null, Instant.now(), diem, 65, "KHA",
          new byte[32], new byte[16]));
    }

    @Test
    @DisplayName("Thiếu nonce")
    void thieuNonce() {
      assertThrows(IllegalStateException.class, () -> ScorePayload.of(
          "B21DCCN002", "2026-1", "v1", new byte[32], Instant.now(), diem, 65, "KHA",
          new byte[32], null));
    }

    @Test
    @DisplayName("Bộ quy tắc rỗng không băm được")
    void rulesetRong() {
      assertThrows(IllegalArgumentException.class, () -> RulesetPayload.rulesetHash(""));
      assertThrows(IllegalArgumentException.class, () -> RulesetPayload.rulesetHash("  "));
    }
  }
}
