package vn.ptit.drl.identity;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentDeviceRepository extends JpaRepository<StudentDevice, Long> {

    List<StudentDevice> findByStudentId(Long studentId);

    Optional<StudentDevice> findByStudentIdAndDeviceFp(Long studentId, String deviceFp);

    Page<StudentDevice> findByStatus(DeviceStatus status, Pageable pageable);

    long countByStudentIdAndStatus(Long studentId, DeviceStatus status);

    /** Dùng ở bước kiểm tra thứ 2 khi check-in: thiết bị quét có phải thiết bị đã duyệt. */
    boolean existsByStudentIdAndDeviceFpAndStatus(Long studentId, String deviceFp,
                                                  DeviceStatus status);
}
