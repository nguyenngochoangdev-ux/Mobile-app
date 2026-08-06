package vn.ptit.drl.scoring;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Bộ quy tắc chấm điểm.
 *
 * <p><b>Interface CẤP CAO NHẤT, không lồng trong class.</b> Bản đầu gom ba repository của
 * module này vào một file cho gọn — và Spring Data <b>không quét</b> interface lồng bên trong
 * một lớp, nên ứng dụng không khởi động được với thông báo
 * {@code No qualifying bean of type ...$RulesetRepository}. Gọn mắt không đáng đổi lấy một
 * quy ước ngầm của framework.
 */
public interface RulesetRepository extends JpaRepository<Ruleset, Long> {

  Optional<Ruleset> findByVersionAndSemester(String version, String semester);

  /** Bộ quy tắc mới nhất còn hiệu lực của một học kỳ. */
  Optional<Ruleset> findFirstBySemesterOrderByEffectiveFromDesc(String semester);

  @Query("SELECT r FROM Ruleset r WHERE r.leafHash IS NULL ORDER BY r.id")
  List<Ruleset> findPendingAnchor(Pageable pageable);

  @Modifying
  @Query(nativeQuery = true, value = "UPDATE rulesets SET leaf_hash = :leaf WHERE id = :id")
  int updateLeafHash(@Param("id") Long id, @Param("leaf") byte[] leaf);
}
