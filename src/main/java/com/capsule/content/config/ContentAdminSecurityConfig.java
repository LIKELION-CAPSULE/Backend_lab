package com.capsule.content.config;

import com.capsule.common.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 콘텐츠 관리용 API(/internal/content/**, ADR 0033) 전용 보안 체인. 공용 SecurityConfig(JWT)보다 먼저 이 경로만 맡는다.
 * capsule.content.admin.token(환경변수 CAPSULE_CONTENT_ADMIN_TOKEN)이 있을 때만 생기고, 요청은
 * Authorization: Bearer &lt;그 토큰&gt;이어야 한다(일정 시간 비교). 값이 비어 있으면 모든 요청을 막는다.
 * 토큰이 없으면 이 체인도 API도 없어서, 경로는 공용 체인(JWT)으로 가 막힌다
 */
@Configuration
@ConditionalOnProperty(name = "capsule.content.admin.token")
public class ContentAdminSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain contentAdminSecurity(
            HttpSecurity http, @Value("${capsule.content.admin.token}") String token) throws Exception {
        http.securityMatcher("/internal/content/**")
                .csrf(csrf -> csrf.disable()) // 도구가 토큰으로만 부름. 쿠키 인증을 쓰지 않음
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest()
                        .access((auth, ctx) -> new AuthorizationDecision(allowed(ctx.getRequest(), token))))
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) -> ErrorResponse.write(
                                response, HttpServletResponse.SC_UNAUTHORIZED, "CONTENT_ADMIN_TOKEN_REQUIRED"))
                        .accessDeniedHandler((request, response, ex) -> ErrorResponse.write(
                                response, HttpServletResponse.SC_UNAUTHORIZED, "CONTENT_ADMIN_TOKEN_REQUIRED")));
        return http.build();
    }

    public static boolean allowed(HttpServletRequest request, String token) {
        if (token == null || token.isBlank()) return false;
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return false;
        return MessageDigest.isEqual(
                header.substring(7).trim().getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }
}
