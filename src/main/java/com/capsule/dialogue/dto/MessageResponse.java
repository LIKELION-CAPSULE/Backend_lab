package com.capsule.dialogue.dto;

import com.capsule.content.api.ContentTypes.ExpressionRef;
import com.capsule.content.api.ContentTypes.MotionRef;
import com.capsule.relation.api.RelationTypes.AffinityChange;
import java.util.UUID;

/**
 * 메시지 답장 (명세 20). 요청에서 고른 캐릭터 1명분. content ~ audioBase64는 19번 characters[] 한 칸과 같은 모양.
 * emotion은 LLM이 정하고, 고정 대사(fallback)·재전송이면 null
 */
public record MessageResponse(
        UUID requestId,
        long characterId,
        String content,
        String emotion,
        MotionRef motion,
        ExpressionRef expression,
        AffinityChange affinity,
        boolean fallback,
        String audioBase64) {}
