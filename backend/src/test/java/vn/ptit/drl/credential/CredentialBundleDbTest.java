package vn.ptit.drl.credential;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.ptit.drl.common.web.BusinessException;

/**
 * Bundle dựng ra ở phía Java phải KHỚP fixture do JS sinh — NỬA JAVA.
 * Nửa JS: {@code verifier/test/bundle.test.mjs}.
 *
 * <p>Chạy: {@code .\scripts\test-backend.ps1 CredentialBundleDbTest} (cần container MySQL ở
 * cổng 3310).
 *
 * <h2>Vì sao test này gieo dữ liệu thẳng bằng SQL thay vì gọi {@code CredentialService.issue}</h2>
 *
 * <p>{@code issue()} tự sinh nonce, tự bốc {@code statusListIndex}, tự lấy giờ hiện tại — nên
 * nó <b>không thể</b> tái tạo đúng credential trong fixture. Muốn so bundle từng trường thì
 * đầu vào phải cố định. Luồng {@code issue()} đã có test riêng ({@code CredentialIssueDbTest});
 * lớp này chỉ kiểm phần <b>đóng gói</b>.
 *
 * <p>Toàn bộ chạy trong giao dịch được cuộn lại nên không để lại rác, và không đụng tới lô đã
 * neo thật trên Amoy.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
    "drl.credential.issuer-private-key="
        + "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80",
    // Địa chỉ Amoy thật, chữ thường. Đặt tường minh chứ không đọc `.env`: fixture chốt đúng
    // ba giá trị này, và test không được đỏ chỉ vì máy nào đó có `.env` khác.
    "drl.anchor.chain-id=80002",
    "drl.anchor.anchor-registry-address=0x4ac296ad010233799ba3b91b8505269213503faf",
    "drl.anchor.issuer-registry-address=0xd323118fa310a730bc4202fadd8dfa7cea4c5637",
    "drl.anchor.status-list-address=0xc8538a8741ce428c4a26f3a06678b6ca10972106"
})
class CredentialBundleDbTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HexFormat HEX = HexFormat.of();
  private static final JsonNode DOC = load();

  private static JsonNode load() {
    try (InputStream in =
        CredentialBundleDbTest.class.getResourceAsStream("/bundle-fixture.json")) {
      if (in == null) {
        throw new IllegalStateException(
            "Không thấy bundle-fixture.json. Sinh lại bằng:"
                + " cd verifier && npm run gen-bundle-fixture");
      }
      return MAPPER.readTree(in);
    } catch (Exception e) {
      throw new IllegalStateException("Không đọc được fixture bundle", e);
    }
  }

  private static JsonNode mongDoi() {
    return DOC.get("bundle");
  }

  private static JsonNode gieo() {
    return DOC.get("duLieuGieo");
  }

  @Autowired CredentialBundleService bundleService;
  @Autowired JdbcTemplate jdbc;

  private long credentialId;

  // ------------------------------------------------------------------ gieo dữ liệu

  @BeforeEach
  void gieoDuLieu() {
    JsonNode payload = mongDoi().get("credential").get("payload");
    credentialId = payload.get("credentialId").longValue();
    long orgId = payload.get("issuerOrgId").longValue();

    // Tổ chức và sinh viên với id CỐ ĐỊNH — payload trong fixture chốt `issuerOrgId`, mà
    // CredentialPayload đọc nó qua `credential.getIssuerOrg().getId()`.
    jdbc.update("INSERT INTO organizations (id, name, type) VALUES (?,?,?)",
        orgId, "Doan Thanh nien — fixture", "DOAN");

    long studentId = 900001L;
    jdbc.update("INSERT INTO students (id, mssv, full_name) VALUES (?,?,?)",
        studentId, payload.get("studentCode").textValue() + "-FIXTURE",
        "Ten Khac Han De Chung Minh Viec Chup Anh");

    // Credential: mọi giá trị lấy từ fixture, kể cả id. MSSV và họ tên của `students` ở trên
    // CỐ TÌNH khác giá trị chụp ảnh — nếu CredentialPayload lỡ đọc qua khóa ngoại thì leaf
    // lệch và test đỏ ngay.
    jdbc.update("""
        INSERT INTO credentials
          (id, student_id, student_code, student_name, issuer_org_id, issuer_address,
           type, semester, activity_count, total_points, payload_json, issued_at, expires_at,
           status_list_index, signature, nonce, leaf_hash)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        credentialId,
        studentId,
        payload.get("studentCode").textValue(),
        payload.get("studentName").textValue(),
        orgId,
        payload.get("issuerAddress").textValue(),
        payload.get("type").textValue(),
        payload.get("claims").get("semester").textValue(),
        payload.get("claims").get("activityCount").intValue(),
        payload.get("claims").get("totalPoints").intValue(),
        gieo().get("loDayDu").get(0).get("payloadJcs").textValue(),
        Timestamp.from(Instant.parse(payload.get("issuedAt").textValue())),
        payload.get("expiresAt").isNull() ? null
            : Timestamp.from(Instant.parse(payload.get("expiresAt").textValue())),
        payload.get("statusListIndex").longValue(),
        bytes(mongDoi().get("credential").get("signature").textValue()),
        bytes(payload.get("nonce").textValue()),
        bytes(mongDoi().get("credential").get("leaf").textValue()));

    gieoLoNeo(true);
  }

  /** Lô neo + bốn lá. {@code onChain=false} để lô nằm lại với {@code tx_hash} NULL. */
  private void gieoLoNeo(boolean onChain) {
    jdbc.update("DELETE FROM anchor_leaves WHERE source_table = 'credentials'");
    jdbc.update("DELETE FROM anchor_batches WHERE domain = 'CRED'");

    JsonNode anchor = mongDoi().get("anchor");
    jdbc.update("""
        INSERT INTO anchor_batches
          (domain, batch_id, merkle_root, leaf_count, tx_hash, block_number, anchored_at)
        VALUES (?,?,?,?,?,?,?)
        """,
        "CRED",
        anchor.get("batchId").longValue(),
        bytes(anchor.get("merkleRoot").textValue()),
        gieo().get("loDayDu").size(),
        onChain ? anchor.get("txHash").textValue() : null,
        onChain ? anchor.get("blockNumber").longValue() : null,
        onChain ? Timestamp.from(Instant.parse(anchor.get("anchoredAt").textValue())) : null);

    Long rowId = jdbc.queryForObject(
        "SELECT id FROM anchor_batches WHERE domain = 'CRED' AND batch_id = ?",
        Long.class, anchor.get("batchId").longValue());

    // Chèn cả bốn lá, không chỉ lá của chủ thể. Ba lá kia là credential của sinh viên khác —
    // đúng như lô thật, và là chỗ PROJECT.md §2.3 nói tới khi cảnh báo proof để lộ hash bản
    // ghi người khác.
    JsonNode lo = gieo().get("loDayDu");
    for (int i = 0; i < lo.size(); i++) {
      StringBuilder proofJson = new StringBuilder("[");
      JsonNode proof = lo.get(i).get("proof");
      for (int k = 0; k < proof.size(); k++) {
        if (k > 0) proofJson.append(',');
        proofJson.append('"').append(proof.get(k).textValue()).append('"');
      }
      proofJson.append(']');

      jdbc.update("""
          INSERT INTO anchor_leaves (batch_id, leaf_hash, proof_json, source_table, source_id)
          VALUES (?,?,?,?,?)
          """,
          rowId,
          bytes(lo.get(i).get("leaf").textValue()),
          proofJson.toString(),
          "credentials",
          // Chỉ lá đầu tiên trỏ về credential đã gieo; ba lá kia mang source_id giả để không
          // vi phạm khóa ngoại nào (anchor_leaves.source_id không có FK, theo thiết kế).
          i == 0 ? credentialId : 990000L + i);
    }
  }

  // ------------------------------------------------------------------ hợp đồng

  @Test
  @DisplayName("Bundle dựng ra khớp fixture do JS sinh — từng trường một")
  void bundleKhopFixture() throws Exception {
    JsonNode thucTe = MAPPER.valueToTree(bundleService.build(credentialId));
    JsonNode mongDoi = mongDoi();

    // `exportedAt` là thời điểm xuất tệp nên nó KHÔNG bao giờ khớp fixture — đó là đúng.
    // Kiểm riêng nó có mặt và đúng định dạng, rồi bỏ ra khỏi phép so cây.
    assertTrue(thucTe.get("exportedAt").textValue()
            .matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$"),
        "exportedAt phải là ISO-8601 UTC độ chính xác giây: " + thucTe.get("exportedAt"));

    var a = (com.fasterxml.jackson.databind.node.ObjectNode) thucTe.deepCopy();
    var b = (com.fasterxml.jackson.databind.node.ObjectNode) mongDoi.deepCopy();
    a.remove("exportedAt");
    b.remove("exportedAt");

    assertEquals(chuanHoa(b), chuanHoa(a),
        "Bundle phía Java khác fixture. Nếu đây là thay đổi CÓ CHỦ Ý thì sinh lại fixture"
            + " (cd verifier && npm run gen-bundle-fixture) VÀ chạy lại test cả hai phía.");
  }

  /**
   * Chuỗi so sánh được giữa hai cây JSON, bất kể kiểu nút số.
   *
   * <p>Cần thiết vì {@code JsonNode.equals} so cả <b>kiểu nút</b>: Jackson đọc
   * {@code 2026080601} từ tệp fixture thành {@code IntNode} (nó vừa {@code int}), còn
   * {@code AnchorPart.batchId} khai {@code long} nên tuần tự hóa ra {@code LongNode}. Hai nút
   * đó <b>không bằng nhau</b> dù in ra giống hệt — thông báo lỗi vì thế trông như hai chuỗi y
   * chang nhau, mất cả buổi để hiểu.
   *
   * <p>Đây thuần túy là chuyện của bộ so sánh trong test, <b>không</b> phải chuyện
   * canonicalization: bundle không đi vào phép bam nào. Chỗ mà kiểu số thật sự quan trọng là
   * {@code Jcs}, và ở đó nó đã được chốt riêng bằng bộ vector.
   */
  private static String chuanHoa(JsonNode n) {
    StringBuilder out = new StringBuilder();
    viet(n, out);
    return out.toString();
  }

  private static void viet(JsonNode n, StringBuilder out) {
    if (n.isObject()) {
      out.append('{');
      var keys = new java.util.ArrayList<String>();
      n.fieldNames().forEachRemaining(keys::add);
      java.util.Collections.sort(keys);
      for (int i = 0; i < keys.size(); i++) {
        if (i > 0) out.append(',');
        out.append('"').append(keys.get(i)).append("\":");
        viet(n.get(keys.get(i)), out);
      }
      out.append('}');
    } else if (n.isArray()) {
      out.append('[');
      for (int i = 0; i < n.size(); i++) {
        if (i > 0) out.append(',');
        viet(n.get(i), out);
      }
      out.append(']');
    } else if (n.isNumber()) {
      // Chỗ duy nhất khác `toString()` mặc định: in số qua asText() nên IntNode và LongNode
      // cùng một giá trị cho ra cùng một chuỗi.
      out.append(n.asText());
    } else {
      out.append(n);
    }
  }

  @Test
  @DisplayName("payload nhúng nguyên văn payload_json, không phải bản Jackson dựng lại")
  void payloadNhungNguyenVan() throws Exception {
    String json = MAPPER.writeValueAsString(bundleService.build(credentialId));
    String daKy = gieo().get("loDayDu").get(0).get("payloadJcs").textValue();

    assertTrue(json.contains(daKy),
        "Chuỗi JCS đã ký không xuất hiện nguyên văn trong bundle. @JsonRawValue trên"
            + " CredentialPart.payload có còn không? Xem javadoc CredentialBundleService.");
  }

  @Test
  @DisplayName("Địa chỉ contract ghi ra CHỮ THƯỜNG")
  void diaChiChuThuong() {
    var chain = bundleService.build(credentialId).chain();
    for (String a : new String[] {
        chain.anchorRegistry(), chain.issuerRegistry(), chain.statusList()}) {
      assertNotNull(a);
      assertTrue(a.matches("^0x[0-9a-f]{40}$"), a);
    }
  }

  @Test
  @DisplayName("proof đọc ra đúng số sibling — cây 4 lá cho proof dài 2")
  void proofDungDoDai() {
    assertEquals(2, bundleService.build(credentialId).anchor().proof().size());
  }

  // ------------------------------------------------------------------ từ chối

  @Nested
  @DisplayName("Phải từ chối xuất, kèm lý do nói rõ nguyên nhân")
  class TuChoi {

    @Test
    @DisplayName("Credential chưa neo")
    void chuaNeo() {
      jdbc.update("DELETE FROM anchor_leaves WHERE source_table = 'credentials'");

      BusinessException e =
          assertThrows(BusinessException.class, () -> bundleService.build(credentialId));
      assertTrue(e.getMessage().contains("chưa được neo"), e.getMessage());
    }

    @Test
    @DisplayName("Lô đã dựng cây nhưng CHƯA lên chuỗi — bundle sẽ không verify được")
    void loChuaLenChuoi() {
      gieoLoNeo(false);

      BusinessException e =
          assertThrows(BusinessException.class, () -> bundleService.build(credentialId));
      assertTrue(e.getMessage().contains("CHƯA lên"), e.getMessage());
    }

    @Test
    @DisplayName("Lá trong anchor_leaves khác lá đã ký")
    void laKhacLaDaKy() {
      jdbc.update(
          "UPDATE anchor_leaves SET leaf_hash = ? WHERE source_table = 'credentials'"
              + " AND source_id = ?",
          new byte[32], credentialId);

      IllegalStateException e =
          assertThrows(IllegalStateException.class, () -> bundleService.build(credentialId));
      assertTrue(e.getMessage().contains("khác lá đã ký"), e.getMessage());
    }

    @Test
    @DisplayName("payload_json đã trôi khỏi các cột — bắt TRƯỚC khi xuất")
    void payloadDaTroi() {
      jdbc.update("UPDATE credentials SET student_name = ? WHERE id = ?",
          "Ten Bi Sua Truc Tiep Bang SQL", credentialId);

      IllegalStateException e =
          assertThrows(IllegalStateException.class, () -> bundleService.build(credentialId));
      assertTrue(e.getMessage().contains("KHÁC payload_json"), e.getMessage());
    }
  }

  private static byte[] bytes(String hex) {
    return HEX.parseHex(hex.startsWith("0x") ? hex.substring(2) : hex);
  }
}
