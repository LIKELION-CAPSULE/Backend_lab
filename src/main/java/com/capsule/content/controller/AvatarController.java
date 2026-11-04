package com.capsule.content.controller;

import com.capsule.common.config.ApiTags;
import com.capsule.content.api.AvatarQuery;
import com.capsule.content.dto.AvatarItem;
import com.capsule.content.dto.AvatarsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = ApiTags.ONBOARDING_HOME)
class AvatarController {

    private final AvatarQuery avatars;

    AvatarController(AvatarQuery avatars) {
        this.avatars = avatars;
    }

    /** api.md 9 */
    @Operation(summary = "9. 아바타 목록 조회", description = "완성형 Live2D 아바타 목록 (현재 1개) (명세: api.md 9)")
    @GetMapping("/api/avatars")
    AvatarsResponse list() {
        return new AvatarsResponse(avatars.activeAvatars().stream()
                .map(a -> new AvatarItem(a.id(), a.name(), a.thumbnailUrl(), a.model()))
                .toList());
    }
}
