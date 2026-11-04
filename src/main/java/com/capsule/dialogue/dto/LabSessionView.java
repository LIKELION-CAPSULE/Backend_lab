package com.capsule.dialogue.dto;

import java.time.Instant;
import java.util.List;

/** 실험실: 대화방. 목록에서는 turns가 비어 있다 */
public record LabSessionView(
        long id,
        String title,
        long characterId,
        String pipeline,
        LabConfig config,
        long turnCount,
        Instant createdAt,
        Instant updatedAt,
        List<LabTurnView> turns) {}
