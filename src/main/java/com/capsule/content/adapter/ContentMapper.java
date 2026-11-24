package com.capsule.content.adapter;

import com.capsule.content.api.ContentTypes.AvatarInfo;
import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.content.api.ContentTypes.MateInfo;
import com.capsule.content.api.ContentTypes.ModelInfo;
import com.capsule.content.domain.Avatar;
import com.capsule.content.domain.CharacterEntity;
import com.capsule.content.domain.Live2dModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 엔티티 → 계약 DTO. 모델 · 이미지 주소에 CDN 주소가 필요해서 빈으로 둔다 */
@Component
public class ContentMapper {

    private final String cdnBaseUrl;

    public ContentMapper(@Value("${capsule.cdn.base-url}") String cdnBaseUrl) {
        this.cdnBaseUrl = cdnBaseUrl.replaceAll("/+$", "");
    }

    /**
     * 이미지 칸 값 → 내려줄 주소 (schema.sql 이미지 칸 규칙, 2026-11-05).
     * 상대 경로면 CDN 주소를 붙이고, 전체 URL(http · https)이면 그대로. 비어 있으면 null
     */
    public String asset(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) return null;
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) return pathOrUrl;
        return cdnBaseUrl + "/" + pathOrUrl.replaceAll("^/+", "");
    }

    /** 준비 중 메이트처럼 모델이 없으면 null */
    public ModelInfo model(Live2dModel m) {
        if (m == null) return null;
        return new ModelInfo(
                m.getFormat(),
                cdnBaseUrl + "/" + m.getStoragePrefix() + m.getModelJsonPath(),
                m.getScale().doubleValue(),
                m.getOffsetX(),
                m.getOffsetY(),
                asset(m.getEnginePath()),
                asset(m.getPlayerPath()),
                asset(m.getReactionsPath()));
    }

    public AvatarInfo avatar(Avatar a) {
        return new AvatarInfo(a.getId(), a.getName(), asset(a.getThumbnailUrl()), a.isActive(), model(a.getModel()));
    }

    public MateInfo mate(CharacterEntity c) {
        return new MateInfo(
                c.getId(),
                c.getName(),
                c.getOneLiner(),
                c.getAgeRating(),
                c.getHashtags(),
                asset(c.getIllustrationUrl()),
                asset(c.getCardImageUrl()),
                asset(c.getVoiceSampleUrl()),
                c.getWorld(),
                c.getMyRole(),
                c.getIntro(),
                model(c.getModel()),
                c.getStageNames(),
                c.isComingSoon(),
                c.isActive());
    }

    public CharacterInfo character(CharacterEntity c) {
        return new CharacterInfo(
                c.getId(),
                0, // 룸이 없어짐 (2026-11-23). CharacterInfo 모양을 지키려고 0
                c.getName(),
                asset(c.getProfileImageUrl()),
                c.getHashtags(), // 옛 성격 태그는 해시태그로 옮겼다
                c.getIntro(),
                c.isActive(),
                model(c.getModel()),
                asset(c.getHomeImageUrl()));
    }
}
