package vn.ptit.drl.credential;

import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Đếm hoạt động của một sinh viên để đề xuất số liệu cho credential sắp cấp.
 *
 * <h2>Vì sao phải có lớp này thay vì để cán bộ gõ tay</h2>
 *
 * <p>{@code activityCount} và {@code totalPoints} đi thẳng vào payload được ký và neo. Neo xong
 * thì <b>không sửa được</b> — muốn đổi phải thu hồi rồi cấp lại. Một con số gõ nhầm vì thế
 * không phải lỗi nhập liệu bình thường; nó là một phát biểu sai được ký tên và đóng dấu thời
 * gian vĩnh viễn. Cho nên giao diện cấp credential <b>đọc số từ dữ liệu</b>, còn ô nhập chỉ để
 * cán bộ sửa khi có lý do và tự chịu trách nhiệm.
 *
 * <p>SQL này trước đây nằm trong {@link CredentialNowRunner}. Tách ra đây vì giờ có hai đường
 * cấp credential (script và giao diện web); hai bản sao của cùng một phép đếm sẽ trôi khỏi
 * nhau, và lúc đó cùng một sinh viên nhận hai con số khác nhau tùy đường cấp.
 *
 * <h2>Lọc theo học kỳ — đọc từ cột, không đoán từ ngày</h2>
 *
 * <p>Lọc bằng {@code events.semester} (có từ V8). Bản đầu của lớp này chép nguyên phép đếm cũ
 * của {@link CredentialNowRunner} — <b>gom toàn bộ mọi kỳ</b> — vì javadoc chỗ đó vẫn ghi
 * "{@code events} không có cột học kỳ", câu đúng lúc viết nhưng V8 đã trả khoản nợ ấy. Số sai
 * này sẽ đi vào một credential được ký và neo vĩnh viễn, nên nó không thuộc loại sửa sau cũng
 * được.
 *
 * <p>Không tự chế mốc ngày để chia kỳ trong Java. Quy ước học kỳ nằm trong cột, cán bộ sửa
 * được bằng một lệnh {@code UPDATE}; viết nó thành hàm thì đổi lịch học phải deploy lại. Xem
 * PROJECT.md §5.1.
 *
 * <p><b>Sự kiện có {@code semester = NULL} bị bỏ qua</b>, giống việc chấm điểm. NULL nghĩa là
 * chưa xác định được kỳ; đếm nó vào là đoán bừa, và đoán bừa ở đây thành một con số đã ký.
 */
@Service
@RequiredArgsConstructor
public class CredentialSuggestionService {

  /**
   * Số liệu đề xuất cho một sinh viên.
   *
   * @param verifiedCount số bản ghi điểm danh máy xác minh được (QR, không phải cán bộ gõ tay).
   *     Không đi vào credential — nó là <b>chỉ số chất lượng dữ liệu</b> để cán bộ biết mình
   *     đang sắp ký tên vào cái gì. Tỉ lệ thấp nghĩa là phần lớn số này do người khai.
   */
  public record Suggestion(int activityCount, int totalPoints, int verifiedCount) {}

  private final JdbcTemplate jdbc;

  public Suggestion forStudent(Long studentId, String semester) {
    Map<String, Object> row = jdbc.queryForMap("""
        SELECT COUNT(*)                                                 AS so_hoat_dong,
               COALESCE(SUM(e.points), 0)                               AS tong_diem,
               COALESCE(SUM(CASE WHEN a.verified THEN 1 ELSE 0 END), 0) AS da_xac_minh
          FROM attendances a JOIN events e ON e.id = a.event_id
         WHERE a.student_id = ? AND e.semester = ?
        """, studentId, semester);

    return new Suggestion(
        ((Number) row.get("so_hoat_dong")).intValue(),
        ((Number) row.get("tong_diem")).intValue(),
        ((Number) row.get("da_xac_minh")).intValue());
  }
}
