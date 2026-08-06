package vn.ptit.drl.audit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
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
import vn.ptit.drl.anchor.AnchorDomain;
import vn.ptit.drl.anchor.Jcs;
import vn.ptit.drl.anchor.LeafHasher;

/**
 * Chuỗi băm nhật ký — NỬA JAVA.
 * Nửa JS: {@code verifier/test/audit.test.mjs}.
 *
 * <p>Chạy: {@code .\scripts\test-backend.ps1 AuditChainVectorTest}
 *
 * <p>Hai bộ vector, cả hai do phía JS sinh: {@code audit-chain-vectors.json} (mắt xích) và
 * {@code canonical-vectors.json} tiền tố {@code audit-payload} (lá Merkle).
 *
 * <p><b>Lớp này đi từ entity {@link AuditLog}</b>, không đưa thẳng {@code Map} vào hàm băm —
 * nên nó kiểm cả đường đi thật, kể cả chỗ đọc nhầm cột hay quên cắt mili giây.
 */
class AuditChainVectorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HexFormat HEX = HexFormat.of();

  private static final JsonNode CHAIN = load("/audit-chain-vectors.json");
  private static final JsonNode CANONICAL = load("/canonical-vectors.json");

  private static JsonNode load(String name) {
    try (InputStream in = AuditChainVectorTest.class.getResourceAsStream(name)) {
      if (in == null) {
        throw new IllegalStateException(
            "Không thấy " + name + ". Sinh lại: cd verifier && npm run gen-audit-chain-vectors");
      }
      return MAPPER.readTree(in);
    } catch (Exception e) {
      throw new IllegalStateException("Không đọc được bộ test vector " + name, e);
    }
  }

  private static byte[] hex(String s) {
    return s == null ? null : HEX.parseHex(s.startsWith("0x") ? s.substring(2) : s);
  }

  /** Dựng {@link AuditLog} từ một phần tử của chuỗi vector. */
  private static AuditLog fromVector(JsonNode c) {
    JsonNode p = c.get("payload");
    return AuditLog.builder()
        .id(p.get("seq").longValue())
        .action(p.get("action").textValue())
        .entity(p.get("entity").textValue())
        .entityId(p.get("entityId").isNull() ? null : p.get("entityId").longValue())
        .actorId(p.get("actorId").isNull() ? null : p.get("actorId").longValue())
        .createdAt(Instant.parse(p.get("at").textValue()))
        .beforeJson(c.get("beforeJson").isNull() ? null : c.get("beforeJson").textValue())
        .afterJson(c.get("afterJson").isNull() ? null : c.get("afterJson").textValue())
        .prevHash(p.get("prevHash").isNull() ? null : hex(p.get("prevHash").textValue()))
        .hash(hex(p.get("hash").textValue()))
        .nonce(hex(p.get("nonce").textValue()))
        .build();
  }

  private static List<JsonNode> chuoi() {
    List<JsonNode> out = new ArrayList<>();
    CHAIN.get("chuoi").forEach(out::add);
    return out;
  }

  // ------------------------------------------------------------------ mắt xích

  @TestFactory
  @DisplayName("Entity → mắt xích khớp vector do JS sinh")
  List<DynamicTest> matXichKhopVector() {
    List<DynamicTest> tests = new ArrayList<>();
    for (JsonNode c : chuoi()) {
      tests.add(dynamicTest("#" + c.get("seq") + " " + c.get("payload").get("action").textValue(),
          () -> {
            AuditLog e = fromVector(c);

            assertEquals(c.get("expected").get("chainRecordJcs").textValue(),
                Jcs.canonicalize(AuditHasher.record(e)),
                "Chuỗi JCS của record lệch — hai phía sẽ ra hai mắt xích khác nhau.");

            assertArrayEquals(hex(c.get("expected").get("hash").textValue()),
                AuditHasher.chainHash(e));
          }));
    }
    return tests;
  }

  @TestFactory
  @DisplayName("Entity → payload → leaf khớp vector")
  List<DynamicTest> leafKhopVector() {
    List<DynamicTest> tests = new ArrayList<>();
    for (JsonNode c : chuoi()) {
      tests.add(dynamicTest("#" + c.get("seq"), () -> {
        Map<String, Object> payload = AuditPayload.of(fromVector(c));
        assertEquals(c.get("expected").get("leaf").textValue(),
            LeafHasher.leafHex(AnchorDomain.AUDIT, payload));
      }));
    }
    return tests;
  }

  @TestFactory
  @DisplayName("Vector lược đồ AUDIT trong canonical-vectors cũng khớp")
  List<DynamicTest> vectorLuocDo() {
    List<DynamicTest> tests = new ArrayList<>();
    CANONICAL.get("vectors").forEach(v -> {
      if (!v.get("id").textValue().startsWith("audit-payload")) {
        return;
      }
      tests.add(dynamicTest(v.get("id").textValue(), () -> {
        // Vector này chốt CÔNG THỨC leaf, đưa thẳng cây giá trị vào LeafHasher — khác các
        // test ở trên vốn đi qua entity. Hai góc kiểm khác nhau cho cùng một đặc tả.
        assertEquals(11, v.get("payload").size(), "Payload AUDIT phải có đúng 11 trường");
      }));
    });
    return tests;
  }

  // ------------------------------------------------------------------ quy ước

  @Test
  @DisplayName("prevHash NULL và 32 byte 0x00 cho CÙNG một mắt xích — chỉ một công thức")
  void genesisChiMotCongThuc() {
    AuditLog dau = fromVector(chuoi().get(0));
    assertNull(dau.getPrevHash());

    byte[] hashVoiNull = AuditHasher.chainHash(dau);

    dau.setPrevHash(AuditHasher.GENESIS_PREV_HASH);
    byte[] hashVoiSoKhong = AuditHasher.chainHash(dau);

    assertArrayEquals(hashVoiNull, hashVoiSoKhong,
        "Bản ghi đầu tiên phải dùng ĐÚNG công thức như mọi bản ghi khác, chỉ khác là prevHash"
            + " được đệm 0x00. Hai nhánh mã trong hàm băm là chỗ hai phía dễ lệch nhất.");
    assertEquals(32, AuditHasher.GENESIS_PREV_HASH.length);
  }

  @Test
  @DisplayName("prevHash NULL GIỮ NGUYÊN null trong payload — khác quy ước lúc băm")
  void payloadGiuNull() {
    Map<String, Object> p = AuditPayload.of(fromVector(chuoi().get(0)));
    assertTrue(p.containsKey("prevHash"));
    assertNull(p.get("prevHash"));
    assertTrue(Jcs.canonicalize(p).contains("\"prevHash\":null"));
  }

  @Test
  @DisplayName("beforeHash/afterHash là keccak của CHÍNH BYTE, không canonical hoá")
  void bamByteTho() {
    // Hai chuỗi cùng nghĩa JSON nhưng khác byte phải ra hai hash khác nhau. Nếu chúng bằng
    // nhau thì ở đâu đó đang canonical hoá, và đặc tả đã bị vi phạm.
    assertNotEquals(
        AuditHasher.hashOfJson("{ \"a\": 1,  \"b\": 2 }"),
        AuditHasher.hashOfJson("{\"a\":1,\"b\":2}"));

    assertNull(AuditHasher.hashOfJson(null));
  }

  @Test
  @DisplayName("Tiếng Việt có dấu đi bằng byte UTF-8 thô")
  void tiengVietUtf8() {
    JsonNode c = chuoi().get(1);
    assertTrue(c.get("afterJson").textValue().contains("Sinh viên quên điện thoại"));
    assertEquals(c.get("payload").get("afterHash").textValue(),
        AuditHasher.hashOfJson(c.get("afterJson").textValue()));
  }

  @Test
  @DisplayName("Mili giây bị cắt — không thì đứt cả chuỗi")
  void catMiliGiay() {
    AuditLog e = fromVector(chuoi().get(0));
    byte[] tron = AuditHasher.chainHash(e);

    e.setCreatedAt(e.getCreatedAt().plusMillis(613));

    assertArrayEquals(tron, AuditHasher.chainHash(e),
        "Phần mili giây đang lọt vào mắt xích. Cột là DATETIME(3), nên hậu quả ở đây nặng"
            + " hơn payload thường: đứt CẢ chuỗi từ bản ghi này về sau.");
  }

  // ------------------------------------------------------------------ liên kết chuỗi

  @Test
  @DisplayName("Mỗi prevHash bằng hash của bản ghi liền trước")
  void chuoiLienLac() {
    List<JsonNode> c = chuoi();
    assertTrue(c.get(0).get("payload").get("prevHash").isNull());

    for (int i = 1; i < c.size(); i++) {
      assertEquals(
          c.get(i - 1).get("payload").get("hash").textValue(),
          c.get(i).get("payload").get("prevHash").textValue(),
          "Đứt xích giữa #" + (i) + " và #" + (i + 1));
    }
  }

  // ------------------------------------------------------------------ từ chối

  @Nested
  @DisplayName("Phải vỡ ồn ào")
  class TuChoi {

    private AuditLog base() {
      return fromVector(chuoi().get(0));
    }

    @Test
    @DisplayName("Thiếu nonce → không neo được")
    void thieuNonce() {
      AuditLog e = base();
      e.setNonce(null);
      assertThrows(IllegalStateException.class, () -> AuditPayload.of(e));
    }

    @Test
    @DisplayName("Thiếu mắt xích hash → không neo được")
    void thieuHash() {
      AuditLog e = base();
      e.setHash(null);
      assertThrows(IllegalStateException.class, () -> AuditPayload.of(e));
    }

    @Test
    @DisplayName("prevHash sai độ dài bị từ chối, không lặng lẽ đệm thêm")
    void prevHashSaiDoDai() {
      assertThrows(IllegalArgumentException.class,
          () -> AuditHasher.preimage(new byte[16], Map.of("a", 1L)));
    }

    @Test
    @DisplayName("Sửa nội dung làm mắt xích tính lại KHÁC — nếu không thì phép kiểm là giả")
    void suaNoiDungThiDoiHash() {
      AuditLog e = base();
      byte[] truoc = AuditHasher.chainHash(e);

      e.setEntityId(999L);
      assertNotEquals(HEX.formatHex(truoc), HEX.formatHex(AuditHasher.chainHash(e)));

      AuditLog e2 = base();
      e2.setAfterJson(e2.getAfterJson().replace("ACTIVE", "REVOKED"));
      assertNotEquals(HEX.formatHex(truoc), HEX.formatHex(AuditHasher.chainHash(e2)));
    }
  }
}
