package vn.ptit.drl.audit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Chuỗi băm nhật ký trên CSDL thật.
 *
 * <p>Chạy: {@code .\scripts\test-backend.ps1 AuditServiceDbTest}
 *
 * <p>Vector ({@code AuditChainVectorTest}) chốt <b>công thức</b>. Lớp này chốt <b>đường đi
 * thật</b>: ghi nối tiếp, lưu xuống MySQL, đọc lại, và phát hiện được khi ai đó sửa thẳng
 * bằng SQL — đúng mô hình đe dọa mà luận điểm 1 nói tới (quản trị viên có quyền ghi CSDL).
 */
@SpringBootTest
@Transactional
class AuditServiceDbTest {

  @Autowired AuditService service;
  @Autowired AuditLogRepository repository;
  @Autowired JdbcTemplate jdbc;

  @PersistenceContext EntityManager entityManager;

  /**
   * Một {@code users.id} có thật, hoặc {@code null} nếu CSDL chưa có người dùng nào.
   *
   * <p>{@code audit_logs.actor_id} có khóa ngoại tới {@code users}, nên không bịa số được.
   * Đọc lúc chạy thay vì gieo thêm một người dùng: lớp này kiểm chuỗi băm, không kiểm việc
   * tạo tài khoản, và mỗi bản ghi thừa lại là một mắt xích thừa trong nhật ký.
   */
  private Long actorCoThat() {
    List<Long> ids = jdbc.queryForList("SELECT id FROM users LIMIT 1", Long.class);
    return ids.isEmpty() ? null : ids.get(0);
  }

  private void ghiBaBanGhi() {
    Long actor = actorCoThat();
    service.record("DEVICE_APPROVE", "student_devices", 12L, actor,
        "{\"status\":\"PENDING\"}", "{\"status\":\"ACTIVE\"}");
    service.record("ATTENDANCE_MANUAL", "attendances", 5L, actor,
        null, "{\"method\":\"MANUAL\",\"verified\":false}");
    // actorId null — hệ thống tự làm, đây là trường hợp thật của job neo và luồng cấp
    // credential tự động.
    service.record("CREDENTIAL_ISSUE", "credentials", 81L, null,
        null, "{\"studentCode\":\"B21DCCN002\"}");
  }

  // ------------------------------------------------------------------ ghi

  @Test
  @DisplayName("Bản ghi đầu tiên có prevHash NULL, các bản sau nối vào bản trước")
  void chuoiNoiTiep() {
    long truocDo = repository.count();
    ghiBaBanGhi();
    repository.flush();
    entityManager.clear();

    List<AuditLog> all = repository.findAllByOrderByIdAsc();
    assertEquals(truocDo + 3, all.size());

    // Chỉ kiểm ba bản ghi vừa ghi; nhật ký có thể đã có sẵn dữ liệu từ test khác.
    List<AuditLog> ba = all.subList(all.size() - 3, all.size());
    for (int i = 1; i < ba.size(); i++) {
      assertArrayEquals(ba.get(i - 1).getHash(), ba.get(i).getPrevHash(),
          "Bản ghi #" + ba.get(i).getId() + " không nối vào bản ghi liền trước");
    }
  }

  @Test
  @DisplayName("hash được ghi thật, không còn là chỗ giữ chỗ toàn 0x00")
  void hashDuocGhiThat() {
    AuditLog e = service.record("TEST", "nothing", 1L, null, null, "{}");
    repository.flush();
    entityManager.clear();

    AuditLog doclai = repository.findById(e.getId()).orElseThrow();

    int khac0 = 0;
    for (byte b : doclai.getHash()) {
      if (b != 0) khac0++;
    }
    assertTrue(khac0 > 0, "hash vẫn là chỗ giữ chỗ toàn 0x00");

    // Và nó phải khớp công thức khi tính lại từ dữ liệu ĐỌC TỪ CSDL — chỗ này bắt được
    // chuyện kiểu cột làm trôi byte (bẫy JSON của MySQL, xem V6/V7).
    assertArrayEquals(AuditHasher.chainHash(doclai), doclai.getHash());
  }

  @Test
  @DisplayName("before_json/after_json giữ nguyên TỪNG BYTE qua vòng lưu-đọc")
  void jsonGiuNguyenByte() {
    // LONGTEXT chứ không phải JSON (V7). Với kiểu JSON, MySQL sắp xếp lại khóa theo độ dài
    // và chèn dấu cách — beforeHash tính lại sẽ khác và ĐỨT CẢ CHUỖI.
    String json = "{ \"z\": 1,  \"a\": \"Nguyễn Ngọc Hoàng\",  \"mm\": null }";

    AuditLog e = service.record("TEST", "nothing", 2L, null, json, json);
    repository.flush();
    entityManager.clear();

    AuditLog doclai = repository.findById(e.getId()).orElseThrow();
    assertEquals(json, doclai.getBeforeJson(),
        "before_json đã bị chuẩn hoá lại. Kiểu cột có còn là LONGTEXT không? Xem V7.");
    assertEquals(json, doclai.getAfterJson());
    assertArrayEquals(AuditHasher.chainHash(doclai), doclai.getHash());
  }

  @Test
  @DisplayName("Không ghi đè được mắt xích lần thứ hai")
  void khongGhiDeDuoc() {
    AuditLog e = service.record("TEST", "nothing", 3L, null, null, "{}");
    repository.flush();

    assertEquals(0, repository.updateHash(e.getId(), new byte[32], new byte[32]),
        "Câu lệnh ghi mắt xích phải chỉ chạy được đúng một lần cho mỗi bản ghi");
  }

  // ------------------------------------------------------------------ kiểm chuỗi

  @Test
  @DisplayName("Chuỗi vừa ghi là nguyên vẹn")
  void chuoiNguyenVen() {
    ghiBaBanGhi();
    repository.flush();
    entityManager.clear();

    var kq = service.verifyChain();
    assertTrue(kq.nguyenVen(), String.join("\n", kq.loi()));
    assertTrue(kq.soBanGhi() >= 3);
  }

  @Nested
  @DisplayName("Sửa thẳng bằng SQL — đúng mô hình đe dọa của luận điểm 1")
  class SuaBangSql {

    @Test
    @DisplayName("Sửa NỘI DUNG một bản ghi quá khứ → phát hiện được")
    void suaNoiDung() {
      ghiBaBanGhi();
      repository.flush();

      List<AuditLog> all = repository.findAllByOrderByIdAsc();
      AuditLog giua = all.get(all.size() - 2);

      // Quản trị viên có toàn quyền CSDL sửa thẳng, bỏ qua mọi tầng ứng dụng.
      jdbc.update("UPDATE audit_logs SET entity_id = 999 WHERE id = ?", giua.getId());
      entityManager.clear();

      var kq = service.verifyChain();
      assertFalse(kq.nguyenVen(), "KHÔNG phát hiện ra việc sửa nội dung");
      assertTrue(kq.loi().stream().anyMatch(l -> l.contains("NỘI DUNG BỊ SỬA")),
          String.join("\n", kq.loi()));
    }

    @Test
    @DisplayName("XÓA một bản ghi giữa chuỗi → phát hiện được")
    void xoaBanGhi() {
      ghiBaBanGhi();
      repository.flush();

      List<AuditLog> all = repository.findAllByOrderByIdAsc();
      AuditLog giua = all.get(all.size() - 2);

      jdbc.update("DELETE FROM audit_logs WHERE id = ?", giua.getId());
      entityManager.clear();

      var kq = service.verifyChain();
      assertFalse(kq.nguyenVen(), "KHÔNG phát hiện ra việc xóa bản ghi");
      assertTrue(kq.loi().stream().anyMatch(l -> l.contains("ĐỨT XÍCH")),
          String.join("\n", kq.loi()));
    }

    @Test
    @DisplayName("Sửa nội dung VÀ tính lại hash của chính nó → vẫn phát hiện được qua prevHash")
    void suaVaTinhLaiHash() {
      ghiBaBanGhi();
      repository.flush();

      List<AuditLog> all = repository.findAllByOrderByIdAsc();
      AuditLog giua = all.get(all.size() - 2);

      // Kẻ tấn công thông minh hơn: sửa xong tính lại mắt xích của chính bản ghi đó. Bắt
      // được vì bản ghi SAU vẫn mang prevHash cũ.
      jdbc.update("UPDATE audit_logs SET entity_id = 999 WHERE id = ?", giua.getId());
      entityManager.clear();
      AuditLog daSua = repository.findById(giua.getId()).orElseThrow();
      byte[] hashMoi = AuditHasher.chainHash(daSua);
      jdbc.update("UPDATE audit_logs SET hash = ? WHERE id = ?", hashMoi, giua.getId());
      entityManager.clear();

      var kq = service.verifyChain();
      assertFalse(kq.nguyenVen());
      assertTrue(kq.loi().stream().anyMatch(l -> l.contains("ĐỨT XÍCH")),
          String.join("\n", kq.loi()));
    }

    @Test
    @DisplayName("⚠️ Tính lại TOÀN BỘ chuỗi → KHÔNG phát hiện được. Đây là lý do phải NEO")
    void tinhLaiCaChuoiThiKhongBat() {
      ghiBaBanGhi();
      repository.flush();
      entityManager.clear();

      List<AuditLog> all = repository.findAllByOrderByIdAsc();
      AuditLog giua = all.get(all.size() - 2);

      jdbc.update("UPDATE audit_logs SET entity_id = 999 WHERE id = ?", giua.getId());
      entityManager.clear();

      // Tính lại mắt xích cho bản ghi bị sửa VÀ mọi bản ghi sau nó.
      List<AuditLog> lai = repository.findAllByOrderByIdAsc();
      byte[] prev = null;
      for (AuditLog e : lai) {
        e.setPrevHash(prev);
        byte[] h = AuditHasher.chainHash(e);
        jdbc.update("UPDATE audit_logs SET prev_hash = ?, hash = ? WHERE id = ?",
            prev, h, e.getId());
        prev = h;
      }
      entityManager.clear();

      var kq = service.verifyChain();

      // Chuỗi lại "nguyên vẹn" — và đó là SỰ THẬT phải nói ra trong báo cáo, không giấu.
      assertTrue(kq.nguyenVen(),
          "Nếu phép kiểm này BẮT được thì hoặc chuỗi tính lại sai, hoặc verifyChain đang"
              + " dựa vào thứ gì đó ngoài chính chuỗi — cần xem lại.");

      // Cái duy nhất chặn được kịch bản này là leaf_hash đã neo lên chuỗi công khai: root
      // trên Amoy không tính lại được. Bản ghi nào đã neo thì leaf_hash của nó cố định, và
      // leaf tính lại từ nội dung mới sẽ KHÁC.
      AuditLog daSua = repository.findById(giua.getId()).orElseThrow();
      assertNull(daSua.getLeafHash(),
          "Bản ghi trong test này chưa neo — nếu đã neo thì đây là chỗ so leaf để bắt.");
    }
  }

  // ------------------------------------------------------------------ neo

  @Test
  @DisplayName("Bản ghi mới nằm trong danh sách chờ neo của miền AUDIT")
  void namTrongDanhSachChoNeo() {
    AuditLog e = service.record("TEST", "nothing", 4L, null, null, "{}");
    repository.flush();

    List<Long> choNeo = repository.findPendingAnchor(PageRequest.of(0, 500))
        .stream().map(AuditLog::getId).toList();

    assertTrue(choNeo.contains(e.getId()),
        "Bản ghi mới không được job neo nhặt lên — miền AUDIT sẽ không bao giờ có lô nào,"
            + " và luận điểm 1 mất chỗ dựa cuối cùng.");
  }

  @Test
  @DisplayName("AnchorSource từ chối neo khi chuỗi đang hỏng")
  void khongNeoChuoiHong() {
    ghiBaBanGhi();
    repository.flush();

    List<AuditLog> all = repository.findAllByOrderByIdAsc();
    jdbc.update("UPDATE audit_logs SET entity_id = 999 WHERE id = ?",
        all.get(all.size() - 1).getId());
    entityManager.clear();

    AuditAnchorSource source = new AuditAnchorSource(repository);

    IllegalStateException ex = assertThrows(IllegalStateException.class,
        () -> source.pending(500));
    assertTrue(ex.getMessage().contains("KHÔNG neo"), ex.getMessage());
  }

  @Test
  @DisplayName("Payload neo KHÔNG chứa nội dung before/after — chỉ chứa hash của chúng")
  void payloadKhongLoNoiDung() {
    String biMat = "{\"hoTen\":\"Nguyễn Ngọc Hoàng\",\"diem\":95}";
    AuditLog e = service.record("TEST", "nothing", 5L, null, biMat, biMat);
    repository.flush();
    entityManager.clear();

    var payload = AuditPayload.of(repository.findById(e.getId()).orElseThrow());
    String json = vn.ptit.drl.anchor.Jcs.canonicalize(payload);

    assertFalse(json.contains("Hoàng"), "Nội dung cá nhân lọt vào payload được neo");
    assertFalse(json.contains("95"), "Nội dung cá nhân lọt vào payload được neo");
    assertNotNull(payload.get("beforeHash"));
  }
}
