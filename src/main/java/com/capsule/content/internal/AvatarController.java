package com.capsule.content.internal;

import com.capsule.content.api.AvatarQuery;
import com.capsule.content.api.ContentTypes.ModelInfo;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AvatarController {

    private final AvatarQuery avatars;

    AvatarController(AvatarQuery avatars) {
        this.avatars = avatars;
    }

    /** api.md 9 */
    @GetMapping("/api/avatars")
    AvatarsResponse list() {
        return new AvatarsResponse(avatars.activeAvatars().stream()
                .map(a -> new AvatarItem(a.id(), a.name(), a.thumbnailUrl(), a.model()))
                .toList());
    }

    record AvatarsResponse(List<AvatarItem> avatars) {}

    record AvatarItem(long id, String name, String thumbnailUrl, ModelInfo model) {}
}
