package vn.ptit.drl.event;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    Optional<Registration> findByEventIdAndStudentId(Long eventId, Long studentId);

    Page<Registration> findByEventId(Long eventId, Pageable pageable);

    Page<Registration> findByStudentIdAndStatus(Long studentId, RegistrationStatus status,
                                                Pageable pageable);

    long countByEventIdAndStatus(Long eventId, RegistrationStatus status);
}
