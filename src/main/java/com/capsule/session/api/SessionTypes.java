package com.capsule.session.api;

import java.time.Instant;

/** session 모듈이 내보내는 DTO 모음. */
public final class SessionTypes {

    private SessionTypes() {}

    /** endedAt이 null이면 진행 중. characterId = 이번 세션 캐릭터 (뼈대는 1명) */
    public record SessionInfo(long id, long userId, long roomId, long characterId, Instant startedAt, Instant endedAt) {

        public boolean inProgress() {
            return endedAt == null;
        }
    }

    /**
     * 상태 이벤트 (api.md 18).
     *
     * @param type FOCUS · AWAY · RETURN · BREAK
     * @param firstFocusOfSession 이 세션의 첫 FOCUS (반응 trigger GREETING)
     * @param afterBreak 바로 앞 이벤트가 BREAK인 FOCUS (반응 trigger BREAK_END)
     */
    public record SessionEventInfo(
            long id,
            long sessionId,
            String type,
            Instant occurredAt,
            boolean firstFocusOfSession,
            boolean afterBreak) {}

    /**
     * 공부 종료 정보. endType: NORMAL · AUTO_CLOSED
     *
     * @param targetMinutes 공부 시작 때 정한 목표 시간 (공부 포기 판단, 없으면 null)
     */
    public record SessionClosing(
            long sessionId,
            long userId,
            long characterId,
            String characterName,
            String endType,
            int studySeconds,
            Integer targetMinutes) {

        /** @deprecated 목표 시간을 넘기는 생성자를 쓴다 (공부 포기 대사, 2026-11-05 확장) */
        @Deprecated(forRemoval = true)
        public SessionClosing(
                long sessionId, long userId, long characterId, String characterName, String endType, int studySeconds) {
            this(sessionId, userId, characterId, characterName, endType, studySeconds, null);
        }
    }
}
