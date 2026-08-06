package vn.ptit.drl.credential;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;

import lombok.RequiredArgsConstructor;
import vn.ptit.drl.anchor.AnchorProofService;
import vn.ptit.drl.common.config.DrlProperties;
import vn.ptit.drl.common.web.BusinessException;
import vn.ptit.drl.common.web.NotFoundException;

/**
 * Dựng bundle — tệp JSON sinh viên cầm đi, xác minh được mà <b>không chạm backend</b>.
 *
 * <p>Đây là mốc của tuần 4 (PROJECT.md §6). Nửa kiểm chứng: {@code verifier/src/bundle.mjs}
 * và {@code verifier/scripts/verify-bundle.mjs}.
 *
 * <h2>Nguyên tắc chi phối toàn bộ định dạng: bundle là ĐẦU VÀO KHÔNG ĐÁNG TIN</h2>
 *
 * <p>Người cầm bundle là người có động cơ sửa nó. Vì vậy mỗi trường phải trả lời được câu
 * hỏi: <i>sửa trường này thì verifier phát hiện bằng cách nào?</i>
 *
 * <table>
 *   <tr><th>Nhóm trường</th><th>Vì sao sửa không ăn thua</th></tr>
 *   <tr><td>{@code credential.payload}</td>
 *       <td>Leaf tính lại đổi ⇒ proof không dẫn về root trên chuỗi</td></tr>
 *   <tr><td>{@code credential.signature}</td>
 *       <td>Địa chỉ phục hồi đổi ⇒ không khớp {@code issuerAddress} trong payload</td></tr>
 *   <tr><td>{@code anchor.proof}</td>
 *       <td>Không dẫn về root trên chuỗi</td></tr>
 *   <tr><td>{@code credential.leaf}, {@code anchor.merkleRoot}</td>
 *       <td><b>Dư thừa có chủ ý.</b> Verifier tính lại leaf và đọc root từ chuỗi; hai trường
 *           này chỉ để chẩn đoán, và verifier báo lỗi nếu chúng khác giá trị thật</td></tr>
 *   <tr><td>{@code anchor.batchId}</td>
 *       <td>Trỏ sang lô khác thì root khác ⇒ proof không dẫn về</td></tr>
 *   <tr><td><b>{@code chain.*}</b></td>
 *       <td><b>Đây là chỗ nguy hiểm nhất.</b> Xem bên dưới</td></tr>
 * </table>
 *
 * <h2>⚠️ Địa chỉ contract trong bundle KHÔNG được dùng để xác minh</h2>
 *
 * <p>Nếu verifier đọc {@code getRoot} từ địa chỉ contract <b>ghi trong bundle</b> thì kẻ tấn
 * công chỉ cần deploy một contract của mình, cho nó trả về root khớp cây Merkle mình tự dựng,
 * rồi ghi địa chỉ đó vào bundle. Mọi phép kiểm khác vẫn xanh và credential giả được chấp nhận.
 * <b>Đây là cách phá hệ thống rẻ nhất nếu làm sai.</b>
 *
 * <p>Vì vậy {@code chain} ở đây là <b>thông tin</b>, không phải cấu hình. Verifier giữ danh
 * sách địa chỉ tin cậy của riêng nó và <b>từ chối</b> bundle nào khai địa chỉ khác. Xem
 * {@code verifier/src/bundle.mjs}.
 *
 * <p>Địa chỉ ghi ra dạng <b>chữ thường</b> để phép so sánh ở phía verifier không phụ thuộc
 * cách viết hoa của EIP-55.
 */
@Service
@RequiredArgsConstructor
public class CredentialBundleService {

  /** Đổi giá trị này là thay đổi phá vỡ tương thích — verifier từ chối bản nó không biết. */
  public static final String FORMAT = "drl-credential-bundle";
  public static final int VERSION = 1;

  private static final HexFormat HEX = HexFormat.of();

  private final CredentialRepository credentials;
  private final AnchorProofService proofs;
  private final DrlProperties props;

  // ---------------------------------------------------------------- định dạng

  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Bundle(String format, int version, String exportedAt,
                       CredentialPart credential, AnchorPart anchor, ChainPart chain,
                       String doc) {}

  /**
   * @param payload <b>đúng chuỗi JCS đã ký</b>, nhúng nguyên văn.
   *     <p>{@link JsonRawValue} là bắt buộc: nếu để Jackson tuần tự hóa lại một {@code Map}
   *     thì thứ tự khóa và cách in số do <i>cấu hình Jackson</i> quyết định, tức là bundle
   *     mang một chuỗi khác chuỗi đã bam. Verifier vẫn xác minh được (JCS sắp xếp lại hết),
   *     nhưng ta mất tính chất kiểm được rất rẻ này: {@code JCS(JSON.parse(payload))} phải
   *     bằng <b>đúng từng byte</b> chuỗi nhận được. Verifier có chốt điều đó.
   */
  public record CredentialPart(@JsonRawValue String payload, String signature, String leaf) {}

  public record AnchorPart(String domain, long batchId, List<String> proof, String merkleRoot,
                           String txHash, Long blockNumber, String anchoredAt) {}

  /** <b>Thông tin, không phải cấu hình.</b> Xem javadoc đầu lớp. */
  public record ChainPart(long chainId, String anchorRegistry, String issuerRegistry,
                          String statusList) {}

  // ---------------------------------------------------------------- dựng

  /**
   * Dựng bundle cho một credential đã neo.
   *
   * @throws NotFoundException nếu không có credential
   * @throws BusinessException nếu credential chưa được neo, hoặc lô của nó chưa lên chuỗi
   */
  @Transactional(readOnly = true)
  public Bundle build(long credentialId) {
    Credential c = credentials.findById(credentialId)
        .orElseThrow(() -> new NotFoundException("Không thấy credential " + credentialId));

    // Bắt trôi lược đồ TRƯỚC khi xuất. Bundle mang payload đã lưu; nếu payload dựng lại từ
    // các cột không còn khớp thì lược đồ đã đổi sau khi credential này được ký, và bundle
    // xuất ra sẽ không bao giờ verify được. Vỡ ở đây, kèm thông báo nói rõ nguyên nhân, tốt
    // hơn nhiều so với để nhà tuyển dụng thấy một dấu đỏ không giải thích được.
    CredentialService.recomputeAndVerifyLeaf(c);

    Optional<AnchorProofService.Proof> maybeProof =
        proofs.findLatest("credentials", c.getId());

    if (maybeProof.isEmpty()) {
      throw new BusinessException(
          "Credential " + credentialId + " chưa được neo nên chưa xuất bundle được."
              + " Job neo chạy 02:00 hằng đêm; cần ngay thì chạy `.\\scripts\\anchor-now.ps1`.");
    }
    AnchorProofService.Proof p = maybeProof.get();

    if (!p.onChain()) {
      throw new BusinessException(
          "Credential " + credentialId + " đã vào lô " + p.batchId() + " nhưng lô đó CHƯA lên"
              + " chuỗi (tx_hash NULL). Bundle xuất bây giờ sẽ không xác minh được vì"
              + " getRoot() trả về rỗng. Chạy lại job neo để nó gửi lại chính lô này.");
    }

    // Chốt chặn cuối: lá trong anchor_leaves phải đúng là lá đã ký. Hai giá trị này đi qua
    // hai đường khác nhau (một do job neo ghi, một do lúc cấp ghi) nên chúng lệch được, và
    // nếu lệch thì bundle mang một proof cho lá KHÁC lá mà chữ ký cam kết.
    String leafDaKy = "0x" + HEX.formatHex(c.getLeafHash());
    if (!leafDaKy.equals(p.leafHash())) {
      throw new IllegalStateException(
          "Credential " + credentialId + ": lá trong anchor_leaves (" + p.leafHash()
              + ") khác lá đã ký (" + leafDaKy + "). Không xuất bundle.");
    }

    var anchor = props.anchor();

    return new Bundle(
        FORMAT,
        VERSION,
        Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
        new CredentialPart(
            c.getPayloadJson(),
            "0x" + HEX.formatHex(c.getSignature()),
            leafDaKy),
        new AnchorPart(
            p.domain(),
            p.batchId(),
            p.proof(),
            p.merkleRoot(),
            p.txHash(),
            p.blockNumber(),
            p.anchoredAt() == null ? null
                : p.anchoredAt().truncatedTo(ChronoUnit.SECONDS).toString()),
        new ChainPart(
            anchor.chainId(),
            lower(anchor.anchorRegistryAddress()),
            lower(anchor.issuerRegistryAddress()),
            lower(anchor.statusListAddress())),
        "Xac minh doc lap: cd verifier && node scripts/verify-bundle.mjs <tep-nay>."
            + " Dia chi contract trong muc `chain` la THONG TIN, verifier dung danh sach"
            + " tin cay cua rieng no.");
  }

  private static String lower(String s) {
    return s == null ? null : s.toLowerCase(Locale.ROOT);
  }
}
