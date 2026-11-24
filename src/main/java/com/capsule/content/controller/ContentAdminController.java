package com.capsule.content.controller;

import com.capsule.common.response.ApiResponse;
import com.capsule.content.dto.ContentAdminRequests.AvatarSaved;
import com.capsule.content.dto.ContentAdminRequests.CreateAvatar;
import com.capsule.content.dto.ContentAdminRequests.CreateMate;
import com.capsule.content.dto.ContentAdminRequests.MateSaved;
import com.capsule.content.dto.ContentAdminRequests.ModelRegistered;
import com.capsule.content.dto.ContentAdminRequests.RegisterModel;
import com.capsule.content.dto.ContentAdminRequests.SetModel;
import com.capsule.content.dto.ContentAdminRequests.SetRuntime;
import com.capsule.content.dto.ContentAdminRequests.UpdateAvatar;
import com.capsule.content.service.ContentAdminService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 콘텐츠 관리용 API (ADR 0033): 모델 · 메이트 등록. 부르는 쪽은 모델 제작 도구 Aliving2D의 '캡슐에 올리기'.
 * capsule.content.admin.token(환경변수 CAPSULE_CONTENT_ADMIN_TOKEN)이 있을 때만 생기고, 그 토큰으로만 부른다
 * (ContentAdminSecurityConfig). 앱이 쓰는 API가 아니라 Swagger에서 숨긴다
 */
@Hidden
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "capsule.content.admin.token")
public class ContentAdminController {

    private final ContentAdminService admin;

    @PostMapping("/internal/content/models")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ModelRegistered> registerModel(@RequestBody RegisterModel body) {
        return ApiResponse.ok(admin.registerModel(body));
    }

    @PostMapping("/internal/content/mates")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MateSaved> createMate(@RequestBody CreateMate body) {
        return ApiResponse.ok(admin.createMate(body));
    }

    /** 이미 등록된 모델의 Aliving2D 엔진 경로 (2026-11-25). 세 칸을 통째로 바꾼다 (보내지 않은 칸은 비움) */
    @PutMapping("/internal/content/models/{modelId}/runtime")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setRuntime(@PathVariable long modelId, @RequestBody SetRuntime body) {
        admin.setRuntime(modelId, body);
    }

    /** 아바타 (api.md 9, 2026-11-25) */
    @PostMapping("/internal/content/avatars")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AvatarSaved> createAvatar(@RequestBody CreateAvatar body) {
        return ApiResponse.ok(admin.createAvatar(body));
    }

    @PatchMapping("/internal/content/avatars/{avatarId}")
    public ApiResponse<AvatarSaved> updateAvatar(@PathVariable long avatarId, @RequestBody UpdateAvatar body) {
        return ApiResponse.ok(admin.updateAvatar(avatarId, body));
    }

    @PutMapping("/internal/content/mates/{mateId}/model")
    public ApiResponse<MateSaved> setModel(@PathVariable long mateId, @RequestBody SetModel body) {
        return ApiResponse.ok(admin.setModel(mateId, body.modelId()));
    }
}
