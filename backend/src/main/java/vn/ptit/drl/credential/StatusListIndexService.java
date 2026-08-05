package vn.ptit.drl.credential;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.ptit.drl.common.config.DrlProperties;

/**
 * Nối {@link StatusListIndexAllocator} với bảng {@code credentials}.
 *
 * <p>Cố ý dùng {@link JdbcTemplate} thay vì một repository JPA: cả lớp này chỉ cần đọc đúng
 * một cột đã tồn tại từ Flyway {@code V1}. Dựng entity {@code Credential} ngay bây giờ là
 * chốt sớm các quyết định của tuần 4 (VC, chữ ký, bundle) trong khi chưa cần.
 *
 * <p>Phần thuật toán nằm ở {@link StatusListIndexAllocator} và không phụ thuộc Spring hay
 * CSDL — nên nó test được thuần, không cần container.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatusListIndexService {

  private final DrlProperties props;
  private final JdbcTemplate jdbc;

  private StatusListIndexAllocator allocator;

  private StatusListIndexAllocator allocator() {
    if (allocator == null) {
      allocator = new StatusListIndexAllocator(props.credential().statusListPoolSize());
    }
    return allocator;
  }

  private boolean isTaken(long index) {
    Integer found =
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM credentials WHERE status_list_index = ?)",
            Integer.class,
            index);
    return found != null && found == 1;
  }

  /**
   * Cấp một {@code status_list_index} còn trống.
   *
   * <p>Bên gọi phải sẵn sàng nhận {@code DataIntegrityViolationException} khi ghi: chỉ số này
   * còn trống <b>tại thời điểm hỏi</b>, và ràng buộc {@code uk_cred_status_index} mới là
   * trọng tài cuối cùng.
   */
  public long allocate() {
    warnIfFilling();
    return allocator().allocate(this::isTaken);
  }

  /** Cấp nhiều chỉ số phân biệt trong một lượt — dùng khi cấp credential hàng loạt cuối kỳ. */
  public List<Long> allocateBatch(int count) {
    warnIfFilling();
    return allocator().allocateBatch(count, this::isTaken);
  }

  /** Số chỉ số đã cấp. */
  public long usedCount() {
    Long n =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM credentials WHERE status_list_index IS NOT NULL", Long.class);
    return n == null ? 0L : n;
  }

  /**
   * Cảnh báo sớm khi pool bắt đầu đầy.
   *
   * <p>Pool đầy làm cấp phát ném lỗi và dừng việc cấp credential. Biết trước vài tuần thì chỉ
   * cần đổi một dòng cấu hình; biết lúc nó gãy giữa buổi chấm cuối kỳ thì tệ hơn nhiều.
   */
  private void warnIfFilling() {
    double occupancy = allocator().occupancy(usedCount());
    if (occupancy >= StatusListIndexAllocator.OCCUPANCY_WARN_THRESHOLD) {
      log.warn(
          "Pool status_list_index đã dùng {}% ({} / {}). Tăng"
              + " `drl.credential.status-list-pool-size` trước khi nó chặn cấp phát."
              + " KHÔNG chuyển sang cấp tuần tự — xem PROJECT.md §2.3.",
          Math.round(occupancy * 100),
          usedCount(),
          allocator().poolSize());
    }
  }
}
