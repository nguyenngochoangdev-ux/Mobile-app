package vn.ptit.drl.event;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import vn.ptit.drl.common.web.BusinessException;
import vn.ptit.drl.common.web.NotFoundException;
import vn.ptit.drl.identity.StudentRepository;
import vn.ptit.drl.identity.jwt.AuthPrincipal;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Registrations", description = "Đăng ký tham gia sự kiện")
public class RegistrationController {

    private final RegistrationRepository repository;
    private final EventRepository eventRepository;
    private final StudentRepository studentRepository;

    public record RegistrationResponse(Long id, Long eventId, String eventTitle,
                                       Long studentId, String mssv, String fullName,
                                       Instant registeredAt, RegistrationStatus status) {

        static RegistrationResponse of(Registration r) {
            return new RegistrationResponse(r.getId(),
                    r.getEvent().getId(), r.getEvent().getTitle(),
                    r.getStudent().getId(), r.getStudent().getMssv(),
                    r.getStudent().getFullName(),
                    r.getRegisteredAt(), r.getStatus());
        }
    }

    @PostMapping("/events/{eventId}/register")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(operationId = "registerForEvent", summary = "Sinh viên tự đăng ký tham gia sự kiện")
    @Transactional
    public RegistrationResponse registerForEvent(@PathVariable Long eventId,
                                         @AuthenticationPrincipal AuthPrincipal principal) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("sự kiện", eventId));

        if (event.getStatus() != EventStatus.OPEN) {
            throw new BusinessException("Sự kiện chưa mở đăng ký hoặc đã đóng");
        }
        if (event.getEndAt().isBefore(Instant.now())) {
            throw new BusinessException("Sự kiện đã kết thúc");
        }

        Long studentId = principal.studentId();
        var existing = repository.findByEventIdAndStudentId(eventId, studentId);
        if (existing.isPresent()) {
            Registration reg = existing.get();
            if (reg.getStatus() == RegistrationStatus.REGISTERED) {
                throw new BusinessException("Bạn đã đăng ký sự kiện này rồi");
            }
            // Đăng ký lại sau khi huỷ: dùng lại bản ghi cũ vì UNIQUE(event_id, student_id).
            reg.setStatus(RegistrationStatus.REGISTERED);
            return RegistrationResponse.of(repository.save(reg));
        }

        if (event.getCapacity() != null) {
            long taken = repository.countByEventIdAndStatus(eventId, RegistrationStatus.REGISTERED);
            if (taken >= event.getCapacity()) {
                throw new BusinessException("Sự kiện đã đủ số lượng đăng ký");
            }
        }

        Registration reg = Registration.builder()
                .event(event)
                .student(studentRepository.findById(studentId)
                        .orElseThrow(() -> new NotFoundException("sinh viên", studentId)))
                .status(RegistrationStatus.REGISTERED)
                .build();
        return RegistrationResponse.of(repository.save(reg));
    }

    @DeleteMapping("/events/{eventId}/register")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Sinh viên huỷ đăng ký")
    @Transactional
    public RegistrationResponse cancel(@PathVariable Long eventId,
                                       @AuthenticationPrincipal AuthPrincipal principal) {
        Registration reg = repository
                .findByEventIdAndStudentId(eventId, principal.studentId())
                .orElseThrow(() -> new NotFoundException("Bạn chưa đăng ký sự kiện này"));

        // Không xoá bản ghi: giữ lại để còn dấu vết sinh viên từng đăng ký rồi huỷ.
        reg.setStatus(RegistrationStatus.CANCELLED);
        return RegistrationResponse.of(repository.save(reg));
    }

    @GetMapping("/events/{eventId}/registrations")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Operation(summary = "Danh sách đăng ký của một sự kiện")
    @Transactional(readOnly = true)
    public Page<RegistrationResponse> listByEvent(@PathVariable Long eventId, Pageable pageable) {
        return repository.findByEventId(eventId, pageable).map(RegistrationResponse::of);
    }

    @GetMapping("/registrations/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Các sự kiện tôi đã đăng ký")
    @Transactional(readOnly = true)
    public Page<RegistrationResponse> myRegistrations(
            @AuthenticationPrincipal AuthPrincipal principal, Pageable pageable) {
        return repository
                .findByStudentIdAndStatus(principal.studentId(),
                        RegistrationStatus.REGISTERED, pageable)
                .map(RegistrationResponse::of);
    }
}
