package vn.ptit.drl.scoring;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Lượt chấm điểm. */
public interface ScoreRunRepository extends JpaRepository<ScoreRun, Long> {

  Optional<ScoreRun> findFirstBySemesterAndStatusOrderByRunAtDesc(
      String semester, ScoreRun.Status status);
}
