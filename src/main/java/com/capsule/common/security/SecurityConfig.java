package com.capsule.common.security;

import com.capsule.common.error.CommonErrorCode;
import com.capsule.common.error.ErrorResponse;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** JWT(HS256) 검증·발급 설정 (ADR 0011). 액세스 토큰의 sub = 사용자 id. */
@Configuration
public class SecurityConfig {

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
                // 제공자는 카카오만 (api.md 6). 와일드카드면 토큰 없이 없는 경로(/social/GOOGLE)가 401 대신 404 (#40)
                path.matcher(HttpMethod.POST, "/api/auth/social/KAKAO"),
                path.matcher(HttpMethod.POST, "/api/auth/social/KAKAO/signup"), // api.md 49 카카오 가입 완료
                path.matcher(HttpMethod.POST, "/api/auth/refresh"),
                // 리프레시 쿠키만으로 처리. 액세스 토큰이 만료돼도 쿠키를 지워야 공용 시연 기기에서 다음 사람이 이전 계정으로 갱신되지 않음
                path.matcher(HttpMethod.POST, "/api/auth/logout"),
                path.matcher(HttpMethod.POST, "/api/auth/password/reset-request"), // api.md 25 · 26 비밀번호 찾기
                path.matcher(HttpMethod.POST, "/api/auth/password/reset"),
                // api.md 39 초대 코드 확인 (로그인 전 초대 링크 랜딩). 같은 모양인 35 /api/referrals/me는 로그인 필요
                new AndRequestMatcher(
                        path.matcher(HttpMethod.GET, "/api/referrals/{code}"),
                        new NegatedRequestMatcher(path.matcher(HttpMethod.GET, "/api/referrals/me"))),
                // api.md 45 출시 알림 신청: 베타 정원이 차서 가입 못 한 사람이 이메일만 남김 (2026-11-24)
                path.matcher(HttpMethod.POST, "/api/beta/waitlist"),
                // api.md 48 로그인 화면 메이트: 로그인 전 화면에서 메이트 모델을 받음 (2026-11-25)
                path.matcher(HttpMethod.GET, "/api/login-mates"),
                path.matcher("/actuator/health/**"),
                // Swagger(ADR 0019). springdoc을 켠 local 프로필에서만 응답하고, 꺼진 prod에서는 404
                path.matcher(HttpMethod.GET, "/swagger-ui.html"),
                path.matcher(HttpMethod.GET, "/swagger-ui/**"),
                path.matcher(HttpMethod.GET, "/v3/api-docs/**"),
                path.matcher("/error")); // 필터 단계 예외가 /error로 넘어올 때 401로 덮이지 않게
    }

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
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
        return (request, response, e) -> ErrorResponse.write(
                response,
                request.getHeader(HttpHeaders.AUTHORIZATION) != null
                        ? CommonErrorCode.AUTH_TOKEN_EXPIRED
                        : CommonErrorCode.UNAUTHORIZED);
    }

    @Bean
    public SecretKey jwtSecretKey(AuthProperties props) {
        byte[] secret =
                props.jwtSecret() == null ? new byte[0] : props.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("capsule.auth.jwt-secret(CAPSULE_JWT_SECRET)은 32바이트 이상이어야 함");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${capsule.cors.allowed-origins:}") List<String> origins) {
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
