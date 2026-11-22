package com.capsule.session.dto;

import java.time.Instant;
import java.util.List;

/** api.md 37 내 메이트. 끝난 연결이 있는 메이트만, 마지막 연결 시작이 최근인 순 */
public record MyMatesResponse(List<MyMate> mates) {

    /**
     * @param firstMetAt · lastConnectedAt 끝난 연결 중 처음 · 마지막 연결 시작 시각
     * @param connectionCount 끝난 연결 수
     */
    public record MyMate(
            long id,
            String name,
            String cardImageUrl,
            Instant firstMetAt,
            Instant lastConnectedAt,
            int connectionCount,
            int totalPresentSeconds,
            int totalConnectedSeconds,
            Relation relation,
            List<Unlock> unlocks,
            List<Recent> recent) {}

    /** nextStageName · nextStageSeconds는 5단계면 null */
    public record Relation(int stage, String stageName, String nextStageName, Integer nextStageSeconds) {}

    /** 이 메이트의 해금 콘텐츠 하나. 열리는 단계 순 */
    public record Unlock(
            long id,
            String type,
            String title,
            int unlockStage,
            String unlockStageName,
            String expressionName,
            String assetUrl,
            boolean unlocked) {}

    /** 최근 끝난 연결 (누르면 23) */
    public record Recent(long connectionId, Instant endedAt, int presentSeconds, int connectedSeconds) {}
}
