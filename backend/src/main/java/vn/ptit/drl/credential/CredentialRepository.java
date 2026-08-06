package vn.ptit.drl.credential;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CredentialRepository extends JpaRepository<Credential, Long> {

  List<Credential> findByStudentIdOrderByIssuedAtDesc(Long studentId);

  Optional<Credential> findByStudentIdAndSemesterAndType(
      Long studentId, String semester, CredentialType type);

  boolean existsByStatusListIndex(Long statusListIndex);

  /**
   * Credential đã cấp nhưng chưa vào lô neo nào, cũ trước mới sau.
   *
   * <p><b>Không</b> lọc theo {@code leaf_hash IS NULL} như {@code attendances} — ở bảng này
   * {@code leaf_hash} có giá trị ngay từ lúc cấp, vì chữ ký ký chính nó. Cờ "đã neo chưa" vì
   * thế phải đọc từ {@code anchor_leaves}, nơi duy nhất biết một bản ghi đã vào lô nào.
   *
   * <p>Điều kiện chốt của miền này là <b>credential đã được ký và lưu</b> — không có gì thay
   * đổi được sau đó ({@link Credential} để mọi cột trong payload {@code updatable = false}),
   * nên không phải đợi mốc thời gian nào như {@code AttendanceAnchorSource} phải đợi
   * {@code events.end_at}.
   *
   * <p>Truy vấn <b>native</b> vì {@code anchor_leaves} cố ý không có entity JPA — module
   * {@code anchor} thao tác nó bằng {@code JdbcTemplate} và không nên lộ lược đồ đó ra thành
   * một kiểu mà nghiệp vụ ánh xạ vào (PROJECT.md §5). Một tên bảng trong chuỗi SQL rẻ hơn
   * nhiều so với một entity dùng chung giữa hai module.
   */
  @Query(nativeQuery = true, value = """
      SELECT c.* FROM credentials c
      WHERE NOT EXISTS (
        SELECT 1 FROM anchor_leaves l
        WHERE l.source_table = 'credentials' AND l.source_id = c.id
      )
      ORDER BY c.id
      """)
  List<Credential> findPendingAnchor(Pageable pageable);

  @Query("SELECT COUNT(c) FROM Credential c WHERE c.semester = :semester")
  long countBySemester(@Param("semester") String semester);

  /**
   * Ghi bằng chứng (payload chuẩn tắc, leaf, chữ ký) cho credential vừa lưu.
   *
   * <p>Phải viết tay vì ba cột này khai {@code updatable = false} trên entity — Hibernate cố
   * ý <b>không</b> sinh {@code UPDATE} cho chúng, đó chính là thứ chặn mọi đường sửa
   * credential đã cấp. Ở đây cần đúng một ngoại lệ: lần ghi <b>đầu tiên và duy nhất</b>, ngay
   * sau khi {@code AUTO_INCREMENT} cấp id (xem {@code CredentialService.persistWithProof}).
   *
   * <p>{@code AND leaf_hash = ?} với chỗ giữ chỗ toàn byte {@code 0x00} là chốt chặn: câu
   * lệnh này <b>chỉ chạy được một lần</b> cho mỗi credential. Gọi lại lần hai trả về 0 dòng.
   * Không có điều kiện đó thì đây trở thành cái cửa hậu sửa credential đã neo mà cả entity
   * lẫn contract đều không chặn được.
   *
   * @return số dòng đã ghi — bên gọi phải kiểm bằng 1
   */
  @Modifying
  @Query(nativeQuery = true, value = """
      UPDATE credentials
         SET payload_json = :json, leaf_hash = :leaf, signature = :sig
       WHERE id = :id AND leaf_hash = :placeholder
      """)
  int saveProofRows(@Param("id") Long id,
                    @Param("json") String json,
                    @Param("leaf") byte[] leaf,
                    @Param("sig") byte[] signature,
                    @Param("placeholder") byte[] placeholder);

  /** Bọc {@link #saveProofRows} để không ai quên kiểm số dòng. */
  default void saveProof(Long id, String json, byte[] leaf, byte[] signature) {
    int rows = saveProofRows(id, json, leaf, signature, new byte[32]);
    if (rows != 1) {
      throw new IllegalStateException(
          "Ghi bằng chứng cho credential " + id + " chạm " + rows + " dòng thay vì 1."
              + " Credential này đã có leaf_hash — nó đã được ký rồi và KHÔNG được ký lại.");
    }
  }
}
