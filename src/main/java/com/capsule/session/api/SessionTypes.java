package com.capsule.session.api;

import java.time.Instant;

/** session 모듈이 내보내는 DTO 모음. */
public final class SessionTypes {

    private SessionTypes() {}

    /**
     * endedAt이 null이면 진행 중. characterId = 이번 연결의 메이트.
     * roomId는 2026-11-23 스터디룸 삭제 뒤 늘 0 (모양을 지키려고 남김, 연결 단계 정리 때 지움)
     */
    public record SessionInfo(long id, long userId, long roomId, long characterId, Instant startedAt, Instant endedAt) {

        public boolean inProgress() {
            return endedAt == null;
        }
    }

    /**
     * 상태 변화 전송으로 저장된 이벤트 (api.md 18).
     *
     * @param type 상태 FOCUS · AWAY · RETURN · BREAK 또는 순간 동작 DROWSY · YAWN · WAVE · HEAD_TURN
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
     * @param targetMinutes 옛 목표 시간. 2026-11-23 목표 시간이 없어져 늘 null (공부 포기 판정 꺼짐)
     */
    public record SessionClosing(
            long sessionId,
            long userId,
            long characterId,
            String characterName,
            String endType,
            int studySeconds,
            Integer targetMinutes) {}
}
