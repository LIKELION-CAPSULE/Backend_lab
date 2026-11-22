package com.capsule.content.dto;

import com.capsule.content.api.ContentTypes.MateInfo;
import com.capsule.content.api.ContentTypes.ModelInfo;
import java.util.List;

/** 메이트 상세 (api.md 31). hasConversation은 대화방 단계에서 추가 */
public record MateDetailResponse(
        long id,
        String name,
        String oneLiner,
        String ageRating,
        List<String> hashtags,
        String illustrationUrl,
        String voiceSampleUrl,
        String world,
        String myRole,
        String intro,
        ModelInfo model,
        boolean comingSoon) {

    public static MateDetailResponse of(MateInfo m) {
        return new MateDetailResponse(
                m.id(),
                m.name(),
                m.oneLiner(),
                m.ageRating(),
                m.hashtags() == null ? List.of() : m.hashtags(),
                m.illustrationUrl(),
                m.voiceSampleUrl(),
                m.world(),
                m.myRole(),
                m.intro(),
                m.model(),
                m.comingSoon());
    }
}
