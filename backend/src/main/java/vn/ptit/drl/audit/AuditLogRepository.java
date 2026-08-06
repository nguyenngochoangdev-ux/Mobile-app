package vn.ptit.drl.audit;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

  /** Mắt xích cuối cùng của chuỗi. {@code Optional.empty()} nghĩa là nhật ký còn rỗng. */
  Optional<AuditLog> findFirstByOrderByIdDesc();

  /** Toàn bộ chuỗi theo đúng thứ tự — dùng để kiểm tra tính toàn vẹn. */
  List<AuditLog> findAllByOrderByIdAsc();

  /** Một đoạn chuỗi, cũ trước mới sau. */
  @Query("SELECT a FROM AuditLog a WHERE a.id >= :fromId ORDER BY a.id")
  List<AuditLog> findChainFrom(@Param("fromId") long fromId, Pageable pageable);

  List<AuditLog> findByEntityAndEntityIdOrderByIdAsc(String entity, Long entityId);

  /**
   * Bản ghi chưa neo, cũ trước mới sau.
   *
   * <p>Khác {@code credentials}: ở bảng này {@code leaf_hash} do job neo điền nên
   * {@code IS NULL} là cờ đúng, giống {@code attendances}.
   */
  @Query("SELECT a FROM AuditLog a WHERE a.leafHash IS NULL ORDER BY a.id")
  List<AuditLog> findPendingAnchor(Pageable pageable);

  /**
   * Ghi mắt xích cho bản ghi vừa lưu — <b>một lần và chỉ một lần</b>.
   *
   * <p>Phải viết tay vì {@code hash} khai {@code updatable = false} trên entity; đó chính là
   * thứ chặn mọi đường sửa nhật ký. Ở đây cần đúng một ngoại lệ: lần ghi đầu tiên, ngay sau
   * khi {@code AUTO_INCREMENT} cấp id và {@code @CreationTimestamp} điền {@code created_at}
   * (mắt xích cần cả hai).
   *
   * <p>{@code AND hash = :placeholder} với chỗ giữ chỗ toàn byte {@code 0x00} là chốt chặn:
   * gọi lần thứ hai trả về 0 dòng. Không có điều kiện đó thì đây trở thành cửa hậu sửa nhật
   * ký — đúng thứ cả cơ chế sinh ra để chặn.
   */
  @Modifying
  @Query(nativeQuery = true,
      value = "UPDATE audit_logs SET hash = :hash WHERE id = :id AND hash = :placeholder")
  int updateHashRows(@Param("id") Long id,
                     @Param("hash") byte[] hash,
                     @Param("placeholder") byte[] placeholder);

  /** Bọc {@link #updateHashRows} cho gọn chỗ gọi. */
  default int updateHash(Long id, byte[] hash, byte[] placeholder) {
    return updateHashRows(id, hash, placeholder);
  }

  /** Đánh dấu đã neo. Gọi SAU khi giao dịch lên chuỗi — xem {@code AnchorSource}. */
  @Modifying
  @Query(nativeQuery = true, value = "UPDATE audit_logs SET leaf_hash = :leaf WHERE id = :id")
  int updateLeafHash(@Param("id") Long id, @Param("leaf") byte[] leaf);
}
