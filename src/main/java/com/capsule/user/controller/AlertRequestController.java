package com.capsule.user.controller;

import com.capsule.common.config.ApiTags;
import com.capsule.common.security.LoginUser;
import com.capsule.user.service.AlertRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** api.md 45 출시 알림 신청 · 46 메이트 공개 알림 신청 (2026-11-24) */
@RestController
@Tag(name = ApiTags.ONBOARDING_HOME)
@RequiredArgsConstructor
public class AlertRequestController {

    private final AlertRequestService alerts;

    public record WaitlistRequest(String email) {}

    @Operation(summary = "45. 출시 알림 신청", description = "베타 정원이 찼을 때 이메일만 남김. 로그인 없이 (명세: api.md 45)")
    @PostMapping("/api/beta/waitlist")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void joinWaitlist(@RequestBody WaitlistRequest req, HttpServletRequest http) {
        alerts.joinWaitlist(req.email(), clientIp(http));
    }

    /**
     * Railway 프록시 뒤라 getRemoteAddr()는 프록시 주소다. X-Forwarded-For의 맨 오른쪽 = 프록시가 직접 붙인 보낸 쪽 주소
     * (앞쪽 값은 보내는 쪽이 꾸밀 수 있음). 헤더가 없으면(로컬 · 테스트) 접속 주소
     */
    static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) return http.getRemoteAddr();
        String[] hops = forwarded.split(",");
        return hops[hops.length - 1].strip();
    }

    @Operation(summary = "46. 메이트 공개 알림 신청", description = "준비 중 메이트의 '알림 받기' (명세: api.md 46)")
    @PostMapping("/api/mates/{mateId}/alert")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestMateAlert(@LoginUser long userId, @PathVariable("mateId") long mateId) {
        alerts.requestMateAlert(userId, mateId);
    }
}
