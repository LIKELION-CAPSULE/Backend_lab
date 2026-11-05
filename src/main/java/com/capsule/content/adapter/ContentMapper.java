package com.capsule.content.adapter;

import com.capsule.content.api.ContentTypes.AvatarInfo;
import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.content.api.ContentTypes.ModelInfo;
import com.capsule.content.domain.Avatar;
import com.capsule.content.domain.CharacterEntity;
import com.capsule.content.domain.Live2dModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 엔티티 → 계약 DTO. 모델 · 이미지 주소에 CDN 주소가 필요해서 빈으로 둔다 */
@Component
class ContentMapper {

    private final String cdnBaseUrl;

    ContentMapper(@Value("${capsule.cdn.base-url}") String cdnBaseUrl) {
        this.cdnBaseUrl = cdnBaseUrl.replaceAll("/+$", "");
    }

    /**
     * 이미지 칸 값 → 내려줄 주소 (schema.sql 이미지 칸 규칙, 2026-11-05).
     * 상대 경로면 CDN 주소를 붙이고, 전체 URL(http · https)이면 그대로. 비어 있으면 null
     */
    String asset(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) return null;
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) return pathOrUrl;
        return cdnBaseUrl + "/" + pathOrUrl.replaceAll("^/+", "");
    }

    ModelInfo model(Live2dModel m) {
        return new ModelInfo(
                cdnBaseUrl + "/" + m.storagePrefix() + m.modelJsonPath(),
                m.scale().doubleValue(),
                m.offsetX(),
                m.offsetY());
    }

    AvatarInfo avatar(Avatar a) {
        return new AvatarInfo(a.id(), a.name(), asset(a.thumbnailUrl()), a.isActive(), model(a.model()));
    }

    CharacterInfo character(CharacterEntity c) {
        return new CharacterInfo(
                c.id(),
                c.roomId(),
                c.name(),
                asset(c.profileImageUrl()),
                c.personality(),
                c.intro(),
                c.isActive(),
                model(c.model()),
                asset(c.homeImageUrl()));
    }
}
