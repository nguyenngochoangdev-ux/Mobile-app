package vn.ptit.drl.credential;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.ptit.drl.anchor.AnchorDomain;
import vn.ptit.drl.anchor.Jcs;
import vn.ptit.drl.anchor.LeafHasher;
import vn.ptit.drl.identity.Student;
import vn.ptit.drl.org.Organization;

/**
 * Cấp credential: chụp ảnh dữ liệu, dựng payload, tính leaf, ký, lưu.
 *
 * <h2>Thứ tự sáu bước — và vì sao không đổi được</h2>
 *
 * <ol>
 *   <li><b>Chụp ảnh</b> MSSV, họ tên, địa chỉ ví issuer. Từ đây trở đi không đọc gì qua khóa
 *       ngoại nữa — xem {@link Credential}.
 *   <li><b>Sinh nonce</b> 16 byte. Thiếu nó thì {@link LeafHasher} từ chối hash (PROJECT.md
 *       §2.3).
 *   <li><b>Cấp {@code statusListIndex}</b> ngẫu nhiên. Phải có <b>trước</b> khi dựng payload,
 *       vì nó nằm <i>trong</i> payload được neo — để ngoài thì người cầm credential đã bị thu
 *       hồi chỉ cần đổi con số đó sang một bit chưa bật.
 *   <li><b>Dựng payload + tính leaf.</b>
 *   <li><b>Ký leaf.</b>
 *   <li><b>Lưu.</b>
 * </ol>
 *
 * <p>Bước 3 trước bước 4 là ràng buộc thật, không phải sở thích. Nó kéo theo một hệ quả khó
 * chịu: {@code credentialId} cũng nằm trong payload, mà id thì do CSDL sinh lúc
 * {@code INSERT} — tức là <b>sau</b> bước 6. Cách xử lý ở {@link #issue}.
 *
 * <h2>Không có luồng "sửa credential"</h2>
 *
 * <p>Cố ý không viết. Credential là phát biểu đã ký và (sắp) đã neo; sửa nó làm leaf tính lại
 * ra giá trị khác và mọi proof fail vĩnh viễn. Muốn đổi nội dung thì <b>thu hồi rồi cấp
 * lại</b> — đúng mô hình W3C Verifiable Credentials, và đúng thứ luận điểm "chống sửa hồi tố"
 * hứa hẹn. Entity đã chặn ở tầng Hibernate bằng {@code updatable = false}; lớp này chỉ đơn
 * giản là không cung cấp cửa nào.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CredentialService {

  /** Số lần cấp lại khi bốc trúng {@code status_list_index} đã có người khác dùng. */
  private static final int MAX_INDEX_RETRY = 5;

  private static final HexFormat HEX = HexFormat.of();

  private final CredentialRepository repository;
  private final StatusListIndexService indexService;
  private final ObjectProvider<IssuerSigner> signerProvider;
  private final vn.ptit.drl.audit.AuditService audit;

  /** Nội dung cần cấp — phần thay đổi theo từng credential. */
  public record Request(Student student, Organization issuerOrg, String semester,
                        int activityCount, int totalPoints, Instant expiresAt) {}

  /**
   * Cấp một credential mới.
   *
   * <h3>Cái vòng luẩn quẩn của {@code credentialId}, và cách gỡ</h3>
   *
   * <p>{@code credentialId} nằm trong payload, nên phải biết id trước khi tính leaf. Nhưng id
   * do {@code AUTO_INCREMENT} sinh lúc {@code INSERT}, tức là sau. Ba cách gỡ, chọn cách thứ
   * ba:
   *
   * <ol>
   *   <li><i>Bỏ {@code credentialId} khỏi payload.</i> Từ chối: hai credential cùng nội dung
   *       cấp cho cùng sinh viên sẽ ra <b>cùng một leaf</b>, mà {@code MerkleService} từ chối
   *       lá trùng (docs/canonicalization.md §8.3) — lô sẽ vỡ. Nonce riêng cũng tách được
   *       chúng, nhưng khi đó bundle không nói được nó là credential nào.
   *   <li><i>Sinh id ở tầng ứng dụng</i> (UUID hoặc sequence). Từ chối: đổi kiểu khóa chính
   *       của một bảng đã có trong V1, cho một lợi ích không đáng.
   *   <li><b>Lưu hai lần trong CÙNG một giao dịch:</b> lưu lần đầu để lấy id, rồi dựng
   *       payload, ký, và lưu lại. Nếu bước ký hỏng thì giao dịch cuộn lại, không để lại
   *       credential dở dang.
   * </ol>
   *
   * <p>Cách 3 đòi lần lưu đầu tiên phải ghi được giá trị tạm cho các cột {@code NOT NULL} —
   * đó là lý do {@code leafHash} và {@code signature} được điền chỗ giữ trước rồi ghi đè.
   * Ghi đè ở đây <b>không</b> vi phạm {@code updatable = false}: Hibernate bỏ qua các cột đó
   * khi sinh {@code UPDATE}, nên lần lưu thứ hai đi qua {@code EntityManager.merge} sẽ không
   * ghi chúng. Xem {@link #persistWithProof}.
   *
   * @throws IllegalStateException nếu chưa cấu hình khóa ký
   */
  @Transactional
  public Credential issue(Request req) {
    IssuerSigner signer = requireSigner();

    Credential c = Credential.builder()
        .student(req.student())
        .studentCode(req.student().getMssv())
        .studentName(req.student().getFullName())
        .issuerOrg(req.issuerOrg())
        .issuerAddress(signer.address())
        .type(CredentialType.HOAT_DONG)
        .semester(req.semester())
        .activityCount(req.activityCount())
        .totalPoints(req.totalPoints())
        // Cắt xuống giây NGAY tại đây, không đợi lúc dựng payload. Cột là DATETIME(3), nên
        // nếu để phần mili giây sống trong CSDL thì giá trị đọc ra khác giá trị đã hash —
        // và phần chênh chỉ lộ ra khi có ai đó so hai thứ, tức là gần như không bao giờ.
        .issuedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS))
        .expiresAt(req.expiresAt() == null ? null
            : req.expiresAt().truncatedTo(ChronoUnit.SECONDS))
        .nonce(HEX.parseHex(LeafHasher.newNonce().substring(2)))
        .build();

    return allocateIndexAndPersist(c, signer);
  }

  /**
   * Cấp {@code status_list_index} rồi lưu, thử lại khi đụng {@code uk_cred_status_index}.
   *
   * <p>{@code StatusListIndexService.allocate()} trả về chỉ số còn trống <b>tại thời điểm
   * hỏi</b>; ràng buộc UNIQUE mới là trọng tài cuối cùng (docs/canonicalization.md §10.5).
   * Hệ chạy một instance nên xác suất đụng rất nhỏ — nhưng "rất nhỏ" không phải "không có",
   * và cách hỏng của nó là một credential không cấp được ngay giữa buổi chấm cuối kỳ.
   */
  private Credential allocateIndexAndPersist(Credential c, IssuerSigner signer) {
    for (int attempt = 1; attempt <= MAX_INDEX_RETRY; attempt++) {
      c.setStatusListIndex(indexService.allocate());
      try {
        return persistWithProof(c, signer);
      } catch (DataIntegrityViolationException e) {
        if (attempt == MAX_INDEX_RETRY) {
          throw new IllegalStateException(
              "Không cấp được status_list_index sau " + MAX_INDEX_RETRY + " lần thử."
                  + " Pool có thể đã gần đầy — xem log cảnh báo của StatusListIndexService"
                  + " và tăng drl.credential.status-list-pool-size."
                  + " KHÔNG chuyển sang cấp tuần tự (PROJECT.md §2.3).", e);
        }
        log.warn("status_list_index {} da co nguoi dung, boc lai (lan {})",
            c.getStatusListIndex(), attempt);
      }
    }
    throw new IllegalStateException("Không tới được đây");
  }

  /**
   * Lưu lần đầu để lấy id, dựng payload, ký, rồi lưu lại.
   *
   * <p>Chỗ giữ chỗ cho {@code leafHash} và {@code signature} là 32 và 65 byte {@code 0x00} —
   * chọn đúng độ dài thật để nếu vì lý do gì đó chúng lọt xuống CSDL thì cũng không phá kiểu
   * cột, và đủ dị thường (toàn byte 0) để nhận ra ngay khi đọc.
   */
  private Credential persistWithProof(Credential c, IssuerSigner signer) {
    c.setLeafHash(new byte[32]);
    c.setSignature(new byte[IssuerSigner.SIGNATURE_BYTES]);
    c.setPayloadJson("{}");

    Credential saved = repository.saveAndFlush(c);

    Map<String, Object> payload = CredentialPayload.of(saved);
    String json = Jcs.canonicalize(payload);
    byte[] leaf = LeafHasher.leaf(AnchorDomain.CRED, payload);
    byte[] sig = signer.sign(leaf);

    // Kiểm ngay tại chỗ thay vì tin. Chi phí là một phép phục hồi khóa (~1 ms) mỗi lần cấp;
    // đổi lại, một credential ký sai KHÔNG BAO GIỜ ra khỏi hàm này. Nếu để lọt, chỗ phát
    // hiện tiếp theo là nhà tuyển dụng bấm "xác minh" và thấy đỏ — muộn nhất có thể.
    String recovered = IssuerSigner.recoverAddress(leaf, sig);
    if (!recovered.equals(saved.getIssuerAddress())) {
      throw new IllegalStateException(
          "Chữ ký vừa tạo phục hồi ra " + recovered + " nhưng payload ghi "
              + saved.getIssuerAddress() + ". Không cấp credential này.");
    }

    saved.setPayloadJson(json);
    saved.setLeafHash(leaf);
    saved.setSignature(sig);

    // Ghi nhật ký TRONG CÙNG giao dịch: nếu việc cấp cuộn lại thì mắt xích cũng phải cuộn
    // theo, nếu không chuỗi sẽ có một bản ghi nói về một credential chưa từng tồn tại.
    //
    // `after` là chính chuỗi JCS đã ký — không dựng lại một bản tóm tắt khác. Nhật ký nói
    // ĐÚNG thứ đã được ký, không phải một cách diễn đạt gần đúng của nó.
    audit.record("CREDENTIAL_ISSUE", "credentials", saved.getId(), null, null, json);

    // Ba cột này khai updatable = false nên Hibernate KHÔNG sinh UPDATE cho chúng. Phải ghi
    // bằng câu lệnh tường minh. Đây là cái giá của việc chốt cứng tính bất biến ở tầng
    // entity, và là cái giá đáng trả: mọi đường sửa credential khác đều bị chặn.
    repository.saveProof(saved.getId(), json, leaf, sig);

    log.info("Cap credential {} cho {} · hoc ky {} · leaf {} · statusIndex {}",
        saved.getId(), saved.getStudentCode(), saved.getSemester(),
        "0x" + HEX.formatHex(leaf), saved.getStatusListIndex());

    return saved;
  }

  /**
   * Tính lại leaf từ các cột đã lưu và đối chiếu với {@code payload_json} + {@code leaf_hash}.
   *
   * <p>Dùng ở hai chỗ: {@link CredentialAnchorSource} gọi trước khi neo, và test gọi để chốt
   * bất biến. Nó bắt đúng một lớp lỗi — <b>trôi lược đồ</b>: ai đó đổi
   * {@link CredentialPayload} mà quên rằng có credential đã ký theo lược đồ cũ.
   *
   * @throws IllegalStateException nếu lệch
   */
  public static byte[] recomputeAndVerifyLeaf(Credential c) {
    Map<String, Object> payload = CredentialPayload.of(c);
    String json = Jcs.canonicalize(payload);

    if (!json.equals(c.getPayloadJson())) {
      throw new IllegalStateException(
          "Credential " + c.getId() + ": payload dựng lại KHÁC payload_json đã lưu."
              + " Lược đồ CredentialPayload đã đổi sau khi credential này được ký."
              + " Chữ ký và mọi proof của nó không còn tái tạo được.\n"
              + "  đã lưu:   " + c.getPayloadJson() + "\n"
              + "  dựng lại: " + json);
    }

    byte[] leaf = LeafHasher.leaf(AnchorDomain.CRED, payload);
    if (!java.util.Arrays.equals(leaf, c.getLeafHash())) {
      throw new IllegalStateException(
          "Credential " + c.getId() + ": leaf tính lại KHÁC leaf_hash đã lưu."
              + " Công thức leaf hash đã đổi sau khi credential này được ký.");
    }
    return leaf;
  }

  /** Địa chỉ ví đang dùng để ký — {@code 0x} + 40 hex chữ thường. */
  public String issuerAddress() {
    return requireSigner().address();
  }

  private IssuerSigner requireSigner() {
    IssuerSigner signer = signerProvider.getIfAvailable();
    if (signer == null) {
      throw new IllegalStateException(
          "Chưa cấu hình khóa ký credential. Điền ISSUER_PRIVATE_KEY trong .env."
              + " Khóa này NÊN KHÁC ANCHOR_PRIVATE_KEY.");
    }
    return signer;
  }
}
