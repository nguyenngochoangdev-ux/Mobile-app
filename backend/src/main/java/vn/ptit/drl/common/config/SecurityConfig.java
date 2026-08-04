package vn.ptit.drl.common.config;

import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

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
                    .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
