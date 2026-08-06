package vn.ptit.drl.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Rule engine — test thuần, không cần Spring hay CSDL.
 *
 * <p>Bộ quy tắc thật ({@code rulesets/2026-1.v1.json}) được kiểm riêng ở
 * {@code ScoringServiceDbTest}; lớp này kiểm <b>hành vi của bộ đánh giá</b> bằng các bộ quy
 * tắc dựng tại chỗ.
 */
class RuleEvaluatorTest {

  private final RuleEvaluator evaluator = new RuleEvaluator();

  private static RulesetDoc boQuyTac(RulesetDoc.TieuChi... tc) {
    return new RulesetDoc("test.v1", "2026-1", "test", 100, List.of(tc),
        List.of(new RulesetDoc.PhanLoai("XUAT_SAC", 90, 100),
            new RulesetDoc.PhanLoai("TOT", 80, 89),
            new RulesetDoc.PhanLoai("KHA", 65, 79),
            new RulesetDoc.PhanLoai("TRUNG_BINH", 50, 64),
            new RulesetDoc.PhanLoai("YEU", 35, 49),
            new RulesetDoc.PhanLoai("KEM", 0, 34)),
        List.of());
  }

  private static RulesetDoc.TieuChi tuDong(String ma, int toiDa, RulesetDoc.QuyTac... qt) {
    return new RulesetDoc.TieuChi(ma, ma, toiDa, RulesetDoc.Nguon.TU_DONG, 0, "", List.of(qt));
  }

  private static RulesetDoc.QuyTac qt(String ma, int diem, String dieuKien) {
    return new RulesetDoc.QuyTac(ma, diem, dieuKien, "");
  }

  // ------------------------------------------------------------------ đánh giá

  @Test
  @DisplayName("Quy tắc thỏa thì cộng điểm, không thỏa thì không")
  void congDiemTheoDieuKien() {
    var doc = boQuyTac(tuDong("C1", 20,
        qt("C1.1", 8, "soLuot['C1'] >= 1"),
        qt("C1.2", 6, "soLuot['C1'] >= 3")));

    var facts = new ScoringFacts();
    facts.themLuot("C1", 5, true);

    var kq = evaluator.danhGia(doc, facts);

    assertEquals(8, kq.diemTheoTieuChi().get("C1"));
    assertEquals(List.of("C1.1"), kq.quyTacThoa());
    assertTrue(kq.loi().isEmpty());
  }

  @Test
  @DisplayName("Điểm bị CẮT TRẦN theo toiDa")
  void catTran() {
    var doc = boQuyTac(tuDong("C1", 10,
        qt("C1.1", 8, "true"), qt("C1.2", 8, "true")));

    assertEquals(10, evaluator.danhGia(doc, new ScoringFacts()).diemTheoTieuChi().get("C1"));
  }

  @Test
  @DisplayName("soLuotXacMinh chỉ đếm bản ghi MÁY xác minh — chất lượng dữ liệu vào công thức")
  void chiDemBanGhiXacMinh() {
    // Đây là chỗ đóng góp của đề tài đi vào công thức tính điểm, không chỉ nằm trong một
    // cột thống kê: điểm danh tay (verified = false) không được thưởng.
    var doc = boQuyTac(tuDong("C3", 20, qt("C3.4", 4, "soLuotXacMinh['C3'] >= 2")));

    var tay = new ScoringFacts();
    tay.themLuot("C3", 5, false);
    tay.themLuot("C3", 5, false);
    assertEquals(0, evaluator.danhGia(doc, tay).diemTheoTieuChi().get("C3"),
        "Hai bản ghi cán bộ nhập tay KHÔNG được thưởng điểm xác minh");

    var may = new ScoringFacts();
    may.themLuot("C3", 5, true);
    may.themLuot("C3", 5, true);
    assertEquals(4, evaluator.danhGia(doc, may).diemTheoTieuChi().get("C3"));
  }

  @Test
  @DisplayName("Tiêu chí MAC_DINH cho đúng điểm nền, không phụ thuộc dữ liệu")
  void macDinh() {
    var doc = boQuyTac(new RulesetDoc.TieuChi(
        "C2", "C2", 25, RulesetDoc.Nguon.MAC_DINH, 25, "", List.of()));

    var nhieu = new ScoringFacts();
    nhieu.themLuot("C2", 100, true);

    assertEquals(25, evaluator.danhGia(doc, new ScoringFacts()).diemTheoTieuChi().get("C2"));
    assertEquals(25, evaluator.danhGia(doc, nhieu).diemTheoTieuChi().get("C2"));
  }

  @Test
  @DisplayName("Xếp loại theo tổng điểm, hai đầu khoảng đều BAO GỒM")
  void xepLoai() {
    var doc = boQuyTac(tuDong("C1", 100, qt("x", 0, "true")));
    assertEquals("KEM", doc.xepLoai(0));
    assertEquals("KEM", doc.xepLoai(34));
    assertEquals("YEU", doc.xepLoai(35));
    assertEquals("KHA", doc.xepLoai(79));
    assertEquals("TOT", doc.xepLoai(80));
    assertEquals("XUAT_SAC", doc.xepLoai(90));
    assertEquals("XUAT_SAC", doc.xepLoai(100));
  }

  // ------------------------------------------------------------------ an toàn

  @Nested
  @DisplayName("⚠️ Biểu thức KHÔNG được chạy mã tuỳ ý")
  class AnToan {

    @Test
    @DisplayName("Tham chiếu KIỂU bị chặn — T(java.lang.Runtime) không dùng được")
    void chanThamChieuKieu() {
      // Đây là phép kiểm quan trọng nhất của cả lớp. Bộ quy tắc là một tệp JSON do người
      // dùng nạp lên; nếu SpEL chạy với StandardEvaluationContext thì biểu thức gọi được
      // phương thức bất kỳ, dựng đối tượng bất kỳ — tức là một đường THỰC THI MÃ TUỲ Ý.
      var doc = boQuyTac(tuDong("C1", 20,
          qt("C1.X", 10, "T(java.lang.System).getProperty('user.name') != null")));

      var kq = evaluator.danhGia(doc, new ScoringFacts());

      assertFalse(kq.loi().isEmpty(), "Tham chiếu kiểu PHẢI bị từ chối");
      assertEquals(0, kq.diemTheoTieuChi().get("C1"), "Và không được cộng điểm");
    }

    @Test
    @DisplayName("Gọi phương thức bị chặn")
    void chanGoiPhuongThuc() {
      var doc = boQuyTac(tuDong("C1", 20, qt("C1.X", 10, "getSoLuot().size() > 0")));

      var kq = evaluator.danhGia(doc, new ScoringFacts());
      assertFalse(kq.loi().isEmpty(), "Gọi phương thức PHẢI bị từ chối");
    }

    @Test
    @DisplayName("Đọc thuộc tính thì ĐƯỢC — đúng bằng thứ bộ quy tắc cần")
    void chophepDocThuocTinh() {
      var doc = boQuyTac(tuDong("C1", 20, qt("C1.X", 10, "tongSoLuot >= 1")));

      var facts = new ScoringFacts();
      facts.themLuot("C1", 5, true);

      var kq = evaluator.danhGia(doc, facts);
      assertTrue(kq.loi().isEmpty(), String.join("; ", kq.loi()));
      assertEquals(10, kq.diemTheoTieuChi().get("C1"));
    }
  }

  // ------------------------------------------------------------------ lỗi

  @Nested
  @DisplayName("Biểu thức hỏng thì hỏng MỘT quy tắc, không hỏng cả lượt chấm")
  class Loi {

    @Test
    @DisplayName("Sai cú pháp → ghi lỗi, coi như không thỏa, các quy tắc khác vẫn chạy")
    void saiCuPhap() {
      var doc = boQuyTac(tuDong("C1", 20,
          qt("C1.HONG", 10, "soLuot['C1' >= "),
          qt("C1.OK", 5, "true")));

      var kq = evaluator.danhGia(doc, new ScoringFacts());

      assertEquals(1, kq.loi().size());
      assertTrue(kq.loi().get(0).startsWith("C1.HONG"));
      assertEquals(5, kq.diemTheoTieuChi().get("C1"), "Quy tắc lành vẫn phải chạy");
    }

    @Test
    @DisplayName("Mọi mã tiêu chí đều có mặt với giá trị 0 — không ném lỗi khóa vắng mặt")
    void moiMaDeuCoMat() {
      var doc = boQuyTac(tuDong("C1", 20, qt("C1.X", 10, "soLuot['C5'] >= 1")));

      var kq = evaluator.danhGia(doc, new ScoringFacts());
      assertTrue(kq.loi().isEmpty(), String.join("; ", kq.loi()));
      assertEquals(0, kq.diemTheoTieuChi().get("C1"));
    }
  }

  // ------------------------------------------------------------------ kiểm bộ quy tắc

  @Nested
  @DisplayName("kiemBoQuyTac — bắt lỗi MỘT LẦN trước khi chấm 500 người")
  class KiemBoQuyTac {

    @Test
    @DisplayName("Bộ quy tắc lành thì không lỗi")
    void lanh() {
      var doc = boQuyTac(
          tuDong("C1", 20, qt("a", 20, "soLuot['C1'] >= 1")),
          new RulesetDoc.TieuChi("C2", "C2", 80, RulesetDoc.Nguon.MAC_DINH, 80, "", List.of()));

      assertTrue(evaluator.kiemBoQuyTac(doc).isEmpty(),
          String.join("; ", evaluator.kiemBoQuyTac(doc)));
    }

    @Test
    @DisplayName("Tổng toiDa khác thang → lỗi")
    void tongToiDaSai() {
      var doc = boQuyTac(tuDong("C1", 20));
      assertTrue(evaluator.kiemBoQuyTac(doc).stream().anyMatch(l -> l.contains("khác thang")));
    }

    @Test
    @DisplayName("MAC_DINH mà lại có quy tắc → mâu thuẫn")
    void macDinhMaCoQuyTac() {
      var doc = boQuyTac(new RulesetDoc.TieuChi(
          "C1", "C1", 100, RulesetDoc.Nguon.MAC_DINH, 50, "", List.of(qt("a", 5, "true"))));

      assertTrue(evaluator.kiemBoQuyTac(doc).stream().anyMatch(l -> l.contains("mâu thuẫn")));
    }

    @Test
    @DisplayName("TU_DONG mà có điểm nền → phải khai HON_HOP")
    void tuDongMaCoDiemNen() {
      var doc = boQuyTac(new RulesetDoc.TieuChi(
          "C1", "C1", 100, RulesetDoc.Nguon.TU_DONG, 15, "", List.of(qt("a", 5, "true"))));

      assertTrue(evaluator.kiemBoQuyTac(doc).stream().anyMatch(l -> l.contains("HON_HOP")));
    }

    @Test
    @DisplayName("diemNen vượt toiDa → lỗi")
    void diemNenVuot() {
      var doc = boQuyTac(new RulesetDoc.TieuChi(
          "C1", "C1", 100, RulesetDoc.Nguon.MAC_DINH, 120, "", List.of()));

      assertTrue(evaluator.kiemBoQuyTac(doc).stream().anyMatch(l -> l.contains("vượt toiDa")));
    }

    @Test
    @DisplayName("Biểu thức hỏng bị bắt ở đây, trước khi chấm ai")
    void batBieuThucHong() {
      var doc = boQuyTac(tuDong("C1", 100, qt("a", 100, "khong_ton_tai['C1'] >= 1")));
      assertFalse(evaluator.kiemBoQuyTac(doc).isEmpty());
    }
  }

  // ------------------------------------------------------------------ số liệu

  @Test
  @DisplayName("diemTuDuLieu / diemMacDinh tính từ chính bộ quy tắc, không viết tay")
  void soLieuTuBoQuyTac() {
    var doc = boQuyTac(
        tuDong("C1", 20, qt("a", 8, "true"), qt("b", 12, "true")),
        new RulesetDoc.TieuChi("C2", "C2", 80, RulesetDoc.Nguon.MAC_DINH, 25, "", List.of()));

    // Con số này đi thẳng vào báo cáo: bao nhiêu phần của thang 100 là kết quả đo đạc, bao
    // nhiêu là giả định. Tính từ bộ quy tắc để nó không bao giờ lệch khỏi bản đang dùng.
    assertEquals(20, doc.diemTuDuLieu());
    assertEquals(25, doc.diemMacDinh());
  }
}
