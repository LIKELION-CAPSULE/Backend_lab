package com.capsule.dialogue.dto;

import com.capsule.content.api.ContentTypes.ExpressionRef;
import com.capsule.content.api.ContentTypes.MotionRef;
import com.capsule.relation.api.RelationTypes.AffinityChange;
import java.util.UUID;

public record ReactionResponse(
        UUID requestId,
        String trigger,
        String content,
        MotionRef motion,
        ExpressionRef expression,
        AffinityChange affinity,
        boolean fallback,
        String audioBase64) {}
