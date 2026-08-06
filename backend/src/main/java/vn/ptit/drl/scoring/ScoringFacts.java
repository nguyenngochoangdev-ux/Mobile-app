package vn.ptit.drl.scoring;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dữ kiện của một sinh viên trong một học kỳ — thứ mà biểu thức SpEL đọc.
 *
 * <p>Đây là <b>toàn bộ</b> bề mặt mà bộ quy tắc nhìn thấy. Giữ nó nhỏ và tường minh là có
 * chủ ý: nếu SpEL truy cập được cả entity thì một biểu thức trong tệp JSON sẽ gọi được
 * phương thức bất kỳ trên đối tượng Java, kể cả phương thức có tác dụng phụ. Bộ quy tắc là
 * <b>cấu hình</b>, không phải mã — nó chỉ được đọc số.
 *
 * <h2>Ba bản đồ, và vì sao có bản đồ thứ ba</h2>
 *
 * <ul>
 *   <li>{@code soLuot} — số hoạt động đã điểm danh, theo tiêu chí.
 *   <li>{@code diem} — tổng điểm hoạt động, theo tiêu chí.
 *   <li>{@code soLuotXacMinh} — <b>chỉ đếm bản ghi có {@code verified = true}</b>, tức là
 *       máy xác minh được sự có mặt chứ không phải cán bộ nhập tay.
 * </ul>
 *
 * <p>Bản đồ thứ ba là chỗ đóng góp của đề tài đi vào <i>công thức tính điểm</i>, không chỉ
 * nằm trong một cột thống kê. Bộ quy tắc thưởng điểm cho hoạt động được máy xác minh
 * (quy tắc {@code C1.3}, {@code C3.4}), nên chất lượng dữ liệu đầu vào <b>ảnh hưởng trực
 * tiếp lên điểm</b> — và điều đó đo được, không phải một lời tuyên bố.
 *
 * <h2>Mọi mã tiêu chí đều có mặt, giá trị 0</h2>
 *
 * <p>Điền sẵn cả năm mã thay vì để bản đồ thưa. SpEL đánh giá {@code soLuot['C5'] >= 1} trên
 * một khóa vắng mặt sẽ <b>ném lỗi</b> chứ không trả {@code false} — và một lỗi ở đó làm hỏng
 * cả lượt chấm chứ không chỉ một quy tắc.
 */
public final class ScoringFacts {

  /** Năm tiêu chí của Thông tư 16/2015/TT-BGDĐT. */
  public static final List<String> MA_TIEU_CHI = List.of("C1", "C2", "C3", "C4", "C5");

  private final Map<String, Integer> soLuot = new LinkedHashMap<>();
  private final Map<String, Integer> diem = new LinkedHashMap<>();
  private final Map<String, Integer> soLuotXacMinh = new LinkedHashMap<>();

  public ScoringFacts() {
    for (String ma : MA_TIEU_CHI) {
      soLuot.put(ma, 0);
      diem.put(ma, 0);
      soLuotXacMinh.put(ma, 0);
    }
  }

  /**
   * Cộng một bản ghi điểm danh vào dữ kiện.
   *
   * @param maTieuChi {@code events.criteria_code}; {@code null} hoặc mã lạ thì <b>bỏ qua</b>
   *     — sự kiện không gắn tiêu chí nào thì không cộng vào đâu cả, và đoán bừa còn tệ hơn
   * @param diemSuKien {@code events.points}
   * @param mayXacMinh {@code attendances.verified}
   */
  public void themLuot(String maTieuChi, int diemSuKien, boolean mayXacMinh) {
    if (maTieuChi == null || !soLuot.containsKey(maTieuChi)) {
      return;
    }
    soLuot.merge(maTieuChi, 1, Integer::sum);
    diem.merge(maTieuChi, diemSuKien, Integer::sum);
    if (mayXacMinh) {
      soLuotXacMinh.merge(maTieuChi, 1, Integer::sum);
    }
  }

  // Getter public vì SpEL đọc qua chúng: `soLuot['C3']`.

  public Map<String, Integer> getSoLuot() {
    return soLuot;
  }

  public Map<String, Integer> getDiem() {
    return diem;
  }

  public Map<String, Integer> getSoLuotXacMinh() {
    return soLuotXacMinh;
  }

  /** Tổng số hoạt động, mọi tiêu chí. */
  public int getTongSoLuot() {
    return soLuot.values().stream().mapToInt(Integer::intValue).sum();
  }

  /** Tổng số hoạt động được MÁY xác minh, mọi tiêu chí. */
  public int getTongSoLuotXacMinh() {
    return soLuotXacMinh.values().stream().mapToInt(Integer::intValue).sum();
  }

  @Override
  public String toString() {
    return "ScoringFacts{soLuot=" + soLuot + ", diem=" + diem
        + ", soLuotXacMinh=" + soLuotXacMinh + "}";
  }
}
