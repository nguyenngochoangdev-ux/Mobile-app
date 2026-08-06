package vn.ptit.drl.credential;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dựng payload miền {@code CRED} từ một credential.
 *
 * <p><b>Đây là HỢP ĐỒNG giữa backend và verifier.</b> Nửa JS: {@code verifier/src/cred.mjs}.
 * Test vector chung: {@code canonical-vectors.json}, các vector có tiền tố
 * {@code cred-payload}. <b>Sửa lớp này phải đi kèm {@code /canonical-hash}.</b>
 *
 * <h2>Mười một trường, và một object lồng</h2>
 *
 * <p>Khác {@link vn.ptit.drl.attendance.AttendancePayload} ở chỗ có {@code claims} là object
 * lồng — cố ý. Phần định danh (ai cấp, cấp cho ai, khi nào, thu hồi ở bit nào) giống nhau ở
 * mọi loại credential; phần <b>nội dung phát biểu</b> thì không. Tách ra thành {@code claims}
 * để tuần 5 thêm loại {@code DIEM_REN_LUYEN} mà không phải đổi phần khung.
 *
 * <p>Cái giá: {@code Jcs} phải sắp xếp khóa <b>đệ quy</b>. Đó là quy tắc 1 của
 * {@code docs/canonicalization.md} §4 và đã có vector {@code score-nested} chốt từ tuần 3,
 * nên đây không phải rủi ro mới.
 *
 * <h2>Chọn trường nào — và vì sao</h2>
 *
 * <ul>
 *   <li>{@code issuerAddress} <b>bắt buộc có trong payload</b>, không để ngoài bundle. Verifier
 *       phục hồi địa chỉ từ chữ ký rồi đối chiếu với trường này, rồi mới hỏi
 *       {@code IssuerRegistry} xem địa chỉ đó có được cấp quyền không. Nếu địa chỉ chỉ nằm
 *       trong phần vỏ của bundle thì sửa nó không phá leaf, và verifier sẽ đi hỏi
 *       {@code IssuerRegistry} về <b>địa chỉ do kẻ tấn công chọn</b>. Neo nó vào leaf làm
 *       chuyện đó bất khả thi.
 *   <li>{@code statusListIndex} <b>bắt buộc có trong payload</b>, cùng một lý do và nghiêm
 *       trọng hơn: để ngoài leaf thì người cầm credential <b>đã bị thu hồi</b> chỉ cần đổi
 *       con số đó sang một bit chưa bật, và verifier báo "còn hiệu lực". Thu hồi trở thành
 *       vô nghĩa.
 *   <li>{@code studentName} <b>có neo.</b> Nhà tuyển dụng cần biết credential nói về ai, và
 *       cần biết cái tên đó không sửa được. <b>Cái giá:</b> tên thật nằm trong bundle —
 *       nhưng bundle là thứ sinh viên <i>chủ động</i> đưa đi, khác hẳn với dữ liệu rò qua
 *       sibling hash (đã chặn bằng nonce, PROJECT.md §2.3).
 *   <li>{@code signature} <b>KHÔNG neo</b> — không thể, và không cần. Không thể vì chữ ký ký
 *       chính leaf, đưa nó vào payload là vòng tròn. Không cần vì hai thứ chứng minh hai
 *       điều khác nhau: leaf + proof + root chứng minh <b>bản ghi tồn tại từ lúc nào và
 *       không sửa được</b>; chữ ký chứng minh <b>ai phát biểu</b>. Cái sau không cần cái
 *       trước bảo vệ, vì sửa chữ ký thì nó hết verify.
 *   <li>{@code revokedAt} <b>KHÔNG neo.</b> Trạng thái thu hồi thay đổi được sau khi cấp, mà
 *       leaf thì không. Nguồn sự thật về thu hồi là bit trên {@code StatusList}, đọc bằng
 *       một {@code eth_call} — đó chính là lý do {@code statusListIndex} phải neo.
 * </ul>
 *
 * <h2>Không có trường phiên bản lược đồ</h2>
 *
 * <p>Cùng quyết định với {@code AttendancePayload}: đổi lược đồ payload là thay đổi phá vỡ
 * tương thích, mọi proof cũ phải neo lại. Ghi vào phần hạn chế của báo cáo.
 */
public final class CredentialPayload {

  private static final HexFormat HEX = HexFormat.of(); // mặc định chữ thường

  private CredentialPayload() {}

  /**
   * Dựng payload chuẩn tắc.
   *
   * <p>Đọc <b>duy nhất</b> các cột đã chụp ảnh trên chính {@link Credential} — không chạm
   * {@code credential.getStudent()} hay {@code credential.getIssuerOrg()}. Đọc qua khóa
   * ngoại là mở đường cho việc đổi tên sinh viên làm hỏng proof đã neo; xem đầu file
   * {@code V4__credential_snapshot_va_chu_ky.sql}.
   *
   * @throws IllegalStateException nếu bản ghi thiếu dữ liệu bắt buộc để neo
   */
  public static Map<String, Object> of(Credential c) {
    if (c.getNonce() == null || c.getNonce().length != 16) {
      throw new IllegalStateException(
          "Credential " + c.getId() + " thiếu nonce 16 byte — không neo được."
              + " Xem PROJECT.md §2.3.");
    }
    requireLowercaseAddress(c.getIssuerAddress(), c.getId());
    if (c.getStatusListIndex() == null) {
      throw new IllegalStateException(
          "Credential " + c.getId() + " chưa có status_list_index — không thu hồi được,"
              + " nên không được cấp. Xem docs/canonicalization.md §10.");
    }

    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("semester", c.getSemester());
    claims.put("activityCount", c.getActivityCount());
    claims.put("totalPoints", c.getTotalPoints());

    Map<String, Object> p = new LinkedHashMap<>();
    p.put("credentialId", c.getId());
    p.put("type", c.getType().name());
    p.put("studentCode", c.getStudentCode());
    p.put("studentName", c.getStudentName());
    p.put("issuerOrgId", c.getIssuerOrg().getId());
    p.put("issuerAddress", c.getIssuerAddress());
    p.put("issuedAt", isoSeconds(c.getIssuedAt()));
    p.put("expiresAt", isoSeconds(c.getExpiresAt()));
    p.put("statusListIndex", c.getStatusListIndex());
    p.put("claims", claims);
    p.put("nonce", "0x" + HEX.formatHex(c.getNonce()));
    return p;
  }

  /**
   * ISO-8601 UTC, <b>độ chính xác GIÂY</b>, hậu tố {@code Z}.
   *
   * <p>Giống hệt {@code AttendancePayload.isoSeconds} và vì đúng lý do đó: cột là
   * {@code DATETIME(3)}, không cắt xuống giây thì bản ghi tròn giây và bản ghi có phần lẻ ra
   * hai định dạng khác nhau trong cùng một lô. Chốt giây theo
   * {@code docs/canonicalization.md} §4 quy tắc 7.
   *
   * <p>Cố ý <b>không</b> gọi lại hàm của {@code attendance}: hai payload là hai hợp đồng độc
   * lập với verifier, và module {@code credential} không nên phụ thuộc {@code attendance}
   * chỉ để dùng chung sáu ký tự. Quy tắc chung nằm ở đặc tả, không ở lời gọi hàm.
   */
  static String isoSeconds(Instant t) {
    return t == null ? null : t.truncatedTo(ChronoUnit.SECONDS).toString();
  }

  /**
   * Địa chỉ ví: {@code 0x} + 40 hex <b>chữ thường</b>.
   *
   * <p>Kiểm ở đây dù CSDL đã có {@code ck_cred_issuer_address}: lớp này cũng chạy trên
   * credential chưa lưu (lúc cấp, để tính leaf rồi ký), khi ràng buộc CSDL chưa có tiếng nói.
   *
   * <p>Vì sao chữ thường chứ không phải EIP-55: dạng checksum trộn hoa/thường theo hash của
   * chính địa chỉ, nên hai phía chuẩn hóa khác nhau là ra hai chuỗi JCS khác nhau — cùng một
   * họ lỗi với nonce chữ hoa mà bộ vector đã chặn từ tuần 3.
   */
  static void requireLowercaseAddress(String address, Long credentialId) {
    if (address == null || !address.matches("^0x[0-9a-f]{40}$")) {
      throw new IllegalStateException(
          "Credential " + credentialId + " có issuerAddress không hợp lệ: " + address
              + ". Yêu cầu: \"0x\" + 40 hex CHỮ THƯỜNG. Dạng checksum EIP-55 bị từ chối vì"
              + " nó làm JCS ra hai chuỗi khác nhau giữa hai phía.");
    }
  }
}
