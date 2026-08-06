package vn.ptit.drl.scoring;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Một lượt chấm điểm cho một học kỳ, bằng một bộ quy tắc cụ thể. */
@Entity
@Table(name = "score_runs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScoreRun {

  public enum Status { RUNNING, DONE, FAILED }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false, length = 16)
  private String semester;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ruleset_id", nullable = false, updatable = false)
  private Ruleset ruleset;

  /**
   * Mốc thời gian của cả lượt chấm — <b>một giá trị dùng cho mọi sinh viên</b>.
   *
   * <p>Nó đi vào {@code scoredAt} của payload {@code SCORE}. Nếu mỗi bản ghi lấy
   * {@code Instant.now()} riêng thì 500 bản ghi của cùng một lượt mang 500 mốc khác nhau, và
   * không nhóm lại được thành "lượt chấm ngày ấy".
   */
  @Column(name = "run_at", nullable = false, updatable = false)
  private Instant runAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Status status;
}
