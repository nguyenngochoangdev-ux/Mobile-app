package vn.ptit.drl.identity.jwt;

/**
 * Danh tính đã xác thực, lấy từ claim của access token.
 *
 * @param studentId chỉ có giá trị khi role = STUDENT, ngược lại null.
 */
public record AuthPrincipal(Long userId, String username, String role, Long studentId) {
}
