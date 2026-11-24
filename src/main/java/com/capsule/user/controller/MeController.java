package com.capsule.user.controller;

import com.capsule.common.config.ApiTags;
import com.capsule.common.response.ApiResponse;
import com.capsule.common.security.LoginUser;
import com.capsule.user.dto.MeResponse;
import com.capsule.user.dto.UpdateMeRequest;
import com.capsule.user.dto.UserSettings;
import com.capsule.user.service.ProfileService;
import com.capsule.user.service.SettingsService;
import com.capsule.user.service.TokenService;
import com.capsule.user.service.WithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** api.md 8, 10, 27, 29, 30 (13 · 14 일정은 2026-11-24 삭제) */
@RestController
@RequestMapping("/api/me")
@Tag(name = ApiTags.ONBOARDING_HOME)
@RequiredArgsConstructor
public class MeController {

    private final ProfileService profiles;
    private final WithdrawalService withdrawals;
    private final TokenService tokens;
    private final SettingsService settings;

    @Operation(summary = "8. 내 정보 조회", description = "내 정보 + 온보딩 필요 여부 + 진행 중 세션(H18 판단) (명세: api.md 8)")
    @GetMapping
    public ApiResponse<MeResponse> me(@LoginUser long userId) {
        return ApiResponse.ok(profiles.me(userId));
    }

    @Operation(summary = "10. 프로필 설정", description = "닉네임 · 아바타 · 생년월일(한 번) · 호칭 · 나에 대해. 보낸 값만 바꿈 (명세: api.md 10)")
    @PatchMapping
    public ApiResponse<MeResponse> update(@LoginUser long userId, @RequestBody UpdateMeRequest req) {
        return ApiResponse.ok(profiles.update(userId, req));
    }

    @Operation(summary = "29. 설정 조회", description = "설정 7개. 저장한 적 없는 값은 기본값 (명세: api.md 29)")
    @GetMapping("/settings")
    public ApiResponse<UserSettings> settings(@LoginUser long userId) {
        return ApiResponse.ok(settings.get(userId));
    }

    @Operation(summary = "30. 설정 변경", description = "보낸 키만 바꾸고 전체 설정을 돌려줌. null이면 기본값 (명세: api.md 30)")
    @PatchMapping("/settings")
    public ApiResponse<UserSettings> updateSettings(@LoginUser long userId, @RequestBody JsonNode body) {
        return ApiResponse.ok(settings.update(userId, body));
    }

    @Operation(
            summary = "27. 회원 탈퇴",
            description = "개인정보를 비우고 일정 · 대화 기억 삭제, 모든 기기 로그아웃. 진행 중 세션이 있으면 409 (명세: api.md 27)")
    @DeleteMapping
    public ResponseEntity<Void> withdraw(@LoginUser long userId) {
        withdrawals.withdraw(userId);
        // 리프레시 쿠키는 경로가 /api/auth라 여기로 오지 않는다. 비우는 Set-Cookie만 보낸다
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, tokens.revoke(null))
                .build();
    }
}
