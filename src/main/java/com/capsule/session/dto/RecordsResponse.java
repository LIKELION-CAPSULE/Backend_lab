package com.capsule.session.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * api.md 38 내 기록. 연결은 시작한 날(오전 5시 기준)에 세고 끝난 연결만.
 * buckets는 WEEK · MONTH면 날짜별(YYYY-MM-DD), YEAR면 월별(YYYY-MM), 연결이 없는 칸도 0
 */
public record RecordsResponse(
        LocalDate from,
        LocalDate to,
        int totalPresentSeconds,
        int totalConnectedSeconds,
        int connectedDays,
        int connectionCount,
        List<Bucket> buckets,
        List<MateSummary> mates,
        List<Recent> recent) {

    public record Bucket(String key, int presentSeconds, int connectedSeconds) {}

    /** stage = 지금 친밀도 단계 */
    public record MateSummary(
            long mateId, String name, int presentSeconds, int connectedSeconds, int connectionCount, int stage) {}

    public record Recent(
            long connectionId,
            long mateId,
            String mateName,
            Instant endedAt,
            int presentSeconds,
            int connectedSeconds) {}
}
