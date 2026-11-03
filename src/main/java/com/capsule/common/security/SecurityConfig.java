package com.capsule.common.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** JWT(HS256) 검증·발급 설정 (ADR 0011). 액세스 토큰의 sub = 사용자 id. */
@Configuration
class SecurityConfig {

    /** 로그인 없이 부르는 API (명세 '인증: 불필요') */
    private static final RequestMatcher PUBLIC = publicEndpoints();

    private static RequestMatcher publicEndpoints() {
        PathPatternRequestMatcher.Builder path = PathPatternRequestMatcher.withDefaults();
        return new OrRequestMatcher(
                path.matcher(HttpMethod.GET, "/api/terms"),
                path.matcher(HttpMethod.POST, "/api/auth/email/verification"),
                path.matcher(HttpMethod.POST, "/api/auth/email/verification/confirm"),
                path.matcher(HttpMethod.POST, "/api/auth/email/signup"),
                path.matcher(HttpMethod.POST, "/api/auth/email/login"),
                path.matcher(HttpMethod.POST, "/api/auth/social/*"),
                path.matcher(HttpMethod.POST, "/api/auth/refresh"),
                path.matcher("/actuator/health/**"),
                path.matcher("/error")); // 필터 단계 예외가 /error로 넘어올 때 401로 덮이지 않게
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()) // 액세스 토큰은 헤더. 리프레시 쿠키는 SameSite로 막음
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        a -> a.requestMatchers(PUBLIC).permitAll().anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults())
                        .bearerTokenResolver(ignoreTokenOnPublicEndpoints())
                        .authenticationEntryPoint(unauthorized()))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized()));
        return http.build();
    }

    /**
     * 공개 API에서는 Authorization 헤더를 읽지 않는다.
     * 안 그러면 만료된 액세스 토큰이 붙은 채로 갱신(/api/auth/refresh)을 부를 때 갱신 전에 401이 난다.
     */
    private static BearerTokenResolver ignoreTokenOnPublicEndpoints() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> PUBLIC.matches(request) ? null : delegate.resolve(request);
    }

    /** 토큰이 있는데 안 맞으면 AUTH_TOKEN_EXPIRED(프론트가 갱신을 시도), 아예 없으면 UNAUTHORIZED */
    private static AuthenticationEntryPoint unauthorized() {
        return (request, response, e) -> {
            String code = request.getHeader(HttpHeaders.AUTHORIZATION) != null ? "AUTH_TOKEN_EXPIRED" : "UNAUTHORIZED";
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + code + "\",\"detail\":{}}");
        };
    }

    @Bean
    SecretKey jwtSecretKey(AuthProperties props) {
        byte[] secret =
                props.jwtSecret() == null ? new byte[0] : props.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("capsule.auth.jwt-secret(CAPSULE_JWT_SECRET)은 32바이트 이상이어야 함");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${capsule.cors.allowed-origins:}") List<String> origins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins.stream().filter(o -> !o.isBlank()).toList());
        cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        cors.setAllowCredentials(true); // 리프레시 토큰 쿠키
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
