package vn.ptit.drl.common.config;

import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import lombok.RequiredArgsConstructor;
import vn.ptit.drl.identity.jwt.JwtAuthFilter;

/*
 * Danh sách đường của PWA phục vụ từ chính backend (xem WebAppConfig).
 *
 * Liệt kê từng đuôi tệp chứ KHÔNG dùng `/**`: một dấu sao kép ở đây mở toàn bộ ứng dụng, và
 * lỗi đó rất khó thấy khi đọc lướt qua cấu hình bảo mật.
 *
 * Mấy đường này không có gì bí mật. Chúng là mã nguồn giao diện, ai tải app về cũng có. Dữ
 * liệu thật vẫn nằm sau /api và vẫn cần JWT.
 */

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] TAI_NGUYEN_TINH = {
            "/", "/index.html", "/manifest.webmanifest",
            "/assets/**", "/registerSW.js", "/sw.js", "/workbox-*.js",
            "/*.png", "/*.svg", "/*.ico", "/*.webmanifest",
            // TWA (Trusted Web Activity) xác minh app bằng cách tự GET đường này và so
            // SHA-256 chữ ký APK với danh sách khai ở đây. Chrome trên điện thoại gọi nó
            // TRƯỚC KHI đăng nhập, nên không thể để sau JWT — 401 ở đây làm app luôn hiện
            // thanh URL (rơi về Custom Tab) dù mọi thứ khác đúng.
            "/.well-known/assetlinks.json"
    };

    private static final String[] ROUTE_CLIENT = {
            "/sv/**", "/cb/**", "/login", "/trinh-chieu/**"
    };


    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // API stateless dùng JWT — không có session, không có form login,
            // nên CSRF không áp dụng.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .exceptionHandling(eh -> eh
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                    // Handler mặc định (AccessDeniedHandlerImpl) gọi sendError(403), khiến
                    // Spring re-dispatch sang /error; filter chain chạy lại nhưng
                    // SecurityContext đã bị xóa (stateless) nên trả về 401 thay vì 403.
                    // Client sẽ tưởng token hết hạn và đi refresh vô ích.
                    // Ghi thẳng response để không phát sinh ERROR dispatch.
                    .accessDeniedHandler((req, res, ex) -> {
                        res.setStatus(HttpStatus.FORBIDDEN.value());
                        res.setContentType("application/json");
                        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
                        res.getWriter().write("""
                                {"status":403,"error":"Forbidden",\
                                "message":"Không đủ quyền thực hiện thao tác này"}""");
                    }))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/**").permitAll()
                    // Swagger mở để sinh TypeScript client cho PWA (quyết định số 3).
                    // TRƯỚC KHI DEPLOY THẬT: đóng lại hoặc đặt sau reverse proxy.
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                    .requestMatchers("/actuator/health").permitAll()
                    // BẮT BUỘC permit /error. Khi một exception thoát khỏi controller,
                    // Spring forward sang /error; nếu đường này bị chặn thì mọi lỗi 500
                    // biến thành 401 và che mất nguyên nhân thật — cực kỳ khó debug.
                    .requestMatchers("/error").permitAll()

                    // ---- PWA phục vụ từ chính backend (xem WebAppConfig) ----------------
                    // Một origin duy nhất cho app và API: điện thoại Android chỉ cài được
                    // PWA khi trang chạy HTTPS, và nếu API nằm ở origin http:// khác thì
                    // trình duyệt chặn thẳng vì mixed content.
                    // Cho cả GET lẫn HEAD. Theo RFC 9110, HEAD phải trả về đúng phần đầu của
                    // GET; mở GET mà quên HEAD làm `curl -I` trả 401 trong khi trình duyệt
                    // vẫn tải trang bình thường. Công cụ giám sát và một số proxy dùng HEAD
                    // để kiểm tra, nên chênh lệch này gây báo động giả.
                    .requestMatchers(HttpMethod.GET, TAI_NGUYEN_TINH).permitAll()
                    .requestMatchers(HttpMethod.HEAD, TAI_NGUYEN_TINH).permitAll()

                    // Route phía client (React Router). Chúng trả về index.html, không trả
                    // dữ liệu — chặn chúng chỉ làm app trắng trang khi người dùng tải lại.
                    .requestMatchers(HttpMethod.GET, ROUTE_CLIENT).permitAll()
                    .requestMatchers(HttpMethod.HEAD, ROUTE_CLIENT).permitAll()

                    .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
