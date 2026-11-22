package com.capsule.session.dto;

import com.capsule.relation.api.RelationTypes.StudySettlement;
import java.time.Instant;

/** api.md 22 · 23 연결 끊기 · 연결 기록 */
public record ReportResponse(
        long connectionId,
        Instant startedAt,
        Instant endedAt,
        String endType,
        int connectedSeconds,
        int presentSeconds,
        int awaySeconds,
        int messageCount,
        int returnCount,
        MateRef mate,
        StudySettlement relation,
        String closingLine) {

    public record MateRef(long id, String name, String illustrationUrl, String myRole) {}
}
