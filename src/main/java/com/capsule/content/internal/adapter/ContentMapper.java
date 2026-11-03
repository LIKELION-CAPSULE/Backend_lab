package com.capsule.content.internal.adapter;

import com.capsule.content.api.ContentTypes.AvatarInfo;
import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.content.api.ContentTypes.ModelInfo;
import com.capsule.content.internal.domain.Avatar;
import com.capsule.content.internal.domain.CharacterEntity;
import com.capsule.content.internal.domain.Live2dModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 엔티티 → 계약 DTO. 모델 주소에 CDN 주소가 필요해서 빈으로 둔다 */
@Component
class ContentMapper {

    private final String cdnBaseUrl;

    ContentMapper(@Value("${capsule.cdn.base-url}") String cdnBaseUrl) {
        this.cdnBaseUrl = cdnBaseUrl.replaceAll("/+$", "");
    }

    ModelInfo model(Live2dModel m) {
        return new ModelInfo(
                cdnBaseUrl + "/" + m.storagePrefix() + m.modelJsonPath(),
                m.scale().doubleValue(),
                m.offsetX(),
                m.offsetY());
    }

    AvatarInfo avatar(Avatar a) {
        return new AvatarInfo(a.id(), a.name(), a.thumbnailUrl(), a.isActive(), model(a.model()));
    }

    CharacterInfo character(CharacterEntity c) {
        return new CharacterInfo(
                c.id(),
                c.roomId(),
                c.name(),
                c.profileImageUrl(),
                c.personality(),
                c.intro(),
                c.isActive(),
                model(c.model()));
    }
}
