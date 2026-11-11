package com.capsule.user.controller;

import com.capsule.common.config.ApiTags;
import com.capsule.common.security.LoginUser;
import com.capsule.user.domain.UserMemory;
import com.capsule.user.dto.CreateMemoryRequest;
import com.capsule.user.dto.MeResponse;
import com.capsule.user.dto.MemoryList;
import com.capsule.user.dto.MemoryResponse;
import com.capsule.user.dto.MemoryStatus;
import com.capsule.user.dto.UpdateMeRequest;
import com.capsule.user.service.MemoryService;
import com.capsule.user.service.ProfileService;
import com.capsule.user.service.TokenService;
import com.capsule.user.service.WithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** api.md 8, 10, 13, 14, 27 */
@RestController
@RequestMapping("/api/me")
@Tag(name = ApiTags.ONBOARDING_HOME)
@RequiredArgsConstructor
public class MeController {

    private final ProfileService profiles;
    private final MemoryService memories;
    private final WithdrawalService withdrawals;
    private final TokenService tokens;

    @Operation(summary = "8. 내 정보 조회", description = "내 정보 + 온보딩 필요 여부 + 진행 중 세션(H18 판단) (명세: api.md 8)")
    @GetMapping
    public MeResponse me(@LoginUser long userId) {
        return profiles.me(userId);
    }

    @Operation(summary = "10. 프로필 설정", description = "닉네임 · 아바타 저장 (온보딩 완료) (명세: api.md 10)")
    @PatchMapping
    public MeResponse update(@LoginUser long userId, @RequestBody UpdateMeRequest req) {
        return profiles.update(userId, req);
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

    @Operation(summary = "13. 일정 저장", description = "시험 · 약속 · 목표 일정 저장 (명세: api.md 13)")
    @PostMapping("/memories")
    @ResponseStatus(HttpStatus.CREATED)
    public MemoryResponse createMemory(@LoginUser long userId, @RequestBody CreateMemoryRequest req) {
        return memories.create(userId, req);
    }

    @Operation(
            summary = "14. 내 일정 조회",
            description = "내 일정(시험 · 약속 · 목표) 조회. 달력 점 · 날짜 클릭 · 예정/전체/지난 탭 (명세: api.md 14)")
    @GetMapping("/memories")
    public MemoryList memories(
            @LoginUser long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "UPCOMING") MemoryStatus status,
            @RequestParam(required = false) UserMemory.Type type) {
        return memories.list(userId, from, to, date, status, type);
    }
}
