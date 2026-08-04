package vn.ptit.drl.identity;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentDeviceRepository extends JpaRepository<StudentDevice, Long> {
    List<StudentDevice> findByStudentId(Long studentId);
    Optional<StudentDevice> findByStudentIdAndDeviceFp(Long studentId, String deviceFp);
    List<StudentDevice> findByStatus(DeviceStatus status);
}
