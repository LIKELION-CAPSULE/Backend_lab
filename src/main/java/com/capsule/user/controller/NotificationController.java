package com.capsule.user.controller;

import com.capsule.common.config.ApiTags;
import com.capsule.common.response.ApiResponse;
import com.capsule.common.security.LoginUser;
import com.capsule.user.dto.NotificationList;
import com.capsule.user.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** api.md 43 알림 목록 · 44 알림 읽음 (2026-11-24, ADR 0035) */
@RestController
@Tag(name = ApiTags.ONBOARDING_HOME)
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notifications;

    @Operation(summary = "43. 알림 목록", description = "GNB 벨의 알림 모아 보기. 최신순, before로 다음 페이지 (명세: api.md 43)")
    @GetMapping("/api/notifications")
    public ApiResponse<NotificationList> list(
            @LoginUser long userId,
            @RequestParam(required = false) Long before,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(notifications.list(userId, before, size));
    }

    @Operation(summary = "44. 알림 읽음", description = "ids가 없으면 내 알림 전부 (명세: api.md 44)")
    @PostMapping("/api/notifications/read")
    public ApiResponse<NotificationList.Unread> read(
            @LoginUser long userId, @RequestBody(required = false) NotificationList.ReadRequest req) {
        return ApiResponse.ok(notifications.read(userId, req == null ? null : req.ids()));
    }
}
