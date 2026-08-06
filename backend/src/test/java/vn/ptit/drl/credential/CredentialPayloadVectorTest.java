package vn.ptit.drl.credential;

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
import vn.ptit.drl.identity.Student;
import vn.ptit.drl.org.Organization;

/**
 * {@link CredentialPayload} dựng ra ĐÚNG payload trong bộ vector — NỬA JAVA.
 * Nửa JS: {@code verifier/test/cred.test.mjs}.
 *
 * <p>Chạy: {@code .\scripts\test-backend.ps1 CredentialPayloadVectorTest}
 *
 * <p>Khác {@code CanonicalVectorTest} ở một điểm quan trọng: lớp kia đưa thẳng cây
 * {@code Map} lấy từ file vector vào {@code LeafHasher}, tức là nó kiểm <b>công thức</b>.
 * Lớp này đi từ <b>entity</b> {@link Credential} qua {@link CredentialPayload#of} rồi mới tới
 * leaf, tức là nó kiểm <b>đường đi thật của luồng cấp credential</b>. Một trường đọc nhầm
 * cột, một chỗ quên cắt mili giây, một địa chỉ viết dạng checksum — chỉ lớp này bắt được.
 */
class CredentialPayloadVectorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HexFormat HEX = HexFormat.of();
  private static final JsonNode DOC = load();

  private static JsonNode load() {
    try (InputStream in =
        CredentialPayloadVectorTest.class.getResourceAsStream("/canonical-vectors.json")) {
      if (in == null) {
        throw new IllegalStateException(
            "Không thấy canonical-vectors.json. Sinh lại: cd verifier && npm run gen-vectors");
      }
      return MAPPER.readTree(in);
    } catch (Exception e) {
      throw new IllegalStateException("Không đọc được bộ test vector", e);
    }
  }

  /** Chỉ các vector của lược đồ CRED thật, không lấy {@code cred-null-va-mang} minh hoạ. */
  private static List<JsonNode> credVectors() {
    List<JsonNode> out = new ArrayList<>();
    DOC.get("vectors").forEach(v -> {
      if (v.get("id").textValue().startsWith("cred-payload")) {
        out.add(v);
      }
    });
    return out;
  }

  /**
   * Dựng {@link Credential} khớp payload của một vector.
   *
   * <p>Cố ý cho {@link Student} và {@link Organization} mang giá trị <b>KHÁC</b> giá trị đã
   * chụp ảnh: nếu {@link CredentialPayload#of} lỡ đọc qua khóa ngoại thay vì đọc cột chụp
   * ảnh, leaf sẽ lệch và mọi test dưới đây đỏ. Đây là cách rẻ nhất để chốt quy tắc chụp ảnh
   * của migration V4.
   */
  private static Credential fromVector(JsonNode v) {
    JsonNode p = v.get("payload");
    JsonNode claims = p.get("claims");

    Student student = new Student();
    student.setId(999L);
    student.setMssv("MSSV-DA-DOI-SAU-KHI-CAP");
    student.setFullName("Tên Đã Đổi Sau Khi Cấp");

    Organization org = new Organization();
    org.setId(p.get("issuerOrgId").longValue());
    org.setIssuerAddress("0x0000000000000000000000000000000000000000");

    JsonNode expires = p.get("expiresAt");

    return Credential.builder()
        .id(p.get("credentialId").longValue())
        .student(student)
        .studentCode(p.get("studentCode").textValue())
        .studentName(p.get("studentName").textValue())
        .issuerOrg(org)
        .issuerAddress(p.get("issuerAddress").textValue())
        .type(CredentialType.valueOf(p.get("type").textValue()))
        .semester(claims.get("semester").textValue())
        .activityCount(claims.get("activityCount").intValue())
        .totalPoints(claims.get("totalPoints").intValue())
        .issuedAt(Instant.parse(p.get("issuedAt").textValue()))
        .expiresAt(expires.isNull() ? null : Instant.parse(expires.textValue()))
        .statusListIndex(p.get("statusListIndex").longValue())
        .nonce(HEX.parseHex(p.get("nonce").textValue().substring(2)))
        .build();
  }

  // ------------------------------------------------------------------ hợp đồng

  @TestFactory
  @DisplayName("Entity → payload → leaf khớp vector do JS sinh")
  List<DynamicTest> leafKhopVector() {
    List<DynamicTest> tests = new ArrayList<>();
    for (JsonNode v : credVectors()) {
      tests.add(dynamicTest(v.get("id").textValue(), () -> {
        Map<String, Object> payload = CredentialPayload.of(fromVector(v));

        assertEquals(v.get("expected").get("jcs").textValue(), Jcs.canonicalize(payload),
            "Chuỗi JCS lệch — " + v.get("why").textValue());
        assertEquals(v.get("expected").get("leaf").textValue(),
            LeafHasher.leafHex(AnchorDomain.CRED, payload));
      }));
    }
    return tests;
  }

  @Test
  @DisplayName("Có ít nhất hai vector CRED thật")
  void duVector() {
    assertTrue(credVectors().size() >= 2,
        "Thiếu vector cred-payload-*. Sinh lại: cd verifier && npm run gen-vectors");
  }

  @Test
  @DisplayName("Đúng 11 trường ở cấp ngoài, khớp CRED_FIELDS phía JS")
  void muoiMotTruong() {
    Map<String, Object> p = CredentialPayload.of(fromVector(credVectors().get(0)));
    assertEquals(11, p.size(), "Tập trường: " + p.keySet());
    assertTrue(p.containsKey("claims"));
    assertTrue(p.get("claims") instanceof Map<?, ?> m && m.size() == 3);
  }

  // ------------------------------------------------------------------ chụp ảnh

  @Nested
  @DisplayName("Chụp ảnh — đổi dữ liệu gốc KHÔNG được đụng tới credential đã cấp")
  class ChupAnh {

    @Test
    @DisplayName("Đổi tên và MSSV của sinh viên không làm đổi leaf")
    void doiSinhVienKhongDoiLeaf() {
      JsonNode v = credVectors().get(0);
      Credential c = fromVector(v);

      String truoc = LeafHasher.leafHex(AnchorDomain.CRED, CredentialPayload.of(c));

      c.getStudent().setMssv("B99XXXXX999");
      c.getStudent().setFullName("Một Cái Tên Hoàn Toàn Khác");
      c.getIssuerOrg().setIssuerAddress("0x1111111111111111111111111111111111111111");

      assertEquals(truoc, LeafHasher.leafHex(AnchorDomain.CRED, CredentialPayload.of(c)),
          "Payload đang đọc qua khóa ngoại thay vì cột chụp ảnh. Đổi tên một sinh viên sẽ"
              + " làm hỏng vĩnh viễn mọi proof credential của họ — xem migration V4.");
      assertEquals(v.get("expected").get("leaf").textValue(), truoc);
    }

    @Test
    @DisplayName("Đổi cột đã chụp ảnh THÌ đổi leaf — nếu không thì phép kiểm trên là giả")
    void doiCotChupAnhThiDoiLeaf() {
      JsonNode v = credVectors().get(0);
      Credential c = fromVector(v);
      c.setStudentName("Tên Khác");

      assertNotEquals(v.get("expected").get("leaf").textValue(),
          LeafHasher.leafHex(AnchorDomain.CRED, CredentialPayload.of(c)));
    }
  }

  // ------------------------------------------------------------------ từ chối

  @Nested
  @DisplayName("Phải vỡ ồn ào")
  class TuChoi {

    private Credential base() {
      return fromVector(credVectors().get(0));
    }

    @Test
    @DisplayName("Thiếu nonce")
    void thieuNonce() {
      Credential c = base();
      c.setNonce(null);
      assertThrows(IllegalStateException.class, () -> CredentialPayload.of(c));
    }

    @Test
    @DisplayName("Nonce sai độ dài")
    void nonceSaiDoDai() {
      Credential c = base();
      c.setNonce(new byte[8]);
      assertThrows(IllegalStateException.class, () -> CredentialPayload.of(c));
    }

    @Test
    @DisplayName("issuerAddress dạng checksum EIP-55 bị từ chối")
    void diaChiChecksum() {
      Credential c = base();
      c.setIssuerAddress("0xf32728c5c2D0575ea406Ad37e2467916c89F529F");

      IllegalStateException e =
          assertThrows(IllegalStateException.class, () -> CredentialPayload.of(c));
      assertTrue(e.getMessage().contains("CHỮ THƯỜNG"), e.getMessage());
    }

    @Test
    @DisplayName("Thiếu statusListIndex — không thu hồi được thì không được cấp")
    void thieuStatusIndex() {
      Credential c = base();
      c.setStatusListIndex(null);
      assertThrows(IllegalStateException.class, () -> CredentialPayload.of(c));
    }
  }

  // ------------------------------------------------------------------ thời gian

  @Nested
  @DisplayName("Thời gian — ISO-8601 UTC độ chính xác GIÂY")
  class ThoiGian {

    @Test
    @DisplayName("Mili giây bị cắt, không làm lệch hash giữa bản ghi tròn và bản ghi lẻ")
    void catMiliGiay() {
      Credential c = fromVector(credVectors().get(0));
      String leafTron = LeafHasher.leafHex(AnchorDomain.CRED, CredentialPayload.of(c));

      c.setIssuedAt(c.getIssuedAt().plusMillis(742));

      assertEquals(leafTron, LeafHasher.leafHex(AnchorDomain.CRED, CredentialPayload.of(c)),
          "Phần mili giây đang lọt vào payload. Cột là DATETIME(3) nên bản ghi tròn giây và"
              + " bản ghi có phần lẻ sẽ ra hai định dạng khác nhau trong cùng một lô.");
    }

    @Test
    @DisplayName("expiresAt null giữ nguyên literal null, không bị lược bỏ")
    void expiresNull() {
      Credential c = fromVector(credVectors().get(0));
      c.setExpiresAt(null);

      Map<String, Object> p = CredentialPayload.of(c);
      assertTrue(p.containsKey("expiresAt"), "Trường vắng mặt cho ra hash KHÁC trường null");
      assertNull(p.get("expiresAt"));
      assertTrue(Jcs.canonicalize(p).contains("\"expiresAt\":null"));
    }

    @Test
    @DisplayName("Định dạng đúng ISO giây, hậu tố Z")
    void dinhDangIso() {
      Map<String, Object> p = CredentialPayload.of(fromVector(credVectors().get(0)));
      assertTrue(((String) p.get("issuedAt")).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$"),
          String.valueOf(p.get("issuedAt")));
    }
  }
}
