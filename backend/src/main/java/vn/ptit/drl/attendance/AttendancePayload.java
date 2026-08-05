package vn.ptit.drl.attendance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dựng payload miền {@code ATTEND} từ một bản ghi điểm danh.
 *
 * <p><b>Đây là HỢP ĐỒNG giữa backend và verifier.</b> Verifier phải dựng lại đúng payload này
 * từ bundle của sinh viên để tính lại leaf hash và đối chiếu với proof. Lệch một trường, một
 * kiểu, hay một cách định dạng thời gian là mọi proof fail — và fail im lặng.
 *
 * <p>Nửa JS: {@code verifier/src/attend.mjs}. Test vector chung:
 * {@code canonical-vectors.json}, các vector có tiền tố {@code attend-payload}.
 * <b>Sửa lớp này phải đi kèm {@code /canonical-hash}.</b>
 *
 * <h2>Chọn trường nào — và vì sao</h2>
 *
 * <ul>
 *   <li>{@code method} <b>bắt buộc có.</b> Nó phân biệt {@code QR_SCAN} với {@code MANUAL} —
 *       chính là tín hiệu chất lượng dữ liệu mà đề tài nêu làm đóng góp (PROJECT.md §10).
 *       Không neo nó thì cán bộ sửa được {@code MANUAL} thành {@code QR_SCAN} về sau mà
 *       proof vẫn hợp lệ, tức là mất đúng thứ đang muốn chứng minh.
 *   <li>{@code deviceFp} <b>có neo</b>, dù nó là dữ liệu quasi-định danh. Không neo thì đổi
 *       được thiết bị đã dùng mà không phá proof, làm bằng chứng device binding vô nghĩa.
 *       <b>Cái giá phải ghi vào phần hạn chế:</b> sinh viên đưa bundle cho nhà tuyển dụng là
 *       lộ luôn {@code deviceFp} của chính mình. Nó chỉ là UUID trong {@code localStorage},
 *       không phải mã phần cứng, nên thiệt hại giới hạn ở mức "trình duyệt nào" — nhưng vẫn
 *       phải nói ra. Tiết lộ chọn lọc (BBS+) là hướng phát triển.
 *   <li>{@code qrSlot} <b>KHÔNG neo.</b> Nó là chi tiết hiện thực của cơ chế QR động và đã
 *       được {@code verified} tóm tắt lại. Neo thêm chỉ làm payload to ra mà không thêm điều
 *       gì chứng minh được.
 *   <li>{@code checkOutAt} <b>có neo</b>, nên chỉ được neo bản ghi của sự kiện <b>đã kết
 *       thúc</b> — xem {@link AttendanceAnchorSource}. Neo sớm rồi sinh viên check-out sau
 *       là leaf thành lạc hậu vĩnh viễn.
 * </ul>
 *
 * <h2>Không có trường phiên bản lược đồ</h2>
 *
 * <p>Cân nhắc rồi bỏ: thêm {@code schemaVersion} là rẻ, nhưng nó chỉ có ích nếu lược đồ đổi —
 * mà đổi lược đồ trong 8 tuần thì đằng nào cũng phải neo lại từ đầu. Ghi vào hạn chế: đổi
 * lược đồ payload là thay đổi phá vỡ tương thích, mọi proof cũ phải neo lại.
 */
public final class AttendancePayload {

  private static final HexFormat HEX = HexFormat.of(); // mặc định chữ thường

  private AttendancePayload() {}

  /**
   * Dựng payload chuẩn tắc. Trả về {@link LinkedHashMap} cho dễ đọc khi gỡ lỗi — thứ tự khóa
   * ở đây không quan trọng vì {@code Jcs} sắp xếp lại toàn bộ.
   *
   * @throws IllegalStateException nếu bản ghi thiếu dữ liệu bắt buộc để neo
   */
  public static Map<String, Object> of(Attendance a) {
    if (a.getNonce() == null || a.getNonce().length != 16) {
      throw new IllegalStateException(
          "Bản ghi điểm danh " + a.getId() + " thiếu nonce 16 byte — không neo được."
              + " Xem PROJECT.md §2.3.");
    }

    Map<String, Object> p = new LinkedHashMap<>();
    p.put("studentCode", a.getStudent().getMssv());
    p.put("eventId", a.getEvent().getId());
    p.put("method", a.getMethod().name());
    p.put("checkInAt", isoSeconds(a.getCheckinAt()));
    p.put("checkOutAt", isoSeconds(a.getCheckoutAt()));
    p.put("deviceFp", a.getDeviceFp());
    p.put("lat", coordinate(a.getLat()));
    p.put("lng", coordinate(a.getLng()));
    p.put("verified", a.getVerified());
    p.put("geofenceOk", a.getGeofenceOk());
    p.put("nonce", "0x" + HEX.formatHex(a.getNonce()));
    return p;
  }

  /**
   * ISO-8601 UTC, <b>độ chính xác GIÂY</b>, hậu tố {@code Z}.
   *
   * <p>Cột trong CSDL là {@code DATETIME(3)} — mili giây. Không cắt xuống giây thì
   * {@code Instant.toString()} sinh ra {@code "...:58.123Z"} ở bản ghi có phần lẻ và
   * {@code "...:58Z"} ở bản ghi tròn giây, tức là hai định dạng khác nhau trong cùng một lô.
   * Verifier dựng lại từ bundle gần như chắc chắn sẽ ra định dạng còn lại.
   * Chốt giây, theo {@code docs/canonicalization.md} §4 quy tắc 7.
   */
  static String isoSeconds(Instant t) {
    return t == null ? null : t.truncatedTo(ChronoUnit.SECONDS).toString();
  }

  /**
   * Toạ độ về {@link Double}.
   *
   * <p>Cột là {@code DECIMAL(10,7)} nên JDBC trả {@link BigDecimal} mang theo số chữ số thập
   * phân của lược đồ: {@code 21.0285000}. {@code Jcs} đã quy về {@code doubleValue()} nên
   * chuyện này không gây lệch, nhưng chuyển ở đây cho tường minh — đọc payload là thấy ngay
   * kiểu thật sự được neo.
   *
   * <p>Toạ độ Việt Nam nằm trong khoảng vĩ độ 8–24 và kinh độ 102–110, thừa an toàn so với
   * vùng {@code [1e-3, 1e7)} mà {@code Jcs} cho phép. Toạ độ sát 0 sẽ làm {@code Jcs} ném
   * lỗi — đúng chủ ý, vì đó là dữ liệu rác chứ không phải toạ độ thật.
   */
  static Double coordinate(BigDecimal v) {
    return v == null ? null : v.doubleValue();
  }
}
