package com.capsule.dialogue.controller;

import com.capsule.common.error.ApiException;
import com.capsule.common.ratelimit.RateLimiter;
import com.capsule.dialogue.config.LabAdmins;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 실험실 관리자 로그인 (ADR 0027). 이 주소만 인증 없이 열려 있어서 시도 수를 막는다: 접속 주소마다 1분 10번, 전체 1분 30번.
 * 전체 제한이 따로 있는 이유: 프록시 뒤(개발 서버)에서는 접속 주소가 X-Forwarded-For에서 와서 보내는 쪽이 매번 바꿀 수 있다.
 * 대가로 누가 1분 동안 30번을 채우면 그 1분은 정상 로그인도 막힌다 (내부 도구라 감수)
 */
@Hidden // 내부 도구: 프론트용 Swagger에 넣지 않는다
@RestController
@ConditionalOnProperty(name = "capsule.llm.lab.enabled", havingValue = "true")
@RequiredArgsConstructor
public class LabLoginController {

    private static final Logger log = LoggerFactory.getLogger(LabLoginController.class);
    private static final int ATTEMPTS = 10;
    private static final int TOTAL_ATTEMPTS = 30;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    public record LoginRequest(String username, String password) {}

    private final LabAdmins admins;
    private final RateLimiter rateLimiter;

    @PostMapping("/internal/llm-lab/api/login")
    public LabAdmins.Session login(@RequestBody LoginRequest body, HttpServletRequest request) {
        String from = request.getRemoteAddr(); // 프록시 뒤에서는 보내는 쪽이 정한 값: 로그 참고용
        if (!rateLimiter.tryAcquire("rate:lab-login:" + from, ATTEMPTS, WINDOW)
                || !rateLimiter.tryAcquire("rate:lab-login", TOTAL_ATTEMPTS, WINDOW))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS");
        LabAdmins.Session session = admins.login(body.username(), body.password())
                .orElseThrow(() -> {
                    // 입력한 아이디는 남기지 않는다: 아이디 칸에 비밀번호를 잘못 치면 그대로 로그에 남아서
                    log.warn("실험실 관리자 로그인 실패 ({})", from);
                    return new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED");
                });
        log.info("실험실 관리자 로그인: {} ({})", session.username(), from);
        return session;
    }
}
