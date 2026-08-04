package vn.ptit.drl.event;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {
    Page<Event> findByStatus(EventStatus status, Pageable pageable);
    Page<Event> findByOrgId(Long orgId, Pageable pageable);
    Page<Event> findByStartAtBetween(Instant from, Instant to, Pageable pageable);
}
