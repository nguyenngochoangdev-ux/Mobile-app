package vn.ptit.drl.attendance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.ptit.drl.common.web.NotFoundException;
import vn.ptit.drl.event.EventRepository;
import vn.ptit.drl.identity.jwt.AuthPrincipal;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Attendance", description = "Điểm danh chống gian lận")
public class AttendanceController {

    private final AttendanceService service;
    private final AttendanceRepository repository;
    private final EventRepository eventRepository;
    private final QrTokenService qrTokenService;

    public record CheckinRequest(
            @NotNull Long eventId,
            @NotNull Long slot,
            @NotBlank String token,
            @NotBlank @Size(max = 128) String deviceFp,
            BigDecimal lat,
            BigDecimal lng) {}

    /** Một lần quét đã lưu offline, kèm thời điểm quét thật. */
    public record OfflineScan(
            @NotNull Long eventId,
            @NotNull Long slot,
            @NotBlank String token,
            @NotBlank String deviceFp,
            BigDecimal lat,
            BigDecimal lng,
            @NotNull Instant scannedAt) {}

    public record SyncRequest(@NotNull @Size(min = 1, max = 200) List<OfflineScan> scans) {}

    public record SyncResult(int accepted, int rejected, List<String> errors) {}

    public record AttendanceResponse(Long id, Long eventId, String eventTitle,
                                     Long studentId, String mssv, String fullName,
                                     Instant checkinAt, Instant checkoutAt,
                                     AttendanceMethod method, Boolean verified,
                                     Boolean geofenceOk, Long qrSlot, boolean anchored) {

        static AttendanceResponse of(Attendance a) {
            return new AttendanceResponse(a.getId(),
                    a.getEvent().getId(), a.getEvent().getTitle(),
                    a.getStudent().getId(), a.getStudent().getMssv(),
                    a.getStudent().getFullName(),
                    a.getCheckinAt(), a.getCheckoutAt(), a.getMethod(),
                    a.getVerified(), a.getGeofenceOk(), a.getQrSlot(),
                    a.getLeafHash() != null);
        }
    }

    // @Transactional trên controller: AttendanceResponse.of() đọc event.title và
    // student.mssv qua association LAZY. Với open-in-view=false, transaction của service
    // đã đóng trước khi map DTO -> LazyInitializationException. Mở transaction ở đây để
    // bao trọn cả bước map.
    @PostMapping("/checkin")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STUDENT')")
    @Transactional
    @Operation(summary = "Sinh viên quét QR để check-in — qua năm bước kiểm tra")
    public AttendanceResponse checkin(@Valid @RequestBody CheckinRequest req,
                                      @AuthenticationPrincipal AuthPrincipal principal) {
        var cmd = new AttendanceService.CheckinCommand(
                req.eventId(), principal.studentId(), req.slot(), req.token(),
                req.deviceFp(), req.lat(), req.lng(), null, false);
        return AttendanceResponse.of(service.checkin(cmd));
    }

    @PostMapping("/checkout")
    @PreAuthorize("hasRole('STUDENT')")
    @Transactional
    @Operation(summary = "Check-out khỏi sự kiện")
    public AttendanceResponse checkout(@RequestParam Long eventId,
                                       @AuthenticationPrincipal AuthPrincipal principal) {
        return AttendanceResponse.of(service.checkout(eventId, principal.studentId()));
    }

    @PostMapping("/sync")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Đồng bộ các lần quét đã lưu offline")
    public SyncResult sync(@Valid @RequestBody SyncRequest req,
                           @AuthenticationPrincipal AuthPrincipal principal) {
        int accepted = 0;
        List<String> errors = new ArrayList<>();

        // Xử lý từng bản ghi độc lập: một bản lỗi không được làm hỏng cả lô.
        // Hội trường mất sóng là chuyện thường, sinh viên có thể gom nhiều lần quét.
        for (OfflineScan scan : req.scans()) {
            try {
                var cmd = new AttendanceService.CheckinCommand(
                        scan.eventId(), principal.studentId(), scan.slot(), scan.token(),
                        scan.deviceFp(), scan.lat(), scan.lng(), scan.scannedAt(), true);
                service.checkin(cmd);
                accepted++;
            } catch (RuntimeException ex) {
                errors.add("eventId=" + scan.eventId() + ": " + ex.getMessage());
            }
        }
        return new SyncResult(accepted, errors.size(), errors);
    }

    @PostMapping("/manual")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @Operation(summary = "Cán bộ điểm danh tay — verified = false (vấn đề oracle)")
    public AttendanceResponse manual(@RequestParam Long eventId, @RequestParam Long studentId) {
        return AttendanceResponse.of(service.manualCheckin(eventId, studentId));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Sổ tay hoạt động của tôi")
    @Transactional(readOnly = true)
    public Page<AttendanceResponse> myAttendance(
            @AuthenticationPrincipal AuthPrincipal principal, Pageable pageable) {
        return repository.findByStudentId(principal.studentId(), pageable)
                .map(AttendanceResponse::of);
    }

    @GetMapping("/event/{eventId}")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Operation(summary = "Danh sách điểm danh của một sự kiện")
    @Transactional(readOnly = true)
    public Page<AttendanceResponse> byEvent(@PathVariable Long eventId, Pageable pageable) {
        return repository.findByEventId(eventId, pageable).map(AttendanceResponse::of);
    }

    @GetMapping("/event/{eventId}/stats")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Operation(summary = "Thống kê điểm danh — số bản ghi cần xem lại")
    @Transactional(readOnly = true)
    public StatsResponse stats(@PathVariable Long eventId) {
        long total = repository.countByEventId(eventId);
        long outOfRange = repository.countByEventIdAndGeofenceOkFalse(eventId);
        return new StatsResponse(total, outOfRange);
    }

    public record StatsResponse(long total, long outsideGeofence) {}

    /**
     * Token QR hiện tại cho màn hình presenter.
     * <p>
     * Trả token chứ KHÔNG trả secretKey. Presenter poll endpoint này mỗi vài giây,
     * hoặc tự tính nếu đã có token và biết chu kỳ.
     */
    @GetMapping("/qr/{eventId}")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Operation(summary = "Token QR hiện tại cho màn hình presenter")
    @Transactional(readOnly = true)
    public QrResponse currentQr(@PathVariable Long eventId) {
        var event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("sự kiện", eventId));
        long slot = qrTokenService.currentSlot();
        String token = qrTokenService.generate(eventId, event.getSecretKey(), slot);
        return new QrResponse(eventId, slot, token,
                qrTokenService.slotStart(slot + 1));
    }

    /** @param validUntil thời điểm slot hiện tại hết hiệu lực — presenter dùng để hẹn render lại. */
    public record QrResponse(Long eventId, long slot, String token, Instant validUntil) {}
}
