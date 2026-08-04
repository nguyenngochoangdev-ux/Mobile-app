package vn.ptit.drl.identity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import vn.ptit.drl.common.web.BusinessException;
import vn.ptit.drl.common.web.NotFoundException;
import vn.ptit.drl.identity.jwt.AuthPrincipal;
import vn.ptit.drl.identity.jwt.JwtService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Đăng nhập, làm mới token")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record TokenResponse(String accessToken, String refreshToken,
                                String tokenType, long expiresIn) {}

    public record MeResponse(Long userId, String username, String role,
                             Long studentId, String mssv, String fullName) {}

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập bằng username + password")
    @Transactional(readOnly = true)
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        User user = userRepository.findByUsername(req.username())
                // Thông báo giống hệt trường hợp sai mật khẩu — không tiết lộ
                // username nào tồn tại.
                .orElseThrow(() -> new BusinessException("Sai tài khoản hoặc mật khẩu"));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BusinessException("Sai tài khoản hoặc mật khẩu");
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            throw new BusinessException("Tài khoản đã bị khóa");
        }

        return new TokenResponse(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user),
                "Bearer",
                jwtService.accessTtlSeconds());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Đổi refresh token lấy access token mới")
    @Transactional(readOnly = true)
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        Claims claims;
        try {
            claims = jwtService.parseExpecting(req.refreshToken(), JwtService.TYPE_REFRESH);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException("Refresh token không hợp lệ hoặc đã hết hạn");
        }

        User user = userRepository.findById(claims.get("uid", Long.class))
                .orElseThrow(() -> new NotFoundException("user", claims.get("uid")));

        if (Boolean.FALSE.equals(user.getEnabled())) {
            throw new BusinessException("Tài khoản đã bị khóa");
        }

        return new TokenResponse(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user),
                "Bearer",
                jwtService.accessTtlSeconds());
    }

    @GetMapping("/me")
    @Operation(summary = "Thông tin tài khoản đang đăng nhập")
    @Transactional(readOnly = true)
    public MeResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new NotFoundException("user", principal.userId()));

        Student s = user.getStudent();
        return new MeResponse(user.getId(), user.getUsername(), user.getRole().name(),
                s == null ? null : s.getId(),
                s == null ? null : s.getMssv(),
                s == null ? null : s.getFullName());
    }
}
