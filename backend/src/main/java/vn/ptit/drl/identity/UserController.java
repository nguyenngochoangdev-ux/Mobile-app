package vn.ptit.drl.identity;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import vn.ptit.drl.common.web.BusinessException;
import vn.ptit.drl.common.web.NotFoundException;
import vn.ptit.drl.identity.jwt.AuthPrincipal;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Quản lý tài khoản")
public class UserController {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, message = "Mật khẩu mới tối thiểu 8 ký tự")
            String newPassword) {}

    /** Không bao giờ chứa {@code passwordHash}. */
    public record UserResponse(Long id, String username, Role role, Long studentId,
                               String mssv, Long staffOrgId, Boolean enabled, Instant createdAt) {

        static UserResponse of(User u) {
            return new UserResponse(u.getId(), u.getUsername(), u.getRole(),
                    u.getStudent() == null ? null : u.getStudent().getId(),
                    u.getStudent() == null ? null : u.getStudent().getMssv(),
                    u.getStaffOrg() == null ? null : u.getStaffOrg().getId(),
                    u.getEnabled(), u.getCreatedAt());
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Danh sách tài khoản")
    @Transactional(readOnly = true)
    public Page<UserResponse> list(Pageable pageable) {
        return repository.findAll(pageable).map(UserResponse::of);
    }

    @PatchMapping("/{id}/enabled")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Khoá hoặc mở khoá tài khoản")
    @Transactional
    public UserResponse setEnabled(@PathVariable Long id, @RequestParam boolean enabled,
                                   @AuthenticationPrincipal AuthPrincipal principal) {
        if (id.equals(principal.userId())) {
            throw new BusinessException("Không thể tự khoá tài khoản của chính mình");
        }
        User user = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("tài khoản", id));
        user.setEnabled(enabled);
        return UserResponse.of(repository.save(user));
    }

    @PostMapping("/me/change-password")
    @Operation(summary = "Tự đổi mật khẩu")
    @Transactional
    public UserResponse changePassword(@Valid @RequestBody ChangePasswordRequest req,
                                       @AuthenticationPrincipal AuthPrincipal principal) {
        User user = repository.findById(principal.userId())
                .orElseThrow(() -> new NotFoundException("tài khoản", principal.userId()));

        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("Mật khẩu hiện tại không đúng");
        }
        if (req.currentPassword().equals(req.newPassword())) {
            throw new BusinessException("Mật khẩu mới phải khác mật khẩu cũ");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        // Token cũ vẫn còn hiệu lực tới khi hết hạn (access 30 phút). Thu hồi ngay cần
        // token blacklist hoặc cột token_version — chưa làm, ghi vào phần hạn chế.
        return UserResponse.of(repository.save(user));
    }
}
