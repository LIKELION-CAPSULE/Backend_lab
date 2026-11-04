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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** api.md 8, 10, 13, 14 */
@RestController
@RequestMapping("/api/me")
@Tag(name = ApiTags.ONBOARDING_HOME)
class MeController {

    private final ProfileService profiles;
    private final MemoryService memories;

    MeController(ProfileService profiles, MemoryService memories) {
        this.profiles = profiles;
        this.memories = memories;
    }

    @Operation(summary = "8. 내 정보 조회", description = "내 정보 + 온보딩 필요 여부 + 진행 중 세션(H18 판단) (명세: api.md 8)")
    @GetMapping
    MeResponse me(@LoginUser long userId) {
        return profiles.me(userId);
    }

    @Operation(summary = "10. 프로필 설정", description = "닉네임 · 아바타 저장 (온보딩 완료) (명세: api.md 10)")
    @PatchMapping
    MeResponse update(@LoginUser long userId, @RequestBody UpdateMeRequest req) {
        return profiles.update(userId, req);
    }

    @Operation(summary = "13. 일정 저장", description = "시험 · 약속 · 목표 일정 저장 (명세: api.md 13)")
    @PostMapping("/memories")
    @ResponseStatus(HttpStatus.CREATED)
    MemoryResponse createMemory(@LoginUser long userId, @RequestBody CreateMemoryRequest req) {
        return memories.create(userId, req);
    }

    @Operation(
            summary = "14. 내 일정 조회",
            description = "내 일정(시험 · 약속 · 목표) 조회. 달력 점 · 날짜 클릭 · 예정/전체/지난 탭 (명세: api.md 14)")
    @GetMapping("/memories")
    MemoryList memories(
            @LoginUser long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "UPCOMING") MemoryStatus status,
            @RequestParam(required = false) UserMemory.Type type) {
        return memories.list(userId, from, to, date, status, type);
    }
}
