package com.capsule.relation.api;

/** relation 모듈이 내보내는 DTO 모음. */
public final class RelationTypes {

    private RelationTypes() {}

    /** 호감도는 0~100 (%처럼 읽는다). 쌓이는 점수가 아니라 지금 얼마나 가까운지. 0 아래·100 위로는 가지 않는다 */
    public static final int MIN_POINTS = 0;

    public static final int MAX_POINTS = 100;

    /**
     * 사용자가 캐릭터에게 보낸 말 하나가 관계에 주는 신호 (명세 20). dialogue가 결정 모델로 고르고, 점수는 relation이 정한다.
     * meaning은 결정 모델에 보여 줄 설명이라 바꾸면 분류 결과가 바뀐다 (실험실 평가로 확인할 것).
     */
    public enum ChatSignal {
        NEUTRAL("평범한 대화, 질문, 잡담, 공부 내용 이야기"),
        PRAISE("캐릭터를 칭찬하거나, 고마워하거나, 다정하게 대한다"),
        STUDY_WILL("공부하겠다는 다짐이나 계획, 해냈다는 보고를 한다"),
        STRUGGLE("힘들다, 어렵다, 졸리다, 하기 싫다처럼 고민이나 피로를 털어놓는다"),
        RUDE("캐릭터에게 무례하거나, 비꼬거나, 무시한다"),
        ABUSE("욕설, 모욕, 혐오 표현을 한다");

        private final String meaning;

        ChatSignal(String meaning) {
            this.meaning = meaning;
        }

        public String meaning() {
            return meaning;
        }
    }

    /** 아직 안 만났으면 Lv.1 · 0점 · 0초. points는 0~100, nextLevelPoints는 최고 단계면 null */
    public record RelationView(
            long characterId,
            int level,
            String levelName,
            int points,
            int totalStudySeconds,
            Integer nextLevelPoints) {}

    /**
     * 한 번의 이벤트·대화로 바뀐 호감도 (api.md 18·20의 affinity). delta는 실제로 바뀐 만큼이다:
     * 100에서 +2면 0, 1에서 −2면 −1. delta 0이면 변화 없음
     */
    public record AffinityChange(int delta, int points, int level, boolean levelUp) {}

    /**
     * 상태 이벤트 하나 (api.md 18).
     *
     * @param eventType 상태 FOCUS · AWAY · RETURN · BREAK, 동작 DROWSY · YAWN · WAVE · HEAD_TURN (api.md 18, 동작은 지금 0점)
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
