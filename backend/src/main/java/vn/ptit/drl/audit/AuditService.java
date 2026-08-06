package vn.ptit.drl.audit;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.ptit.drl.anchor.LeafHasher;

/**
 * Ghi nhật ký có chuỗi băm, và kiểm tra tính toàn vẹn của chuỗi đó.
 *
 * <p>Hiện thực <b>luận điểm 1</b> (PROJECT.md §10): không ngăn được quản trị viên sửa dữ liệu
 * quá khứ, nhưng chứng minh được là họ đã sửa.
 *
 * <h2>Ghi nối tiếp — vì sao {@code synchronized} và vì sao thế là đủ</h2>
 *
 * <p>Mắt xích của bản ghi mới phụ thuộc mắt xích của bản ghi <b>cuối cùng</b>. Hai luồng cùng
 * đọc "bản ghi cuối" rồi cùng ghi sẽ tạo ra hai bản ghi trỏ về cùng một cha — chuỗi biến thành
 * <b>cái cây</b>, và kẻ tấn công xóa được một nhánh mà nhánh còn lại vẫn liền xích. Mất đúng
 * thứ cơ chế này bảo vệ.
 *
 * <p><b>{@code synchronized} thu hẹp cửa sổ đua nhưng KHÔNG đóng được nó</b> — nói khác đi là
 * nói quá. Phương thức này chạy với {@code Propagation.REQUIRED}, tức là nó tham gia giao
 * dịch <i>của bên gọi</i>, và giao dịch đó <b>commit sau khi khóa đã được nhả</b>. Nên vẫn có
 * đường: luồng A đọc bản ghi cuối, chèn (chưa commit), nhả khóa; luồng B vào, đọc <i>vẫn</i>
 * bản ghi cũ, và hai bản ghi cùng trỏ về một cha.
 *
 * <p><b>Thứ thật sự bảo đảm là ràng buộc CSDL</b> {@code uk_audit_prev_hash} (V7): bản ghi
 * thứ hai vi phạm UNIQUE và giao dịch của nó cuộn lại. Cách hỏng vì thế là <b>một thao tác
 * nghiệp vụ thất bại ồn ào và người dùng thử lại</b>, chứ không phải một chuỗi hỏng âm thầm.
 * Đó là đánh đổi đúng.
 *
 * <p>Hệ chạy <b>một instance</b> (PROJECT.md §4: không Redis, không ShedLock) nên xác suất
 * chạm vào đường này rất nhỏ; {@code synchronized} vẫn giữ vì nó rẻ và làm nó nhỏ hơn nữa.
 *
 * <p><b>Khiếm khuyết đã biết:</b> MySQL cho phép nhiều giá trị NULL trong một UNIQUE, nên
 * ràng buộc đó <b>không</b> chặn được hai bản ghi <i>đầu tiên</i> cùng lúc — chỉ xảy ra khi
 * nhật ký còn rỗng. Ghi ra đây thay vì giả vờ là không có.
 *
 * <h2>Ghi nhật ký KHÔNG được làm hỏng nghiệp vụ</h2>
 *
 * <p>{@link #record} chạy trong giao dịch <b>của bên gọi</b> ({@code REQUIRED}) — cố ý. Nếu
 * thao tác nghiệp vụ cuộn lại thì bản ghi nhật ký cũng phải cuộn theo, nếu không chuỗi sẽ có
 * một mắt xích nói về một sự kiện chưa từng xảy ra.
 *
 * <p>Đánh đổi: một lỗi trong lúc ghi nhật ký sẽ làm hỏng cả thao tác nghiệp vụ. Chấp nhận có
 * ý thức — hệ thống mà "ghi log thất bại thì thôi bỏ qua" thì nhật ký của nó không dùng làm
 * bằng chứng được.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

  private static final HexFormat HEX = HexFormat.of();

  private final AuditLogRepository repository;

  /** Kết quả kiểm tra chuỗi. */
  public record ChainCheck(long soBanGhi, boolean nguyenVen, List<String> loi) {

    public static ChainCheck ok(long n) {
      return new ChainCheck(n, true, List.of());
    }
  }

  // ------------------------------------------------------------------ ghi

  /**
   * Ghi một mắt xích mới.
   *
   * @param actorId {@code users.id}, hoặc {@code null} khi hệ thống tự làm
   * @param beforeJson trạng thái trước dạng JSON <b>nguyên văn</b>, {@code null} nếu tạo mới
   * @param afterJson trạng thái sau, {@code null} nếu xóa
   */
  @Transactional(propagation = Propagation.REQUIRED)
  public synchronized AuditLog record(String action, String entity, Long entityId,
                                      Long actorId, String beforeJson, String afterJson) {

    byte[] prevHash = repository.findFirstByOrderByIdDesc()
        .map(AuditLog::getHash)
        .orElse(null);

    AuditLog e = AuditLog.builder()
        .actorId(actorId)
        .action(action)
        .entity(entity)
        .entityId(entityId)
        .beforeJson(beforeJson)
        .afterJson(afterJson)
        .prevHash(prevHash)
        .nonce(HEX.parseHex(LeafHasher.newNonce().substring(2)))
        // Chỗ giữ chỗ: `hash` là NOT NULL và mắt xích cần `createdAt`, mà `createdAt` do
        // @CreationTimestamp điền lúc lưu. Nên phải lưu một lần để lấy nó, rồi mới băm được.
        // Cùng lối với CredentialService.persistWithProof.
        .hash(new byte[32])
        .build();

    AuditLog saved = repository.saveAndFlush(e);

    byte[] hash = AuditHasher.chainHash(saved);
    if (repository.updateHash(saved.getId(), hash, new byte[32]) != 1) {
      throw new IllegalStateException(
          "Ghi mắt xích cho bản ghi nhật ký " + saved.getId() + " chạm khác 1 dòng."
              + " Bản ghi này đã có hash — nhật ký chỉ ghi thêm, không ghi lại.");
    }
    saved.setHash(hash);

    log.debug("Nhat ky #{} · {} {} · hash {}", saved.getId(), action, entity, hex(hash));
    return saved;
  }

  // ------------------------------------------------------------------ kiểm tra

  /**
   * Kiểm toàn bộ chuỗi từ đầu.
   *
   * <p>Ba thứ được kiểm ở mỗi mắt xích, và mỗi thứ bắt một kiểu tấn công khác nhau:
   *
   * <ol>
   *   <li><b>{@code hash} tính lại có khớp không</b> — bắt việc <b>sửa nội dung</b> một bản
   *       ghi mà quên tính lại mắt xích.
   *   <li><b>{@code prevHash} có bằng {@code hash} của bản ghi liền trước không</b> — bắt việc
   *       <b>chèn</b> hoặc <b>xóa</b> bản ghi giữa chuỗi.
   *   <li><b>Chỉ bản ghi đầu tiên được có {@code prevHash} NULL</b> — bắt việc cắt chuỗi làm
   *       đôi rồi bắt đầu lại từ giữa.
   * </ol>
   *
   * <p><b>Giới hạn phải nói rõ khi bảo vệ:</b> phép kiểm này một mình <b>không</b> chống được
   * quản trị viên có toàn quyền CSDL — họ sửa một bản ghi rồi tính lại toàn bộ chuỗi từ điểm
   * đó về sau, và mọi thứ ở đây lại xanh. Thứ chặn việc đó là <b>neo định kỳ</b>: root đã lên
   * chuỗi công khai thì không tính lại được. Chuỗi băm làm việc sửa trở nên <i>tốn kém</i>;
   * việc neo làm nó <i>bất khả thi</i> với khoảng thời gian đã neo.
   */
  @Transactional(readOnly = true)
  public ChainCheck verifyChain() {
    List<AuditLog> chain = repository.findAllByOrderByIdAsc();
    if (chain.isEmpty()) {
      return ChainCheck.ok(0);
    }

    List<String> loi = new ArrayList<>();
    byte[] mongDoiPrev = null;

    for (int i = 0; i < chain.size(); i++) {
      AuditLog e = chain.get(i);

      if (i == 0) {
        if (e.getPrevHash() != null) {
          loi.add("Bản ghi đầu tiên (#" + e.getId() + ") có prevHash khác NULL —"
              + " có bản ghi nào đó trước nó đã bị xóa.");
        }
      } else if (!java.util.Arrays.equals(e.getPrevHash(), mongDoiPrev)) {
        loi.add("ĐỨT XÍCH tại #" + e.getId() + ": prevHash = " + hex(e.getPrevHash())
            + " nhưng bản ghi liền trước (#" + chain.get(i - 1).getId() + ") có hash = "
            + hex(mongDoiPrev) + ". Có bản ghi bị chèn vào, bị xóa, hoặc bị sửa.");
      }

      byte[] tinhLai;
      try {
        tinhLai = AuditHasher.chainHash(e);
      } catch (RuntimeException ex) {
        loi.add("Không tính lại được hash của #" + e.getId() + ": " + ex.getMessage());
        mongDoiPrev = e.getHash();
        continue;
      }

      if (!java.util.Arrays.equals(tinhLai, e.getHash())) {
        loi.add("NỘI DUNG BỊ SỬA tại #" + e.getId() + ": hash lưu = " + hex(e.getHash())
            + " nhưng tính lại ra " + hex(tinhLai) + ".");
      }

      mongDoiPrev = e.getHash();
    }

    if (!loi.isEmpty()) {
      log.error("Chuoi bam nhat ky KHONG NGUYEN VEN: {} loi tren {} ban ghi",
          loi.size(), chain.size());
    }
    return new ChainCheck(chain.size(), loi.isEmpty(), List.copyOf(loi));
  }

  /** Mắt xích cuối cùng — tiện cho việc đối chiếu nhanh với bản in ra ngoài. */
  @Transactional(readOnly = true)
  public Optional<String> lastHash() {
    return repository.findFirstByOrderByIdDesc().map(e -> hex(e.getHash()));
  }

  private static String hex(byte[] b) {
    return b == null ? "NULL" : "0x" + HEX.formatHex(b);
  }
}
