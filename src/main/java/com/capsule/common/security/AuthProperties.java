package com.capsule.common.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 인증 설정 (ADR 0011). jwtSecret은 환경변수로만 받는다. */
@ConfigurationProperties("capsule.auth")
public record AuthProperties(
        String jwtSecret, Duration refreshTokenTtl, boolean refreshCookieSecure, String refreshCookieSameSite) {}
