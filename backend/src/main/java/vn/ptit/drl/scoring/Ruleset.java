package vn.ptit.drl.scoring;

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
 * Một bản bộ quy tắc chấm điểm đã công bố.
 *
 * <p>Mọi cột {@code updatable = false} trừ {@code leafHash}: bộ quy tắc đã công bố là một
 * <b>văn bản có hiệu lực</b>. Sửa nó sau khi đã chấm điểm nghĩa là đổi câu chuyện giải thích
 * những con số đã neo — chính là việc miền {@code RULESET} sinh ra để chặn. Muốn đổi quy tắc
 * thì <b>tạo bản version mới</b>.
 */
@Entity
@Table(name = "rulesets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Ruleset {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false, length = 32)
  private String version;

  @Column(nullable = false, updatable = false, length = 16)
  private String semester;

  /**
   * Nội dung tệp bộ quy tắc, <b>nguyên văn</b>.
   *
   * <p>LONGTEXT chứ không phải JSON: keccak của chính byte này là {@link #rulesetHash}, và
   * kiểu JSON của MySQL sắp xếp lại khóa rồi chèn khoảng trắng. Xem migration V7.
   */
  @Column(name = "json_body", nullable = false, updatable = false, columnDefinition = "longtext")
  @JdbcTypeCode(SqlTypes.LONGVARCHAR)
  private String jsonBody;

  /** {@code keccak256} của byte UTF-8 của {@link #jsonBody}. KHÁC {@link #leafHash}. */
  @Column(name = "ruleset_hash", nullable = false, updatable = false, length = 32)
  @JdbcTypeCode(SqlTypes.BINARY)
  private byte[] rulesetHash;

  @Column(nullable = false, updatable = false, length = 16)
  @JdbcTypeCode(SqlTypes.BINARY)
  private byte[] nonce;

  /** Do job neo điền. NULL nghĩa là chưa neo. */
  @Column(name = "leaf_hash", length = 32)
  @JdbcTypeCode(SqlTypes.BINARY)
  private byte[] leafHash;

  @Column(name = "effective_from", nullable = false, updatable = false)
  private Instant effectiveFrom;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
