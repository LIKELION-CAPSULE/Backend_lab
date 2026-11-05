package com.capsule.dialogue.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 실험실(/internal/llm-lab/**) 전용 보안 체인. 공용 SecurityConfig(JWT)보다 먼저 이 경로만 맡는다. 실험실이 켜졌을 때만 생긴다(local).
 * 기본은 거부: 화면(HTML) 주소만 열고, 나머지는 전부
 * - capsule.llm.lab.token이 비어 있으면 이 컴퓨터(loopback)에서 온 요청만 (같은 와이파이의 다른 사람이 내 키로 호출하지 못하게)
 * - 토큰을 넣었으면 X-Lab-Token 헤더가 같아야 한다 (공용 개발 서버에 열 때)
 * 프록시 뒤(forward-headers-strategy가 none이 아님)에서는 접속 주소를 X-Forwarded-For로 꾸밀 수 있어서 토큰 없이 켜면 시작에 실패한다.
 * 운영 설정(ADR 0026)도 이 경로라 서비스 동작을 바꾸는 권한이 이 토큰 하나에 걸린다.
 * ponytail: 토큰 하나로만 막는다. 공용 서버에 오래 열어 둘 거면 사내 로그인(SSO) 뒤에 둘 것
 */
@Configuration
@ConditionalOnProperty(name = "capsule.llm.lab.enabled", havingValue = "true")
class LabSecurityConfig {

    @Bean
    @Order(0)
    SecurityFilterChain llmLabSecurity(
            HttpSecurity http,
            LlmProperties props,
            LabAdmins admins,
            @Value("${server.forward-headers-strategy:none}") String forwardHeaders)
            throws Exception {
        String token = props.lab().token();
        if (!present(token) && !admins.enabled() && !"none".equalsIgnoreCase(forwardHeaders))
            throw new IllegalStateException("프록시 뒤(server.forward-headers-strategy=" + forwardHeaders
                    + ")에서 실험실을 켜려면 관리자 계정(CAPSULE_ADMIN_USERS_<아이디>)이나 CAPSULE_LLM_LAB_TOKEN이 필요"
                    + " (접속 주소를 꾸밀 수 있음)");
        String required = admins.enabled() ? "LAB_LOGIN_REQUIRED" : "LAB_TOKEN_REQUIRED"; // 화면이 로그인 · 토큰 칸을 고름
        http.securityMatcher("/internal/llm-lab", "/internal/llm-lab/**")
                .csrf(csrf -> csrf.disable()) // 같은 출처 화면에서 JSON으로만 부름. 쿠키 인증을 쓰지 않음
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers(
                                HttpMethod.GET, "/internal/llm-lab", "/internal/llm-lab/", "/internal/llm-lab/flow")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/internal/llm-lab/api/login")
                        .permitAll()
                        .anyRequest()
                        .access((auth, ctx) -> new AuthorizationDecision(allowed(ctx.getRequest(), token, admins))))
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.getWriter()
                            .write("{\"code\":\"" + required + "\",\"message\":\"" + required + "\",\"detail\":{}}");
                }));
        return http.build();
    }

    /**
     * 관리자 로그인 토큰 → 고정 토큰 순서로 본다. 둘 중 하나라도 설정돼 있으면 이 컴퓨터에서 온 요청도 헤더가 있어야 한다.
     * 둘 다 없을 때(로컬 기본)만 loopback으로 통과
     */
    static boolean allowed(HttpServletRequest request, String token, LabAdmins admins) {
        String given = request.getHeader("X-Lab-Token");
        if (given != null) {
            var admin = admins.verify(given);
            if (admin.isPresent()) {
                request.setAttribute(LabAdmins.REQUEST_ATTRIBUTE, admin.get());
                return true;
            }
            if (present(token)
                    && MessageDigest.isEqual(
                            given.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8)))
                return true;
        }
        if (present(token) || admins.enabled()) return false;
        return loopback(request.getRemoteAddr());
    }

    private static boolean present(String s) {
        return s != null && !s.isBlank();
    }

    private static boolean loopback(String address) {
        try {
            return address != null && InetAddress.getByName(address).isLoopbackAddress(); // IP 문자열이라 DNS 조회 없음
        } catch (java.net.UnknownHostException e) {
            return false;
        }
    }
}
