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
import vn.ptit.drl.identity.Student;

/**
 * Điểm rèn luyện của một sinh viên trong một lượt chấm.
 *
 * <p>Không sửa được: mọi cột đi vào payload đều {@code updatable = false}. Chấm lại thì tạo
 * một {@link ScoreRun} mới — cùng lý do với credential và nhật ký.
 */
@Entity
@Table(name = "scores")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Score {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "run_id", nullable = false, updatable = false)
  private ScoreRun run;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "student_id", nullable = false, updatable = false)
  private Student student;

  @Column(nullable = false, updatable = false) private Integer c1;
  @Column(nullable = false, updatable = false) private Integer c2;
  @Column(nullable = false, updatable = false) private Integer c3;
  @Column(nullable = false, updatable = false) private Integer c4;
  @Column(nullable = false, updatable = false) private Integer c5;

  @Column(nullable = false, updatable = false)
  private Integer total;

  @Column(updatable = false, length = 16)
  private String classification;

  /**
   * Cam kết vào ĐÚNG tập bản ghi điểm danh đã dùng để tính điểm này.
   *
   * <p>Đây là đóng góp học thuật rõ nhất của đề tài — xem {@link EvidenceHasher}.
   */
  @Column(name = "evidence_hash", updatable = false, length = 32)
  @JdbcTypeCode(SqlTypes.BINARY)
  private byte[] evidenceHash;

  @Column(nullable = false, updatable = false, length = 16)
  @JdbcTypeCode(SqlTypes.BINARY)
  private byte[] nonce;

  /** Do job neo điền. NULL nghĩa là chưa neo. */
  @Column(name = "leaf_hash", length = 32)
  @JdbcTypeCode(SqlTypes.BINARY)
  private byte[] leafHash;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
