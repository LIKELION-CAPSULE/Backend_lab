package com.capsule.dialogue.dto;

import java.time.Instant;
import java.util.List;

public record EvalCaseView(
        long id,
        String name,
        long characterId,
        List<String> turns,
        LabConfig context,
        String rubric,
        Instant createdAt) {}
