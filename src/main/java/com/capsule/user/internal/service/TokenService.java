package com.capsule.user.internal.service;

import com.capsule.common.error.ApiException;
import com.capsule.common.security.AuthProperties;
import com.capsule.user.internal.dto.Issued;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
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
public class TokenService {

    public static final String REFRESH_COOKIE = "refresh_token";
    private static final Duration ACCESS_TTL = Duration.ofMinutes(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtEncoder jwtEncoder;
    private final StringRedisTemplate redis;
    private final AuthProperties props;
    private final Clock clock;

    TokenService(JwtEncoder jwtEncoder, StringRedisTemplate redis, AuthProperties props, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.redis = redis;
        this.props = props;
        this.clock = clock;
    }

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
        return new Issued(accessToken, cookie(refreshToken, props.refreshTokenTtl()));
    }

    /** 옛 토큰은 지우고(GETDEL) 새로 발급. 이미 쓴 토큰을 다시 내면 401 */
    public Issued rotate(String refreshToken) {
        String userId = refreshToken == null ? null : redis.opsForValue().getAndDelete(refreshKey(refreshToken));
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID");
        }
        return issue(Long.parseLong(userId));
    }

    /** 리프레시 토큰을 지우고, 쿠키를 비우는 Set-Cookie 값을 돌려준다 */
    public String revoke(String refreshToken) {
        if (refreshToken != null) {
            redis.delete(refreshKey(refreshToken));
        }
        return cookie("", Duration.ZERO);
    }

    static String randomToken() {
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
}
