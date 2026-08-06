package vn.ptit.drl.attendance;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByEventIdAndStudentId(Long eventId, Long studentId);

    /**
     * Mọi bản ghi điểm danh của một học kỳ, kèm sinh viên và sự kiện.
     *
     * <p>Dùng cho việc chấm điểm hàng loạt: <b>một truy vấn cho cả 500 sinh viên</b>, thay vì
     * một truy vấn mỗi người. {@code join fetch} vì mỗi bản ghi cần {@code student.mssv} (để
     * dựng payload {@code ATTEND} tính leaf làm bằng chứng) và {@code event.criteriaCode} +
     * {@code event.points} (để dựng dữ kiện chấm điểm).
     *
     * <p>Sự kiện có {@code semester} NULL <b>bị bỏ qua</b> — xem migration V8. Đoán học kỳ
     * cho chúng sẽ đưa điểm sai vào một bản ghi đã ký và đã neo.
     *
     * <p>Sắp xếp theo {@code student.id} rồi {@code id} để lượt chấm tất định.
     */
    @Query("""
        SELECT a FROM Attendance a
          JOIN FETCH a.student s
          JOIN FETCH a.event e
         WHERE e.semester = :semester
         ORDER BY s.id, a.id
        """)
    List<Attendance> findBySemesterForScoring(@Param("semester") String semester);

    boolean existsByEventIdAndStudentId(Long eventId, Long studentId);

    Page<Attendance> findByEventId(Long eventId, Pageable pageable);

    Page<Attendance> findByStudentId(Long studentId, Pageable pageable);

    long countByEventId(Long eventId);

    /** Số bản ghi cần xem lại: ngoài vùng geofence hoặc cán bộ nhập tay. */
    long countByEventIdAndGeofenceOkFalse(Long eventId);

    /**
     * Bản ghi chờ neo: chưa có {@code leaf_hash} và sự kiện đã kết thúc.
     *
     * <p>Điều kiện {@code endAt < now} là bắt buộc, không phải tối ưu: payload được neo có
     * {@code checkOutAt}, nên neo trước khi sự kiện xong là leaf lạc hậu vĩnh viễn —
     * {@code AnchorRegistry} không cho neo lại. Xem {@code AttendanceAnchorSource}.
     *
     * <p>{@code join fetch} để tránh N+1: mỗi payload cần {@code student.mssv} và
     * {@code event.id}, và một lô có thể tới 5000 bản ghi.
     *
     * <p>Sắp xếp theo {@code id} để thứ tự lá tất định — thứ tự lá là một phần của bằng
     * chứng, vì cây Merkle giữ nguyên thứ tự (docs/canonicalization.md §8 quy ước 3).
     */
    @Query("""
        select a from Attendance a
          join fetch a.student
          join fetch a.event e
        where a.leafHash is null and e.endAt < :now
        order by a.id
        """)
    List<Attendance> findPendingAnchor(@Param("now") Instant now, Pageable pageable);
}
