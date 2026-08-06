package vn.ptit.drl.scoring;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * Đánh giá bộ quy tắc bằng SpEL.
 *
 * <h2>⚠️ {@code SimpleEvaluationContext}, KHÔNG phải {@code StandardEvaluationContext}</h2>
 *
 * <p>Đây là dòng quan trọng nhất của cả lớp. {@code StandardEvaluationContext} (mặc định của
 * SpEL) cho phép biểu thức <b>gọi phương thức bất kỳ, dựng đối tượng bất kỳ, truy cập kiểu
 * bất kỳ</b> — kể cả {@code T(java.lang.Runtime).getRuntime().exec(...)}. Bộ quy tắc là một
 * tệp JSON do người dùng nạp lên, nên dùng context đó là biến nó thành <b>một đường thực thi
 * mã tuỳ ý</b>.
 *
 * <p>{@code SimpleEvaluationContext.forReadOnlyDataBinding()} chỉ cho đọc thuộc tính và chỉ
 * số. Không gọi được phương thức, không tham chiếu được kiểu, không gán được. Đúng bằng thứ
 * bộ quy tắc cần: đọc số rồi so sánh.
 *
 * <p>Đây là lỗ hổng có tên trong danh sách CWE và đã gây ra nhiều CVE thật ở các ứng dụng
 * Spring. <b>Nên nêu trong báo cáo</b> ở phần bàn về rule engine: chọn SpEL là mượn được một
 * bộ đánh giá đã kiểm chứng, nhưng phải biết đóng đúng một cái van, và cái van đó không bật
 * sẵn.
 *
 * <h2>Biểu thức hỏng thì hỏng một quy tắc, không hỏng cả lượt chấm</h2>
 *
 * <p>Một quy tắc sai cú pháp hoặc đọc khóa không tồn tại sẽ bị ghi vào danh sách lỗi và
 * <b>coi như không thỏa</b> — chứ không ném ra ngoài. Ném ra ngoài nghĩa là một dấu chấm phẩy
 * thừa trong tệp quy tắc làm cả 500 sinh viên không chấm được.
 *
 * <p>Nhưng lỗi <b>không bị nuốt</b>: {@link KetQua#loi()} mang chúng lên, và
 * {@code ScoringService} từ chối chạy nếu bộ quy tắc có quy tắc hỏng — kiểm <b>một lần trước
 * khi chấm</b> thay vì 500 lần trong lúc chấm.
 */
@Component
public class RuleEvaluator {

  private final ExpressionParser parser = new SpelExpressionParser();

  /** Biểu thức đã phân tích, dùng lại giữa 500 sinh viên. Khóa là chính chuỗi biểu thức. */
  private final Map<String, Expression> cache = new ConcurrentHashMap<>();

  /**
   * Kết quả chấm một sinh viên.
   *
   * @param diemTheoTieuChi mã tiêu chí → điểm, đã cắt trần theo {@code toiDa}
   * @param quyTacThoa mã các quy tắc đã thỏa, theo thứ tự trong bộ quy tắc
   * @param loi biểu thức hỏng, rỗng khi mọi thứ bình thường
   */
  public record KetQua(Map<String, Integer> diemTheoTieuChi, int total, String xepLoai,
                       List<String> quyTacThoa, List<String> loi) {}

  /** Chấm một sinh viên. */
  public KetQua danhGia(RulesetDoc ruleset, ScoringFacts facts) {
    var context = SimpleEvaluationContext.forReadOnlyDataBinding().withRootObject(facts).build();

    Map<String, Integer> diemTheoTieuChi = new LinkedHashMap<>();
    List<String> thoa = new ArrayList<>();
    List<String> loi = new ArrayList<>();
    int total = 0;

    for (RulesetDoc.TieuChi tc : ruleset.tieuChi()) {
      int diem = tc.diemNen();

      for (RulesetDoc.QuyTac qt : tc.quyTac()) {
        Boolean ket;
        try {
          ket = cache.computeIfAbsent(qt.dieuKien(), parser::parseExpression)
              .getValue(context, Boolean.class);
        } catch (SpelParseException | SpelEvaluationException | IllegalStateException e) {
          loi.add(qt.ma() + ": " + e.getMessage());
          continue;
        }
        if (Boolean.TRUE.equals(ket)) {
          diem += qt.diem();
          thoa.add(qt.ma());
        }
      }

      // Cắt trần. Bộ quy tắc có thể khai tổng điểm các quy tắc vượt `toiDa` — cắt ở đây thay
      // vì từ chối bộ quy tắc, vì "cộng dồn rồi cắt trần" là cách viết quy chế bình thường.
      // Sàn 0 để một `diemNen` âm do gõ nhầm không kéo tổng xuống dưới 0.
      diem = Math.max(0, Math.min(diem, tc.toiDa()));

      diemTheoTieuChi.put(tc.ma(), diem);
      total += diem;
    }

    return new KetQua(diemTheoTieuChi, total, ruleset.xepLoai(total),
        List.copyOf(thoa), List.copyOf(loi));
  }

  /**
   * Kiểm mọi biểu thức trong bộ quy tắc <b>trước khi</b> chấm ai cả.
   *
   * <p>Chạy trên một {@link ScoringFacts} rỗng: đủ để bắt sai cú pháp, sai tên thuộc tính, và
   * biểu thức không trả về boolean. Không bắt được lỗi chỉ xuất hiện với dữ liệu cụ thể —
   * nhưng bề mặt mà biểu thức nhìn thấy chỉ có ba bản đồ số nguyên đã điền đủ khóa, nên loại
   * lỗi đó gần như không còn chỗ tồn tại.
   *
   * @return danh sách lỗi, rỗng nghĩa là bộ quy tắc dùng được
   */
  public List<String> kiemBoQuyTac(RulesetDoc ruleset) {
    List<String> loi = new ArrayList<>(danhGia(ruleset, new ScoringFacts()).loi());

    for (RulesetDoc.TieuChi tc : ruleset.tieuChi()) {
      int tongQuyTac = tc.quyTac().stream().mapToInt(RulesetDoc.QuyTac::diem).sum();

      if (tc.diemNen() > tc.toiDa()) {
        loi.add(tc.ma() + ": diemNen " + tc.diemNen() + " vượt toiDa " + tc.toiDa());
      }
      if (tc.diemNen() < 0) {
        loi.add(tc.ma() + ": diemNen âm (" + tc.diemNen() + ")");
      }
      // Cảnh báo chứ không phải lỗi — nhưng vẫn báo, vì nó gần như luôn là gõ nhầm: khai
      // quy tắc cộng tới một mức không bao giờ đạt tới được.
      if (tc.diemNen() + tongQuyTac > tc.toiDa()) {
        loi.add(tc.ma() + ": diemNen + tổng quy tắc = " + (tc.diemNen() + tongQuyTac)
            + " vượt toiDa " + tc.toiDa() + " — phần vượt sẽ bị cắt, kiểm lại có cố ý không");
      }
      if (tc.nguon() == RulesetDoc.Nguon.MAC_DINH && !tc.quyTac().isEmpty()) {
        loi.add(tc.ma() + ": khai nguon = MAC_DINH nhưng lại có " + tc.quyTac().size()
            + " quy tắc — hai thứ mâu thuẫn nhau");
      }
      if (tc.nguon() == RulesetDoc.Nguon.TU_DONG && tc.diemNen() != 0) {
        loi.add(tc.ma() + ": khai nguon = TU_DONG nhưng diemNen = " + tc.diemNen()
            + " — điểm nền khác 0 nghĩa là có phần KHÔNG chấm từ dữ liệu, phải khai HON_HOP");
      }
    }

    int tongToiDa = ruleset.tieuChi().stream().mapToInt(RulesetDoc.TieuChi::toiDa).sum();
    if (tongToiDa != ruleset.thang()) {
      loi.add("Tổng toiDa của các tiêu chí = " + tongToiDa + ", khác thang " + ruleset.thang());
    }
    return List.copyOf(loi);
  }
}
