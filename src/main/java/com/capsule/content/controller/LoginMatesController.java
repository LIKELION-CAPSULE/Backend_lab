package com.capsule.content.controller;

import com.capsule.common.config.ApiTags;
import com.capsule.common.error.ApiException;
import com.capsule.common.error.CommonErrorCode;
import com.capsule.common.ratelimit.RateLimiter;
import com.capsule.common.response.ApiResponse;
import com.capsule.content.api.ContentTypes.MateInfo;
import com.capsule.content.api.MateQuery;
import com.capsule.content.dto.LoginMatesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** api.md 48 로그인 화면 메이트 (2026-11-25). 로그인 없이 부른다 */
@RestController
@Tag(name = ApiTags.ONBOARDING_HOME)
@RequiredArgsConstructor
public class LoginMatesController {

    /** 로그인 없이 부르는 API라 같은 IP 1분 60번 (명세 48) */
    private static final int PER_MINUTE = 60;

    private static final String ALL_AGES = "ALL";

    private final MateQuery mates;
    private final RateLimiter rateLimiter;

    @Operation(
            summary = "48. 로그인 화면 메이트",
            description = "로그인 전 화면에서 움직일 메이트와 모델. 공개 · 활성 · 전연령 · 모델이 있는 메이트만 (명세: api.md 48)")
    @GetMapping("/api/login-mates")
    public ResponseEntity<ApiResponse<LoginMatesResponse>> list(HttpServletRequest http) {
        if (!rateLimiter.tryAcquire("rl:login-mates:" + clientIp(http), PER_MINUTE, Duration.ofMinutes(1))) {
            throw new ApiException(CommonErrorCode.TOO_MANY_REQUESTS);
        }
        LoginMatesResponse body = new LoginMatesResponse(mates.activeMates().stream()
                .filter(m -> !m.comingSoon() && m.model() != null && ALL_AGES.equals(m.ageRating()))
                .map(LoginMatesController::view)
                .toList());
        // 응답은 짧게 캐시 (모델 파일 자체는 버전 경로라 버킷에서 긴 캐시)
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(ApiResponse.ok(body));
    }

    private static LoginMatesResponse.Mate view(MateInfo m) {
        // model은 31 메이트 상세와 같은 매퍼 · 같은 값 (프론트가 로그인 뒤 다시 불러오지 않게)
        return new LoginMatesResponse.Mate(m.id(), m.name(), m.ageRating(), m.cardImageUrl(), m.model());
    }

    /** Railway 프록시 뒤: X-Forwarded-For 맨 오른쪽 = 프록시가 붙인 보낸 쪽 주소. 없으면 접속 주소 (user 45와 같은 규칙) */
    static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) return http.getRemoteAddr();
        String[] hops = forwarded.split(",");
        return hops[hops.length - 1].strip();
    }
}
