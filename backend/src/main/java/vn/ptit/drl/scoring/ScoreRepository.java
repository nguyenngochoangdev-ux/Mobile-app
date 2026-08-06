package vn.ptit.drl.scoring;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Điểm rèn luyện của từng sinh viên trong từng lượt chấm. */
public interface ScoreRepository extends JpaRepository<Score, Long> {

  List<Score> findByRunIdOrderByIdAsc(Long runId);

  long countByRunId(Long runId);

  @Query("SELECT s FROM Score s WHERE s.leafHash IS NULL ORDER BY s.id")
  List<Score> findPendingAnchor(Pageable pageable);

  @Modifying
  @Query(nativeQuery = true, value = "UPDATE scores SET leaf_hash = :leaf WHERE id = :id")
  int updateLeafHash(@Param("id") Long id, @Param("leaf") byte[] leaf);

  /** Phân bố xếp loại của một lượt chấm — số liệu cho chương 11. */
  @Query("""
      SELECT s.classification, COUNT(s), MIN(s.total), MAX(s.total), AVG(s.total)
        FROM Score s WHERE s.run.id = :runId GROUP BY s.classification
      """)
  List<Object[]> thongKeXepLoai(@Param("runId") Long runId);
}
