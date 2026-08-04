package vn.ptit.drl.identity.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import vn.ptit.drl.common.config.DrlProperties;
import vn.ptit.drl.identity.Role;
import vn.ptit.drl.identity.User;

@Service
public class JwtService {

    /** Phân biệt access token và refresh token — chống dùng refresh token để gọi API. */
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(DrlProperties props) {
        byte[] secret = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET quá ngắn (" + secret.length + " byte). HS256 cần tối thiểu 32 byte. "
                    + "Sinh bằng: openssl rand -base64 64");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.accessTtl = Duration.ofMinutes(props.jwt().accessTtlMinutes());
        this.refreshTtl = Duration.ofDays(props.jwt().refreshTtlDays());
    }

    public String generateAccessToken(User user) {
        return build(user, TYPE_ACCESS, accessTtl);
    }

    public String generateRefreshToken(User user) {
        return build(user, TYPE_REFRESH, refreshTtl);
    }

    private String build(User user, String type, Duration ttl) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("role", user.getRole().name())
                .claim("typ", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)));

        // Gắn sẵn studentId để tầng điểm danh không phải truy vấn lại mỗi request.
        if (user.getRole() == Role.STUDENT && user.getStudent() != null) {
            builder.claim("sid", user.getStudent().getId());
        }
        return builder.signWith(key).compact();
    }

    /** @throws JwtException nếu chữ ký sai, token hết hạn, hoặc định dạng hỏng. */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public Claims parseExpecting(String token, String expectedType) {
        Claims claims = parse(token);
        if (!expectedType.equals(claims.get("typ", String.class))) {
            throw new JwtException("Sai loại token: cần " + expectedType);
        }
        return claims;
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }
}
