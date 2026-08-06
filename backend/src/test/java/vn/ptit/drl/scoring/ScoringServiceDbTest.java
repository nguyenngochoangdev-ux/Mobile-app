package vn.ptit.drl.scoring;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import vn.ptit.drl.anchor.AnchorDomain;
import vn.ptit.drl.anchor.LeafHasher;
import vn.ptit.drl.attendance.Attendance;
import vn.ptit.drl.attendance.AttendancePayload;
import vn.ptit.drl.attendance.AttendanceRepository;
import vn.ptit.drl.common.web.BusinessException;

/**
 * Chấm điểm trên CSDL thật — <b>mốc của tuần 5</b>.
 *
 * <p>Chạy: {@code .\scripts\test-backend.ps1 ScoringServiceDbTest}
 *
 * <p>Vector ({@code ScoringVectorTest}) chốt công thức. Lớp này chốt <b>đường đi thật</b>:
 * nạp bộ quy tắc từ tệp, chấm toàn khóa, và {@code evidence_hash} <b>tái tính lại được</b>
 * từ chính các bản ghi điểm danh — điều mà cả mốc tuần 5 lẫn đóng góp học thuật đòi hỏi.
 */
@SpringBootTest
@Transactional
class ScoringServiceDbTest {

  private static final String RULESET = "2026-1.v1";
  private static final String SEMESTER = "2026-1";

  @Autowired ScoringService service;
  @Autowired RuleEvaluator evaluator;
  @Autowired RulesetRepository rulesets;
  @Autowired ScoreRepository scores;
  @Autowired AttendanceRepository attendances;
  @Autowired JdbcTemplate jdbc;

  @PersistenceContext EntityManager entityManager;

  private Long actorCoThat() {
    List<Long> ids = jdbc.queryForList("SELECT id FROM users LIMIT 1", Long.class);
    return ids.isEmpty() ? null : ids.get(0);
  }

  // ------------------------------------------------------------------ bộ quy tắc

  @Test
  @DisplayName("Bộ quy tắc THẬT trong resources không có lỗi nào")
  void boQuyTacThatLanh() {
    Ruleset r = service.napBoQuyTac(RULESET);
    RulesetDoc doc = service.doc(r);

    List<String> loi = evaluator.kiemBoQuyTac(doc);
    assertTrue(loi.isEmpty(), "Bộ quy tắc thật có lỗi:\n  " + String.join("\n  ", loi));
  }

  @Test
  @DisplayName("Bộ quy tắc khai đúng phần nào chấm từ dữ liệu, phần nào mặc định")
  void khaiDungNguonDuLieu() {
    RulesetDoc doc = service.doc(service.napBoQuyTac(RULESET));

    // Con số này đi thẳng vào báo cáo. Chốt bằng test để nó không lệch khỏi bộ quy tắc thật
    // khi ai đó sửa tệp mà quên sửa báo cáo.
    assertEquals(50, doc.diemTuDuLieu(),
        "Điểm chấm được từ dữ liệu điểm danh, trên thang 100");
    assertEquals(40, doc.diemMacDinh(),
        "Điểm nền cố định, không phụ thuộc sinh viên làm gì");

    assertEquals(RulesetDoc.Nguon.MAC_DINH, doc.tieuChi("C2").nguon());
    assertEquals(RulesetDoc.Nguon.MAC_DINH, doc.tieuChi("C5").nguon());
    assertEquals(RulesetDoc.Nguon.HON_HOP, doc.tieuChi("C4").nguon());
    assertEquals(RulesetDoc.Nguon.TU_DONG, doc.tieuChi("C1").nguon());
    assertEquals(RulesetDoc.Nguon.TU_DONG, doc.tieuChi("C3").nguon());

    assertFalse(doc.hanChe().isEmpty(), "Bộ quy tắc phải tự khai hạn chế của nó");
  }

  @Test
  @DisplayName("Nạp lại bộ quy tắc KHÔNG tạo bản thứ hai")
  void napLaiKhongTaoBanMoi() {
    Long id = service.napBoQuyTac(RULESET).getId();
    assertEquals(id, service.napBoQuyTac(RULESET).getId());
  }

  @Test
  @DisplayName("TỆP quy tắc đổi sau khi đã công bố → nạp lại bị TỪ CHỐI")
  void suaTepQuyTacDaCongBo() {
    Ruleset r = service.napBoQuyTac(RULESET);

    // Kịch bản thật: ai đó sửa tệp quy chế trong resources rồi deploy lại. Không sửa được
    // tệp trong lúc test, nên mô phỏng bằng cách đổi `ruleset_hash` đã lưu — hiệu quả giống
    // hệt: hash của tệp hiện tại không còn khớp bản đã công bố.
    //
    // Bản đầu của test này sửa `json_body` và KHÔNG BAO GIỜ đỏ được: napBoQuyTac so hash của
    // TỆP với `ruleset_hash` ĐÃ LƯU, mà `json_body` không tham gia phép so đó.
    jdbc.update("UPDATE rulesets SET ruleset_hash = ? WHERE id = ?", new byte[32], r.getId());
    entityManager.clear();

    BusinessException e =
        assertThrows(BusinessException.class, () -> service.napBoQuyTac(RULESET));
    assertTrue(e.getMessage().contains("KHÁC bản đã"), e.getMessage());
  }

  @Test
  @DisplayName("Sửa json_body THẲNG TRONG CSDL → AnchorSource từ chối neo")
  void suaJsonBodyTrongCsdl() {
    // Đường tấn công khác: không sửa tệp mà sửa thẳng bảng, để `json_body` và `ruleset_hash`
    // lệch nhau. napBoQuyTac không thấy (nó chỉ so tệp với hash đã lưu), nên chốt chặn phải
    // nằm ở chỗ cuối cùng trước khi đóng dấu vĩnh viễn — lúc neo.
    Ruleset r = service.napBoQuyTac(RULESET);
    jdbc.update("UPDATE rulesets SET json_body = ? WHERE id = ?",
        r.getJsonBody() + "\n", r.getId());
    entityManager.clear();

    var source = new RulesetAnchorSource(rulesets);
    IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> source.pending(10));
    assertTrue(e.getMessage().contains("không khớp ruleset_hash"), e.getMessage());
  }

  @Test
  @DisplayName("rulesetHash băm ĐÚNG BYTE của tệp — đọc lại từ CSDL vẫn khớp")
  void rulesetHashKhopByte() {
    Ruleset r = service.napBoQuyTac(RULESET);
    rulesets.flush();
    entityManager.clear();

    Ruleset doclai = rulesets.findById(r.getId()).orElseThrow();
    assertArrayEquals(RulesetPayload.rulesetHash(doclai.getJsonBody()),
        doclai.getRulesetHash(),
        "Nội dung đọc từ CSDL không băm ra đúng ruleset_hash. Kiểu cột json_body có còn là"
            + " LONGTEXT không? Xem V7.");
  }

  // ------------------------------------------------------------------ chấm

  @Test
  @DisplayName("MỐC TUẦN 5 — chấm toàn khóa, mỗi bản ghi có evidence_hash TÁI TÍNH ĐƯỢC")
  void chamToanKhoa() {
    var kq = service.chamHocKy(SEMESTER, RULESET, actorCoThat());

    assertTrue(kq.soSinhVien() >= 500,
        "Mốc tuần 5 đòi chấm 500 sinh viên, chấm được " + kq.soSinhVien());
    assertEquals(kq.soSinhVien(), scores.countByRunId(kq.runId()));

    // ---- Phần quan trọng nhất: tái tính evidence_hash từ dữ liệu gốc -------------
    //
    // Đi lại đúng đường mà một người kiểm toán bên ngoài sẽ đi: lấy bản ghi điểm danh của
    // sinh viên, tính leaf ATTEND của từng cái, băm thành evidence_hash, so với giá trị đã
    // lưu. Nếu bước này không chạy được thì "đóng góp học thuật" chỉ là một cột trong bảng.
    Map<Long, List<Attendance>> theoSv = new java.util.LinkedHashMap<>();
    for (Attendance a : attendances.findBySemesterForScoring(SEMESTER)) {
      theoSv.computeIfAbsent(a.getStudent().getId(), k -> new ArrayList<>()).add(a);
    }

    int daKiem = 0;
    int coHoatDong = 0;
    for (Score s : scores.findByRunIdOrderByIdAsc(kq.runId())) {
      List<String> leaves = new ArrayList<>();
      for (Attendance a : theoSv.getOrDefault(s.getStudent().getId(), List.of())) {
        leaves.add(LeafHasher.leafHex(AnchorDomain.ATTEND, AttendancePayload.of(a)));
      }

      assertArrayEquals(EvidenceHasher.hash(leaves), s.getEvidenceHash(),
          "evidence_hash của sinh viên " + s.getStudent().getMssv()
              + " KHÔNG tái tính được từ bản ghi điểm danh — đóng góp học thuật của đề tài"
              + " sống hay chết ở dòng này.");
      daKiem++;
      if (!leaves.isEmpty()) {
        coHoatDong++;
      }
    }

    assertEquals(kq.soSinhVien(), daKiem);
    assertTrue(coHoatDong > 0, "Phải có ít nhất một sinh viên có hoạt động để phép kiểm có nghĩa");
    assertEquals(kq.soCoHoatDong(), coHoatDong);
  }

  @Test
  @DisplayName("Điểm nằm trong dải bộ quy tắc cho phép: sàn 40, trần 90")
  void daiDiem() {
    var kq = service.chamHocKy(SEMESTER, RULESET, null);

    // Hệ quả số học của bộ quy tắc, đã ghi trong `hanChe` của chính nó. Chốt bằng test để
    // báo cáo không nói một con số mà hệ thống cho ra con số khác.
    assertTrue(kq.diemThapNhat() >= 40,
        "Sàn phải là 40 (điểm mặc định C2 25 + nền C4 15), thấp nhất đo được: "
            + kq.diemThapNhat());
    assertTrue(kq.diemCaoNhat() <= 90,
        "Trần phải là 90 vì C5 không bao giờ được cấp, cao nhất đo được: " + kq.diemCaoNhat());

    for (Score s : scores.findByRunIdOrderByIdAsc(kq.runId())) {
      assertEquals(s.getTotal(), s.getC1() + s.getC2() + s.getC3() + s.getC4() + s.getC5(),
          "total phải bằng tổng năm tiêu chí");
      assertNotNull(s.getClassification());
    }
  }

  @Test
  @DisplayName("Sinh viên không hoạt động: evidence_hash là băm của danh sách RỖNG")
  void khongHoatDong() {
    var kq = service.chamHocKy(SEMESTER, RULESET, null);

    byte[] rong = EvidenceHasher.hash(List.of());
    long soRong = scores.findByRunIdOrderByIdAsc(kq.runId()).stream()
        .filter(s -> java.util.Arrays.equals(s.getEvidenceHash(), rong))
        .count();

    // "Rỗng" là một phát biểu kiểm được, không phải thiếu dữ liệu.
    assertTrue(soRong > 0, "Với dữ liệu demo phải có sinh viên chưa tham gia hoạt động nào");
    assertEquals(kq.soSinhVien() - kq.soCoHoatDong(), soRong);
  }

  @Test
  @DisplayName("Bộ quy tắc sai học kỳ → TỪ CHỐI")
  void saiHocKy() {
    BusinessException e = assertThrows(BusinessException.class,
        () -> service.chamHocKy("2099-2", RULESET, null));
    assertTrue(e.getMessage().contains("dành cho học kỳ"), e.getMessage());
  }

  // ------------------------------------------------------------------ neo

  @Test
  @DisplayName("Điểm và bộ quy tắc vừa chấm nằm trong danh sách chờ neo")
  void namTrongDanhSachChoNeo() {
    var kq = service.chamHocKy(SEMESTER, RULESET, null);
    scores.flush();
    rulesets.flush();

    assertFalse(scores.findPendingAnchor(PageRequest.of(0, 10)).isEmpty(),
        "Miền SCORE sẽ không bao giờ có lô nào");
    assertFalse(rulesets.findPendingAnchor(PageRequest.of(0, 10)).isEmpty(),
        "Miền RULESET sẽ không bao giờ có lô nào — và khi đó điểm đã neo mất một nửa ý nghĩa");
    assertTrue(kq.runId() > 0);
  }

  @Test
  @DisplayName("AnchorSource dựng được payload cho mọi bản ghi vừa chấm")
  void anchorSourceDungDuocPayload() {
    service.chamHocKy(SEMESTER, RULESET, null);
    scores.flush();
    rulesets.flush();

    var scoreSource = new ScoreAnchorSource(scores, service);
    var rulesetSource = new RulesetAnchorSource(rulesets);

    // Payload dựng được nghĩa là mọi trường bắt buộc đều có: nonce, evidenceHash,
    // rulesetHash. Thiếu bất kỳ cái nào là ném ngay tại đây thay vì lúc job neo chạy 2 giờ
    // sáng.
    assertFalse(scoreSource.pending(600).isEmpty());
    assertFalse(rulesetSource.pending(10).isEmpty());
  }
}
