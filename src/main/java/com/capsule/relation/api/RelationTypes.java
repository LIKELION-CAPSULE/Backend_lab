package com.capsule.relation.api;

/** relation 모듈이 내보내는 DTO 모음. */
public final class RelationTypes {

    private RelationTypes() {}

    /** 아직 안 만났으면 Lv.1 · 0점 · 0초. nextLevelPoints는 최고 단계면 null */
    public record RelationView(
            long characterId,
            int level,
            String levelName,
            int points,
            int totalStudySeconds,
            Integer nextLevelPoints) {}

    /** 한 번의 이벤트·대화로 바뀐 호감도 (api.md 19·20의 affinity). delta 0이면 변화 없음 */
    public record AffinityChange(int delta, int points, int level, boolean levelUp) {}

    /**
     * 상태 이벤트 하나 (api.md 18).
     *
     * @param eventType 상태 FOCUS · AWAY · RETURN · BREAK, 동작 DROWSY · YAWN · WAVE (api.md 18, 동작은 지금 0점)
     * @param firstFocusOfSession 이 세션의 첫 FOCUS인지 (처음 만난 날 첫 FOCUS 보너스 판단용)
     */
    public record SessionEventRef(
            long userId,
            long characterId,
            long sessionId,
            long sessionEventId,
            String eventType,
            boolean firstFocusOfSession) {}

    /** 공부 종료 정산 결과 (api.md 22의 relation). pointsBefore = 이 세션 시작 때 점수 */
    public record StudySettlement(
            int levelBefore,
            int levelAfter,
            String levelName,
            int pointsBefore,
            int pointsAfter,
            Integer nextLevelPoints,
            boolean levelUp) {}
}
