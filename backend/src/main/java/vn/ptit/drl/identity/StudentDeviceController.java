package vn.ptit.drl.identity;

import java.time.Instant;
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
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import vn.ptit.drl.common.config.DrlProperties;
import vn.ptit.drl.common.web.BusinessException;
import vn.ptit.drl.common.web.NotFoundException;
import vn.ptit.drl.identity.jwt.AuthPrincipal;

/**
 * Đăng ký và duyệt thiết bị.
 * <p>
 * Thiếu tầng này thì toàn bộ cơ chế QR động vô nghĩa: sinh viên chỉ cần đưa tài khoản
 * cho bạn quét hộ. Đây là bước kiểm tra thứ 2 trong năm bước check-in.
 */
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@Tag(name = "Devices", description = "Đăng ký và duyệt thiết bị của sinh viên")
public class StudentDeviceController {

    private final StudentDeviceRepository repository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final DrlProperties props;

    public record RegisterRequest(
            @NotBlank @Size(max = 128) String deviceFp,
            @Size(max = 128) String label) {}

    public record DeviceResponse(Long id, Long studentId, String mssv, String fullName,
                                 String deviceFp, String label, DeviceStatus status,
                                 String approvedBy, Instant approvedAt, Instant createdAt) {

        static DeviceResponse of(StudentDevice d) {
            return new DeviceResponse(d.getId(),
                    d.getStudent().getId(), d.getStudent().getMssv(),
                    d.getStudent().getFullName(),
                    d.getDeviceFp(), d.getLabel(), d.getStatus(),
                    d.getApprovedBy() == null ? null : d.getApprovedBy().getUsername(),
                    d.getApprovedAt(), d.getCreatedAt());
        }
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Sinh viên đăng ký thiết bị của mình")
    @Transactional
    public DeviceResponse register(@Valid @RequestBody RegisterRequest req,
                                   @AuthenticationPrincipal AuthPrincipal principal) {
        Long studentId = principal.studentId();

        var existing = repository.findByStudentIdAndDeviceFp(studentId, req.deviceFp());
        if (existing.isPresent()) {
            StudentDevice d = existing.get();
            if (d.getStatus() == DeviceStatus.REVOKED) {
                throw new BusinessException(
                        "Thiết bị này đã bị thu hồi. Liên hệ cán bộ để được cấp lại.");
            }
            // Đăng ký lại cùng thiết bị là thao tác vô hại (cài lại app, xoá cache).
            return DeviceResponse.of(d);
        }

        // Thiết bị đầu tiên duyệt tự động; đổi thiết bị luôn phải qua cán bộ.
        // Chính ràng buộc "đổi thiết bị cần duyệt" mới là thứ chống điểm danh hộ.
        boolean isFirst = repository.countByStudentIdAndStatus(studentId, DeviceStatus.ACTIVE) == 0;
        boolean autoApprove = isFirst && props.attendance().autoApproveFirstDevice();

        StudentDevice device = StudentDevice.builder()
                .student(studentRepository.findById(studentId)
                        .orElseThrow(() -> new NotFoundException("sinh viên", studentId)))
                .deviceFp(req.deviceFp())
                .label(req.label())
                .status(autoApprove ? DeviceStatus.ACTIVE : DeviceStatus.PENDING)
                .approvedAt(autoApprove ? Instant.now() : null)
                .build();

        return DeviceResponse.of(repository.save(device));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Thiết bị của tôi")
    @Transactional(readOnly = true)
    public List<DeviceResponse> myDevices(@AuthenticationPrincipal AuthPrincipal principal) {
        return repository.findByStudentId(principal.studentId())
                .stream().map(DeviceResponse::of).toList();
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Operation(summary = "Hàng đợi thiết bị chờ duyệt")
    @Transactional(readOnly = true)
    public Page<DeviceResponse> pending(Pageable pageable) {
        return repository.findByStatus(DeviceStatus.PENDING, pageable).map(DeviceResponse::of);
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Operation(summary = "Thiết bị của một sinh viên")
    @Transactional(readOnly = true)
    public List<DeviceResponse> byStudent(@PathVariable Long studentId) {
        return repository.findByStudentId(studentId).stream().map(DeviceResponse::of).toList();
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Operation(summary = "Cán bộ duyệt thiết bị")
    @Transactional
    public DeviceResponse approve(@PathVariable Long id,
                                  @AuthenticationPrincipal AuthPrincipal principal) {
        StudentDevice device = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("thiết bị", id));

        if (device.getStatus() == DeviceStatus.ACTIVE) {
            throw new BusinessException("Thiết bị đã ở trạng thái hoạt động");
        }

        // Mỗi sinh viên chỉ có MỘT thiết bị hoạt động. Duyệt thiết bị mới thì thu hồi
        // thiết bị cũ — nếu không, mượn tài khoản vẫn điểm danh hộ được bằng máy cũ.
        repository.findByStudentId(device.getStudent().getId()).stream()
                .filter(d -> d.getStatus() == DeviceStatus.ACTIVE)
                .forEach(d -> {
                    d.setStatus(DeviceStatus.REVOKED);
                    repository.save(d);
                });

        device.setStatus(DeviceStatus.ACTIVE);
        device.setApprovedBy(userRepository.findById(principal.userId()).orElse(null));
        device.setApprovedAt(Instant.now());
        return DeviceResponse.of(repository.save(device));
    }

    @PatchMapping("/{id}/revoke")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Operation(summary = "Cán bộ thu hồi thiết bị")
    @Transactional
    public DeviceResponse revoke(@PathVariable Long id,
                                 @AuthenticationPrincipal AuthPrincipal principal) {
        StudentDevice device = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("thiết bị", id));
        device.setStatus(DeviceStatus.REVOKED);
        device.setApprovedBy(userRepository.findById(principal.userId()).orElse(null));
        device.setApprovedAt(Instant.now());
        return DeviceResponse.of(repository.save(device));
    }
}
