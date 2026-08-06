package vn.ptit.drl.credential;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.drl.identity.Student;
import vn.ptit.drl.org.Organization;

/**
 * Một credential đã cấp cho sinh viên.
 *
 * <h2>Vì sao gần như mọi cột đều {@code updatable = false}</h2>
 *
 * <p>Credential là một <b>phát biểu đã ký và đã neo</b>. Mọi trường đi vào payload
 * ({@link CredentialPayload}) mà bị sửa sau khi neo sẽ làm leaf hash tính lại ra giá trị
 * khác, tức là <b>mọi Merkle proof của bản ghi này fail vĩnh viễn</b> — và fail im lặng,
 * vì hash vẫn tính ra bình thường, chỉ là không khớp root đã nằm trên chuỗi.
 * {@code AnchorRegistry} cố ý không cho ghi đè nên không có đường sửa.
 *
 * <p>{@code updatable = false} biến chuyện đó thành lỗi ở tầng Hibernate thay vì một
 * {@code UPDATE} lặng lẽ đi qua. Muốn sửa nội dung thì <b>thu hồi rồi cấp lại</b> — đúng mô
 * hình của W3C Verifiable Credentials, và đúng thứ luận điểm "chống sửa hồi tố"
 * (PROJECT.md §10) hứa hẹn.
 *
 * <p>Ba cột <b>được</b> phép sửa, và chỉ ba: {@code leafHash} (job neo điền sau khi giao dịch
 * lên chuỗi), {@code revokedAt} và {@code revokeTxHash} (thu hồi). Không cột nào trong ba cột
 * đó nằm trong payload.
 *
 * <h2>Chụp ảnh thay vì đọc qua khóa ngoại</h2>
 *
 * <p>{@code studentCode}, {@code studentName}, {@code issuerAddress} là <b>bản sao</b> của
 * giá trị tại thời điểm cấp, không phải thứ đọc qua {@link #student} hay {@link #issuerOrg}.
 * Lý do đầy đủ ở đầu file {@code V4__credential_snapshot_va_chu_ky.sql}: đổi tên một sinh
 * viên không được phép làm hỏng credential đã cấp cho họ.
 *
 * <p>Quan hệ {@link #student} và {@link #issuerOrg} vẫn giữ — để truy vấn và để ràng buộc
 * khóa ngoại, <b>không</b> để dựng payload.
 */
@Entity
@Table(name = "credentials")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Credential {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // ---------------------------------------------------------------- chủ thể

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "student_id", nullable = false, updatable = false)
  private Student student;

  /** CHỤP ẢNH từ {@code students.mssv}. Đừng thay bằng {@code student.getMssv()}. */
  @Column(name = "student_code", nullable = false, updatable = false, length = 32)
  private String studentCode;

  /** CHỤP ẢNH từ {@code students.full_name}. */
  @Column(name = "student_name", nullable = false, updatable = false, length = 255)
  private String studentName;

  // ---------------------------------------------------------------- bên cấp

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "issuer_org_id", nullable = false, updatable = false)
  private Organization issuerOrg;

  /**
   * CHỤP ẢNH địa chỉ ví đã ký, <b>chữ thường</b>.
   *
   * <p>Chữ thường là ràng buộc cấu trúc, không phải quy ước — CSDL có
   * {@code ck_cred_issuer_address}. EIP-55 trộn hoa/thường theo hash của chính địa chỉ, nên
   * một phía lưu checksum còn phía kia lưu chữ thường là ra hai chuỗi JCS khác nhau, tức
   * hai leaf khác nhau.
   */
  // CHAR(42) chứ không VARCHAR: độ dài cố định. Thiếu @JdbcTypeCode thì ddl-auto=validate
  // báo lệch kiểu — Hibernate mặc định map String sang VARCHAR. Giống Organization.
  @Column(name = "issuer_address", nullable = false, updatable = false, length = 42)
  @JdbcTypeCode(SqlTypes.CHAR)
  private String issuerAddress;

  // ---------------------------------------------------------------- nội dung

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 64)
  private CredentialType type;

  /** Học kỳ credential này tổng kết, ví dụ {@code 2026-1}. */
  @Column(nullable = false, updatable = false, length = 16)
  private String semester;

  /** Số hoạt động đã điểm danh trong học kỳ, chốt lúc cấp. */
  @Column(name = "activity_count", nullable = false, updatable = false)
  private Integer activityCount;

  /** Tổng điểm hoạt động, chốt lúc cấp. */
  @Column(name = "total_points", nullable = false, updatable = false)
  private Integer totalPoints;

  @Column(name = "issued_at", nullable = false, updatable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", updatable = false)
  private Instant expiresAt;

  // ---------------------------------------------------------------- bằng chứng

  /**
   * Đúng chuỗi {@code JCS(payload)} đã dùng để tính {@link #leafHash} và để ký.
   *
   * <p>Bản đối chứng chống trôi: lúc neo, {@link CredentialAnchorSource} dựng lại payload từ
   * các cột rồi canonical hóa và so với chuỗi này. Lệch một byte là ném lỗi ngay, thay vì
   * neo một leaf khác với leaf đã ký.
   */
  @Column(name = "payload_json", nullable = false, updatable = false, columnDefinition = "json")
  private String payloadJson;

  /**
   * {@code keccak256(payload_json)} theo công thức leaf — tính <b>lúc cấp</b>, không đợi neo.
   *
   * <p>Khác {@code attendances.leaf_hash} (do job neo điền). Ở đây leaf phải có sẵn từ lúc
   * cấp vì {@link #signature} ký chính nó.
   *
   * <p>Hệ quả: {@link CredentialAnchorSource} <b>không</b> lọc bản ghi chờ neo theo
   * {@code leaf_hash IS NULL} được như {@code attendance}. Nó lọc theo việc đã có dòng trong
   * {@code anchor_leaves} hay chưa.
   */
  @Column(name = "leaf_hash", nullable = false, updatable = false, length = 32)
  @JdbcTypeCode(SqlTypes.BINARY)
  private byte[] leafHash;

  /**
   * ECDSA secp256k1 trên {@link #leafHash}, 65 byte {@code r||s||v}.
   *
   * <p><b>Không phải ES256K của JOSE</b> — xem {@link IssuerSigner} và migration V4.
   */
  @Column(nullable = false, updatable = false, length = 128)
  @JdbcTypeCode(SqlTypes.VARBINARY)
  private byte[] signature;

  /**
   * 16 byte ngẫu nhiên. Thiếu nonce thì proof của sinh viên này để lộ bản ghi của sinh viên
   * khác — xem PROJECT.md §2.3.
   */
  @Column(nullable = false, updatable = false, length = 16)
  @JdbcTypeCode(SqlTypes.BINARY)
  private byte[] nonce;

  // ---------------------------------------------------------------- thu hồi

  /**
   * Chỉ số bit trên {@code StatusList}, cấp <b>ngẫu nhiên</b> từ pool còn trống.
   *
   * <p>Cấp tuần tự làm sự kiện {@code StatusChanged(index)} trên chuỗi công khai lộ thứ tự
   * cấp phát — xem {@code docs/canonicalization.md} §10.
   */
  @Column(name = "status_list_index", nullable = false, updatable = false)
  private Long statusListIndex;

  /** Chỉ đặt SAU khi giao dịch {@code setRevoked()} đã lên chuỗi. */
  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "revoke_tx_hash", length = 66)
  @JdbcTypeCode(SqlTypes.CHAR)
  private String revokeTxHash;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
