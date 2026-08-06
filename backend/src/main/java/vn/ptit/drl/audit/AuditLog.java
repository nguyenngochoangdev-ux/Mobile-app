package vn.ptit.drl.audit;

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

/**
 * Một mắt xích của nhật ký có chuỗi băm.
 *
 * <p>Đây là thứ hiện thực <b>luận điểm 1</b> (PROJECT.md §10): không <i>ngăn</i> được quản trị
 * viên sửa dữ liệu quá khứ, nhưng <i>chứng minh</i> được là họ đã sửa.
 *
 * <h2>Hai hash, dùng nhầm là hỏng</h2>
 *
 * <table>
 *   <tr><th>Cột</th><th>Là gì</th><th>Chứng minh điều gì</th></tr>
 *   <tr><td>{@link #hash}</td><td>{@code keccak(prevHash ‖ JCS(record))}</td>
 *       <td><b>Mắt xích.</b> Sửa hay chèn một bản ghi quá khứ làm đứt xích ở mọi bản ghi
 *           sau nó</td></tr>
 *   <tr><td>{@link #leafHash}</td><td>{@code keccak(bytes8('AUDIT') ‖ ':' ‖ JCS(payload))}</td>
 *       <td><b>Lá</b> trong cây Merkle của lô neo. Chứng minh <b>một</b> bản ghi cụ thể đã
 *           tồn tại vào lúc lô được neo</td></tr>
 * </table>
 *
 * <p>Cần cả hai, và chúng bổ sung cho nhau chứ không thay thế. Chuỗi băm một mình chỉ chứng
 * minh tính nhất quán <i>nội bộ</i> — kẻ tấn công có quyền ghi CSDL vẫn tính lại được toàn bộ
 * chuỗi từ điểm sửa trở đi. Cái chặn việc đó là <b>neo định kỳ</b>: root đã lên chuỗi công
 * khai thì không tính lại được nữa.
 *
 * <p>Nói cách khác: <b>chuỗi băm làm cho việc sửa trở nên tốn kém; việc neo làm cho nó trở
 * nên bất khả thi đối với khoảng thời gian đã neo.</b> Đây là câu nên dùng khi bảo vệ.
 *
 * <h2>Bất biến tuyệt đối</h2>
 *
 * <p>Không cột nào {@code updatable}. Nhật ký chỉ ghi thêm — sửa một dòng là phá đúng thứ nó
 * sinh ra để bảo vệ. Không có luồng xóa, và cũng không nên có: xóa một dòng cũng làm đứt xích
 * y như sửa, nên nó <b>phát hiện được</b>, nhưng bản ghi thì mất vĩnh viễn.
 */
@Entity
@Table(name = "audit_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** {@code users.id} của người thực hiện. NULL khi hệ thống tự làm (job neo, seeder). */
  @Column(name = "actor_id", updatable = false)
  private Long actorId;

  @Column(nullable = false, updatable = false, length = 64)
  private String action;

  @Column(nullable = false, updatable = false, length = 64)
  private String entity;

  @Column(name = "entity_id", updatable = false)
  private Long entityId;

  /**
   * Trạng thái trước, <b>nguyên văn</b>. NULL khi là hành động tạo mới.
   *
   * <p>LONGTEXT chứ không phải JSON: keccak của <b>chính byte này</b> đi vào {@link #hash}, và
   * kiểu JSON của MySQL sắp xếp lại khóa rồi chèn khoảng trắng. Xem migration V7.
   */
  @Column(name = "before_json", updatable = false, columnDefinition = "longtext")
  @JdbcTypeCode(SqlTypes.LONGVARCHAR)
  private String beforeJson;

  /** Trạng thái sau, nguyên văn. NULL khi là hành động xóa. Xem {@link #beforeJson}. */
  @Column(name = "after_json", updatable = false, columnDefinition = "longtext")
  @JdbcTypeCode(SqlTypes.LONGVARCHAR)
  private String afterJson;

  /**
   * Mắt xích trước. <b>NULL chỉ ở bản ghi đầu tiên của cả chuỗi.</b>
   *
   * <p>Lúc băm, NULL được thay bằng 32 byte {@code 0x00} — xem {@link AuditHasher}.
   */
  @Column(name = "prev_hash", updatable = false, length = 32)
  @JdbcTypeCode(SqlTypes.BINARY)
  private byte[] prevHash;

  @Column(nullable = false, updatable = false, length = 32)
  @JdbcTypeCode(SqlTypes.BINARY)
  private byte[] hash;

  /**
   * 16 byte ngẫu nhiên.
   *
   * <p>Vẫn cần dù đã có {@link #prevHash}: {@code prevHash} cho entropy ở mọi bản ghi <b>trừ
   * bản ghi đầu tiên</b>, và dựa vào nó là dựa vào một tính chất phụ. Xem
   * {@code docs/canonicalization.md} §9.3.
   */
  @Column(nullable = false, updatable = false, length = 16)
  @JdbcTypeCode(SqlTypes.BINARY)
  private byte[] nonce;

  /** Do job neo điền sau khi giao dịch lên chuỗi. NULL nghĩa là chưa neo. */
  @Column(name = "leaf_hash", length = 32)
  @JdbcTypeCode(SqlTypes.BINARY)
  private byte[] leafHash;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
