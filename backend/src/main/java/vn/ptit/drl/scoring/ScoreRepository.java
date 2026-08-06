package vn.ptit.drl.scoring;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Điểm rèn luyện của từng sinh viên trong từng lượt chấm. */
public interface ScoreRepository extends JpaRepository<Score, Long> {

  List<Score> findByRunIdOrderByIdAsc(Long runId);

  /**
   * Điểm mới nhất của một sinh viên ở một học kỳ.
   *
   * <p>Sắp theo {@code run.runAt} giảm dần chứ không theo {@code id}: chấm lại tạo lượt mới,
   * và thứ sinh viên cần thấy là <b>lượt gần nhất</b>. Dùng {@code id} thì đúng trong hầu hết
   * trường hợp nhưng sai ngay khi có hai lượt chạy xen kẽ.
   */
  Optional<Score> findFirstByStudentIdAndRunSemesterOrderByRunRunAtDesc(
      Long studentId, String semester);

  /** Điểm mới nhất của một sinh viên, mọi học kỳ, mới trước cũ sau. */
  @Query("""
      SELECT s FROM Score s WHERE s.student.id = :studentId
       ORDER BY s.run.runAt DESC, s.id DESC
      """)
  List<Score> findByStudentNewestFirst(@Param("studentId") Long studentId, Pageable pageable);

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
