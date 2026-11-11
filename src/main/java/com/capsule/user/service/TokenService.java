package com.capsule.user.service;

import com.capsule.common.error.ApiException;
import com.capsule.common.security.AuthProperties;
import com.capsule.user.domain.User;
import com.capsule.user.dto.Issued;
import com.capsule.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** 액세스 토큰(JWT 30분) + 리프레시 토큰(무작위 값, Redis, 쓸 때마다 교체) (ADR 0011) */
@Service
@RequiredArgsConstructor
public class TokenService {

    public static final String REFRESH_COOKIE = "refresh_token";
    private static final Duration ACCESS_TTL = Duration.ofMinutes(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtEncoder jwtEncoder;
    private final StringRedisTemplate redis;
    private final AuthProperties props;
    private final UserRepository users;
    private final Clock clock;

    public Issued issue(long userId) {
        Instant now = Instant.now(clock);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(Long.toString(userId))
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TTL))
                .build();
        String accessToken = jwtEncoder
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        String refreshToken = randomToken();
        redis.opsForValue().set(refreshKey(refreshToken), Long.toString(userId), props.refreshTokenTtl());
        // 발급 시각은 따로 둔다. 값 형식(사용자 id)을 바꾸지 않아서 이전 버전으로 되돌려도 토큰이 그대로 돈다 (revokeAll)
        redis.opsForValue().set(issuedAtKey(refreshToken), Long.toString(now.toEpochMilli()), props.refreshTokenTtl());
        return new Issued(accessToken, cookie(refreshToken, props.refreshTokenTtl()));
    }

    /** 옛 토큰은 지우고(GETDEL) 새로 발급. 이미 쓴 토큰 · 모두 로그아웃(revokeAll) 전에 받은 토큰이면 401 */
    public Issued rotate(String refreshToken) {
        String saved = refreshToken == null ? null : redis.opsForValue().getAndDelete(refreshKey(refreshToken));
        if (saved == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID");
        }
        long userId = Long.parseLong(saved);
        String issued = redis.opsForValue().getAndDelete(issuedAtKey(refreshToken));
        long issuedAt = issued == null ? 0 : Long.parseLong(issued); // 발급 시각이 없는 옛 토큰은 가장 오래된 것으로
        String revokedAt = redis.opsForValue().get(revokedAtKey(userId));
        if (revokedAt != null && issuedAt <= Long.parseLong(revokedAt)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID");
        }
        // 탈퇴(api.md 27) 뒤 폐기가 빠졌어도 새 토큰을 주지 않는다
        if (users.findById(userId).filter(User::isActive).isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID");
        }
        return issue(userId);
    }

    /**
     * 그 사용자의 리프레시 토큰을 모두 무효로 (api.md 26 비밀번호 재설정 뒤 모든 기기 로그아웃).
     * 토큰을 하나씩 찾지 않고 '이 시각 전에 받은 토큰은 무효'를 남긴다. 리프레시 토큰 유효기간이 지나면 기록도 지워진다.
     * 액세스 토큰(30분)은 만료될 때까지 그대로 쓰인다
     */
    public void revokeAll(long userId) {
        redis.opsForValue()
                .set(revokedAtKey(userId), Long.toString(Instant.now(clock).toEpochMilli()), props.refreshTokenTtl());
    }

    /** 리프레시 토큰을 지우고, 쿠키를 비우는 Set-Cookie 값을 돌려준다 */
    public String revoke(String refreshToken) {
        if (refreshToken != null) {
            redis.delete(java.util.List.of(refreshKey(refreshToken), issuedAtKey(refreshToken)));
        }
        return cookie("", Duration.ZERO);
    }

    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String cookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(props.refreshCookieSecure())
                .sameSite(props.refreshCookieSameSite())
                .path("/api/auth")
                .maxAge(maxAge)
                .build()
                .toString();
    }

    private static String refreshKey(String token) {
        return "auth:refresh:" + token;
    }

    private static String issuedAtKey(String token) {
        return "auth:refresh-issued-at:" + token;
    }

    private static String revokedAtKey(long userId) {
        return "auth:refresh-revoked-at:" + userId;
    }
}
