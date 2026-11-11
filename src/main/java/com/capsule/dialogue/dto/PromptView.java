package com.capsule.dialogue.dto;

import com.capsule.dialogue.domain.PromptVersion;
import java.time.Instant;

/** 실험실: 프롬프트 한 버전. characterId null = 공통 */
public record PromptView(
        long id,
        String key,
        Long characterId,
        int version,
        String status,
        String body,
        String note,
        Instant createdAt,
        Instant activatedAt) {

    public static PromptView from(PromptVersion v) {
        return new PromptView(
                v.getId(),
                v.getPromptKey(),
                v.getCharacterId(),
                v.getVersion(),
                v.getStatus(),
                v.getBody(),
                v.getNote(),
                v.getCreatedAt(),
                v.getActivatedAt());
    }
}
