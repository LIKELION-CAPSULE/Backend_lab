package com.capsule.dialogue.controller;

import com.capsule.common.error.ApiException;
import com.capsule.common.ratelimit.RateLimiter;
import com.capsule.dialogue.config.LabAdmins;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 실험실 관리자 로그인 (ADR 0027). 이 주소만 인증 없이 열려 있어서, 접속 주소마다 1분에 10번까지만 받는다 */
@Hidden // 내부 도구: 프론트용 Swagger에 넣지 않는다
@RestController
@ConditionalOnProperty(name = "capsule.llm.lab.enabled", havingValue = "true")
class LabLoginController {

    private static final Logger log = LoggerFactory.getLogger(LabLoginController.class);
    static final int ATTEMPTS = 10;

    record LoginRequest(String username, String password) {}

    private final LabAdmins admins;
    private final RateLimiter rateLimiter;

    LabLoginController(LabAdmins admins, RateLimiter rateLimiter) {
        this.admins = admins;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/internal/llm-lab/api/login")
    LabAdmins.Session login(@RequestBody LoginRequest body, HttpServletRequest request) {
        String from = request.getRemoteAddr();
        if (!rateLimiter.tryAcquire("rate:lab-login:" + from, ATTEMPTS, Duration.ofMinutes(1)))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS");
        LabAdmins.Session session = admins.login(body.username(), body.password())
                .orElseThrow(() -> {
                    log.warn("실험실 관리자 로그인 실패: {} ({})", body.username(), from);
                    return new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED");
                });
        log.info("실험실 관리자 로그인: {} ({})", session.username(), from);
        return session;
    }
}
