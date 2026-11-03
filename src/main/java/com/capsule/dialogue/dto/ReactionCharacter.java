package com.capsule.dialogue.dto;

import com.capsule.content.api.ContentTypes.ExpressionRef;
import com.capsule.content.api.ContentTypes.MotionRef;

/** 이벤트 반응 캐릭터 한 명분. emotion은 LLM이 정하고, 고정 대사(fallback)면 null */
public record ReactionCharacter(
        long characterId,
        String content,
        String emotion,
        MotionRef motion,
        ExpressionRef expression,
        boolean fallback,
        String audioBase64) {}
