package vn.ptit.drl.credential;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.ptit.drl.common.web.BusinessException;
import vn.ptit.drl.common.web.NotFoundException;

/**
 * Thu hồi credential: lật bit trên {@code StatusList}, rồi mới ghi CSDL.
 *
 * <h2>⚠️ Thứ tự NGƯỢC với job neo — và đó là chủ ý</h2>
 *
 * <p>{@link vn.ptit.drl.anchor.AnchorJob} ghi CSDL <b>trước</b>, gửi giao dịch <b>sau</b>.
 * Ở đây <b>ngược lại</b>: gửi giao dịch trước, ghi CSDL sau.
 *
 * <blockquote>
 * <b>Đính chính một chú thích sai.</b> Migration {@code V4} viết <i>"Thu tu ghi giong het job
 * neo (AnchorJob javadoc): gui giao dich TRUOC, ghi revoked_at SAU"</i>. Vế sau đúng, vế
 * "giống hệt job neo" <b>sai</b> — job neo làm ngược lại. Không sửa được V4 vì migration đã
 * chạy và Flyway bam checksum toàn bộ nội dung file (xem đầu file {@code V5}); đính chính
 * nằm ở đây, và bảng bên dưới là lời giải thích đầy đủ.
 * </blockquote>
 *
 * <p>Lý do hai nơi chọn hai thứ tự khác nhau nằm ở <b>cách hỏng nào sửa được</b>:
 *
 * <table>
 *   <tr><th></th><th>Job neo</th><th>Thu hồi</th></tr>
 *   <tr><td>Nguồn sự thật</td><td>CSDL (giữ lá và proof)</td><td><b>Chuỗi</b> (verifier chỉ đọc bit)</td></tr>
 *   <tr><td>Ghi CSDL trước rồi tx hỏng</td>
 *       <td>Lô nằm lại với {@code tx_hash} NULL, lần sau neo lại <b>chính nó</b> — sửa được</td>
 *       <td><b>CSDL bảo đã thu hồi, chuỗi thì chưa.</b> Nhà tuyển dụng chạy verifier và thấy
 *           credential CÒN HIỆU LỰC. Hỏng im lặng, và hỏng đúng chỗ quan trọng nhất</td></tr>
 *   <tr><td>Gửi tx trước rồi ghi CSDL hỏng</td>
 *       <td>Giao dịch đã lên chuỗi mà CSDL không biết ⇒ lần sau neo lô thứ hai cho cùng dữ
 *           liệu, để lại hai root</td>
 *       <td><b>Chuỗi đã thu hồi, CSDL chưa biết.</b> Verifier vẫn báo ĐÃ THU HỒI — đúng.
 *           Chỉ trang quản trị hiển thị lệch, và {@link #reconcile} sửa được</td></tr>
 * </table>
 *
 * <p>Nói gọn: <b>đặt trạng thái quyền lực nhất ở nơi người ngoài đọc.</b> Với thu hồi, nơi đó
 * là chuỗi, nên chuỗi phải đi trước.
 *
 * <h2>Không có "thu hồi cục bộ"</h2>
 *
 * <p>Cố ý không cho thu hồi khi chuỗi đang tắt. Một chế độ như vậy sẽ ghi {@code revoked_at}
 * mà không lật bit, và mọi verifier bên ngoài vẫn thấy credential hợp lệ — tức là biến thu
 * hồi thành một dòng ghi chú nội bộ trong khi giao diện quản trị nói "đã thu hồi".
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CredentialRevocationService {

  private final CredentialRepository credentials;
  private final ObjectProvider<StatusListClient> clientProvider;
  private final JdbcTemplate jdbc;
  private final vn.ptit.drl.audit.AuditService audit;

  /** Kết quả một lần thu hồi, để log và để test đối chiếu. */
  public record Result(long credentialId, long statusListIndex, boolean revoked,
                       String txHash, long gasUsed, boolean daDungTruocDo) {}

  /**
   * Thu hồi (hoặc bỏ thu hồi) một credential.
   *
   * @param revoked {@code true} để thu hồi, {@code false} để bỏ thu hồi
   * @param actorId {@code users.id} của cán bộ bấm nút, {@code null} nếu hệ thống tự làm.
   *     Bắt buộc truyền vào chứ không đọc từ {@code SecurityContext}: lớp này cũng chạy từ
   *     runner và từ test, nơi không có context nào — và một nhật ký ghi "không rõ ai" cho
   *     một hành động của người thật thì không dùng làm bằng chứng được.
   * @throws NotFoundException nếu không có credential
   * @throws BusinessException nếu chuỗi đang tắt, hoặc trạng thái đã đúng như yêu cầu
   */
  public Result setRevoked(long credentialId, boolean revoked, String lyDo, Long actorId) {
    StatusListClient client = requireClient();

    Credential c = credentials.findById(credentialId)
        .orElseThrow(() -> new NotFoundException("Không thấy credential " + credentialId));

    long index = c.getStatusListIndex();

    // Đọc trạng thái THẬT trên chuỗi, không tin cột revoked_at. Hai lý do: cột có thể lệch
    // (xem javadoc đầu lớp), và gọi lại setRevoked trên trạng thái không đổi vẫn tốn một
    // giao dịch dù contract không ghi gì.
    boolean truocDo;
    try {
      truocDo = client.isRevoked(index);
    } catch (Exception e) {
      throw new BusinessException(
          "Không đọc được trạng thái thu hồi từ chuỗi: " + e.getMessage()
              + ". Không gửi giao dịch khi chưa biết trạng thái hiện tại.");
    }

    if (truocDo == revoked) {
      // Đồng bộ lại CSDL cho khớp chuỗi rồi thôi — đây chính là trường hợp "chuỗi đã đổi,
      // CSDL chưa biết" mà javadoc đầu lớp nói tới.
      ghiCsdl(c, revoked, null);
      log.info("Credential {} da o trang thai revoked={} tren chuoi. Chi dong bo CSDL.",
          credentialId, revoked);
      return new Result(credentialId, index, revoked, null, 0L, true);
    }

    log.warn("Gui giao dich {} credential {} · statusIndex {} · ly do: {}",
        revoked ? "THU HOI" : "BO THU HOI", credentialId, index,
        lyDo == null || lyDo.isBlank() ? "(khong ghi)" : lyDo);

    TransactionReceipt receipt;
    try {
      receipt = client.setRevoked(index, revoked);
    } catch (Exception e) {
      throw new BusinessException(
          "Thu hồi credential " + credentialId + " thất bại ở bước gửi giao dịch: "
              + e.getMessage() + ". CSDL KHÔNG bị thay đổi — trạng thái trên chuỗi vẫn là"
              + " nguồn sự thật, và nó chưa đổi.");
    }

    // Xác nhận bằng eth_call thay vì tin biên nhận. Biên nhận status = 1 nói giao dịch không
    // revert, không nói bit đã đúng giá trị mong muốn.
    try {
      boolean sau = client.isRevoked(index);
      if (sau != revoked) {
        throw new IllegalStateException(
            "Giao dịch " + receipt.getTransactionHash() + " thành công nhưng isRevoked("
                + index + ") vẫn trả về " + sau + ". KHÔNG ghi CSDL.");
      }
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      log.warn("Khong doc lai duoc trang thai sau khi ghi ({}). Van ghi CSDL vi giao dich"
          + " da thanh cong.", e.getMessage());
    }

    ghiCsdl(c, revoked, receipt.getTransactionHash());

    // Ghi nhật ký SAU khi cả chuỗi lẫn CSDL đã xong. Ghi trước là ghi một sự kiện có thể
    // chưa xảy ra; và vì nhật ký chỉ ghi thêm, không rút lại được.
    audit.record(revoked ? "CREDENTIAL_REVOKE" : "CREDENTIAL_UNREVOKE", "credentials",
        credentialId, actorId,
        vn.ptit.drl.audit.AuditJson.of("revoked", truocDo),
        vn.ptit.drl.audit.AuditJson.of(
            "revoked", revoked,
            "txHash", receipt.getTransactionHash(),
            "statusListIndex", index,
            "lyDo", lyDo));

    log.info("Credential {} · revoked={} · tx {} · gas {}",
        credentialId, revoked, receipt.getTransactionHash(), receipt.getGasUsed());
    log.info("   https://amoy.polygonscan.com/tx/{}", receipt.getTransactionHash());

    return new Result(credentialId, index, revoked, receipt.getTransactionHash(),
        receipt.getGasUsed().longValueExact(), false);
  }

  /**
   * Đồng bộ {@code revoked_at} của một credential theo trạng thái THẬT trên chuỗi.
   *
   * <p>Dùng khi bước ghi CSDL hỏng sau khi giao dịch đã lên chuỗi — cách hỏng mà thứ tự
   * "chuỗi trước" cố ý chọn vì nó sửa được. Không gửi giao dịch nào.
   *
   * @return trạng thái đọc từ chuỗi
   */
  public boolean reconcile(long credentialId) {
    StatusListClient client = requireClient();
    Credential c = credentials.findById(credentialId)
        .orElseThrow(() -> new NotFoundException("Không thấy credential " + credentialId));

    boolean trenChuoi;
    try {
      trenChuoi = client.isRevoked(c.getStatusListIndex());
    } catch (Exception e) {
      throw new BusinessException("Không đọc được chuỗi: " + e.getMessage());
    }

    // Đọc trạng thái CSDL bằng truy vấn, KHÔNG qua `c.getRevokedAt()`.
    //
    // Entity có thể đang mang giá trị cũ trong persistence context — và trớ trêu là chính
    // hàm này tồn tại để xử lý lúc CSDL và thực tế lệch nhau, nên tin vào một bản sao trong
    // bộ nhớ là mâu thuẫn với lý do nó tồn tại. Cùng họ bẫy với việc test đọc lại entity mà
    // quên `entityManager.clear()` (xem docs/canonicalization.md §13.4).
    Boolean daGhi = jdbc.queryForObject(
        "SELECT revoked_at IS NOT NULL FROM credentials WHERE id = ?", Boolean.class, credentialId);
    boolean trongCsdl = Boolean.TRUE.equals(daGhi);

    if (trenChuoi != trongCsdl) {
      log.warn("Credential {} LECH: chuoi = {}, CSDL = {}. Lay theo CHUOI.",
          credentialId, trenChuoi, trongCsdl);
    }

    // Ghi VÔ ĐIỀU KIỆN, không chỉ khi lệch. Rẻ, idempotent, và không phụ thuộc vào việc đọc
    // đúng trạng thái hiện tại — nếu phép so ở trên sai vì lý do nào đó thì kết quả vẫn đúng.
    ghiCsdl(c, trenChuoi, trenChuoi ? c.getRevokeTxHash() : null);

    return trenChuoi;
  }

  /**
   * Ghi hai cột thu hồi.
   *
   * <p>Viết tay bằng {@link JdbcTemplate} chứ không qua Hibernate: {@code revokedAt} và
   * {@code revokeTxHash} là hai trong ba cột <b>duy nhất</b> của {@link Credential} được phép
   * sửa, còn lại đều {@code updatable = false}. Ghi qua entity thì một lần
   * {@code saveAndFlush} lỡ tay sẽ đi qua toàn bộ bản ghi — không hỏng gì vì Hibernate bỏ qua
   * các cột kia, nhưng đường đi thì mập mờ. Câu lệnh tường minh nói rõ chỉ hai cột này đổi.
   */
  @Transactional
  protected void ghiCsdl(Credential c, boolean revoked, String txHash) {
    jdbc.update(
        "UPDATE credentials SET revoked_at = ?, revoke_tx_hash = ? WHERE id = ?",
        revoked ? java.sql.Timestamp.from(Instant.now().truncatedTo(ChronoUnit.SECONDS)) : null,
        revoked ? txHash : null,
        c.getId());

    c.setRevokedAt(revoked ? Instant.now().truncatedTo(ChronoUnit.SECONDS) : null);
    c.setRevokeTxHash(revoked ? txHash : null);
  }

  private StatusListClient requireClient() {
    StatusListClient client = clientProvider.getIfAvailable();
    if (client == null) {
      throw new BusinessException(
          "Chuỗi đang tắt nên KHÔNG thu hồi được. Đặt ANCHOR_ENABLED=true và điền"
              + " STATUS_LIST_ADDRESS trong .env."
              + " Cố ý không có chế độ 'thu hồi cục bộ': ghi revoked_at mà không lật bit trên"
              + " chuỗi làm mọi verifier bên ngoài vẫn thấy credential hợp lệ.");
    }
    return client;
  }
}
