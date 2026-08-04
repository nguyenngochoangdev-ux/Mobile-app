package vn.ptit.drl.attendance;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByEventIdAndStudentId(Long eventId, Long studentId);

    boolean existsByEventIdAndStudentId(Long eventId, Long studentId);

    Page<Attendance> findByEventId(Long eventId, Pageable pageable);

    Page<Attendance> findByStudentId(Long studentId, Pageable pageable);

    long countByEventId(Long eventId);

    /** Số bản ghi cần xem lại: ngoài vùng geofence hoặc cán bộ nhập tay. */
    long countByEventIdAndGeofenceOkFalse(Long eventId);
}
