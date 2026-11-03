package com.capsule.session.dto;

import com.capsule.relation.api.RelationTypes.StudySettlement;
import java.time.Instant;

public record ReportResponse(
        long sessionId,
        String roomName,
        Instant startedAt,
        Instant endedAt,
        String endType,
        int studySeconds,
        int presentSeconds,
        int awaySeconds,
        int returnCount,
        CharacterRef character,
        StudySettlement relation,
        String closingLine) {}
