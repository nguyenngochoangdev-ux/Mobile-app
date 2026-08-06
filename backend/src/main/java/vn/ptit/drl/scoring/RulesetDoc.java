package vn.ptit.drl.scoring;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Bộ quy tắc chấm điểm rèn luyện, đã phân tích từ JSON.
 *
 * <h2>Vì sao Jackson được phép xuất hiện ở đây</h2>
 *
 * <p>{@code Jcs} cố ý không chạm Jackson vì leaf hash không được phụ thuộc cấu hình
 * tuần tự hóa. Ở đây <b>ngược lại</b>: cái được băm là <b>chính byte của tệp JSON gốc</b>
 * ({@code ruleset_hash}), không phải kết quả tuần tự hóa lại đối tượng này. Jackson chỉ dùng
 * để <i>đọc</i>, và kết quả đọc không đi vào phép băm nào.
 *
 * <p>Cùng lối với {@code AuditHasher.hashOfJson}: băm byte thô, phân tích riêng.
 *
 * <h2>{@code nguon} — trường trung thực nhất của bộ quy tắc</h2>
 *
 * <p>Nó khai mỗi tiêu chí được chấm bằng cái gì:
 *
 * <ul>
 *   <li>{@code TU_DONG} — hoàn toàn từ dữ liệu điểm danh.
 *   <li>{@code MAC_DINH} — <b>không có nguồn dữ liệu</b>; điểm là giá trị bộ quy tắc khai.
 *   <li>{@code HON_HOP} — một phần từ dữ liệu, phần còn lại là điểm nền mặc định.
 * </ul>
 *
 * <p>Không có trường này thì một bảng điểm 5 tiêu chí trông như thể cả 5 đều được tính từ
 * dữ liệu — và đó là điều <b>không đúng</b> với hệ thống hiện tại. Ghi ra làm cho báo cáo
 * nói được chính xác 50/100 điểm đến từ đo đạc và 40/100 là giả định.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RulesetDoc(
    String version,
    String semester,
    String canCu,
    int thang,
    List<TieuChi> tieuChi,
    List<PhanLoai> phanLoai,
    List<String> hanChe) {

  /** Nguồn dữ liệu của một tiêu chí. */
  public enum Nguon {
    /** Chấm hoàn toàn từ dữ liệu điểm danh. */
    TU_DONG,
    /** Không có nguồn dữ liệu — điểm là giá trị mặc định do bộ quy tắc khai. */
    MAC_DINH,
    /** Một phần từ dữ liệu, phần còn lại là điểm nền. */
    HON_HOP
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record TieuChi(
      String ma,
      String ten,
      int toiDa,
      Nguon nguon,
      int diemNen,
      String lyDo,
      List<QuyTac> quyTac) {}

  /**
   * Một quy tắc cộng điểm.
   *
   * @param dieuKien biểu thức <b>SpEL</b> trả về boolean, đánh giá trên {@link ScoringFacts}.
   *     Dùng SpEL chứ không phải một DSL tự viết — {@code CLAUDE.md} cấm "DSL riêng cho rule
   *     engine", và cấm đúng: một DSL cần bộ phân tích, bộ kiểm lỗi, thông báo lỗi, tài liệu,
   *     và test cho chính nó. SpEL có sẵn trong Spring, đã được kiểm chứng, và người đọc bộ
   *     quy tắc tra cứu được cú pháp ở tài liệu Spring.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record QuyTac(String ma, int diem, String dieuKien, String moTa) {}

  /** Khoảng điểm của một xếp loại. Cả hai đầu đều <b>bao gồm</b>. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PhanLoai(String ma, int tu, int den) {}

  /** Xếp loại ứng với một tổng điểm, hoặc {@code null} nếu không khoảng nào chứa nó. */
  public String xepLoai(int total) {
    for (PhanLoai p : phanLoai) {
      if (total >= p.tu() && total <= p.den()) {
        return p.ma();
      }
    }
    return null;
  }

  /** Tiêu chí theo mã, hoặc {@code null}. */
  public TieuChi tieuChi(String ma) {
    for (TieuChi t : tieuChi) {
      if (t.ma().equals(ma)) {
        return t;
      }
    }
    return null;
  }

  /**
   * Tổng số điểm <b>chấm được từ dữ liệu</b> (không tính điểm nền mặc định).
   *
   * <p>Con số này đi thẳng vào báo cáo: nó nói bao nhiêu phần của thang 100 là kết quả đo
   * đạc, và bao nhiêu là giả định. Tính từ bộ quy tắc thay vì viết tay để nó không bao giờ
   * lệch khỏi bộ quy tắc thật đang dùng.
   */
  public int diemTuDuLieu() {
    int tong = 0;
    for (TieuChi t : tieuChi) {
      for (QuyTac q : t.quyTac()) {
        tong += q.diem();
      }
    }
    return tong;
  }

  /** Tổng điểm nền — phần cố định, không phụ thuộc sinh viên làm gì. */
  public int diemMacDinh() {
    int tong = 0;
    for (TieuChi t : tieuChi) {
      tong += t.diemNen();
    }
    return tong;
  }
}
