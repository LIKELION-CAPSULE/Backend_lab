package com.capsule.relation.api;

/** relation 모듈이 내보내는 DTO 모음. */
public final class RelationTypes {

    private RelationTypes() {}

    /** 호감도는 0~100 (%처럼 읽는다). 쌓이는 점수가 아니라 지금 얼마나 가까운지. 0 아래·100 위로는 가지 않는다 */
    public static final int MIN_POINTS = 0;

    public static final int MAX_POINTS = 100;

    /** 처음 만나면 50에서 시작 (PM 확정, DB 기본값과 같음) */
    public static final int START_POINTS = 50;

    /**
     * 사용자가 캐릭터에게 보낸 말의 종류 (명세 20). 종류는 코드, 점수는 캐릭터 콘텐츠가 정한다.
     * 기본은 dialogue가 말 패턴으로 고른다(A안). meaning은 결정 모델(B안 · 심한 말 확인)에 보여 줄 설명이라 바꾸면 분류가 바뀐다.
     */
    public enum SignalType {
        NEUTRAL("평범한 대화, 질문, 잡담, 공부 내용 이야기"),
        COMPLIMENT("캐릭터를 칭찬한다 (예쁘다, 멋있다, 대단하다)"),
        THANKS("캐릭터에게 고마워한다 (고마워, 땡큐, ㄱㅅ)"),
        CONFIDE("자기 고민이나 힘든 마음을 털어놓는다"),
        LOW_EFFORT("성의 없는 짧은 단답 (ㅇㅇ, ㄴㄴ, 몰라)"),
        COMPLAINT("짜증이나 불평을 한다 (지겹다, 재미없다, 잔소리)"),
        RUDE("캐릭터에게 무례하거나 욕을 한다"),
        HATE_OR_SEXUAL("혐오 표현이나 성적인 말을 한다");

        private final String meaning;

        SignalType(String meaning) {
            this.meaning = meaning;
        }

        public String meaning() {
            return meaning;
        }
    }

    /**
     * 대화 한 번의 점수 (dialogue가 캐릭터 점수표와 세션 안 규칙으로 정한 값). 하루 상한 · 0~100 자르기 · 오름 멈춤은 relation이 한다.
     *
     * @param points 더할 점수. lossPercent가 있으면 최소 감점 (예: −5)
     * @param lossPercent 0보다 크면 지금 값의 이 %를 깎는다 (심한 말). points보다 덜 깎이면 points만큼
     */
    public record ChatScore(SignalType signal, int points, int lossPercent) {

        public static ChatScore none() {
            return new ChatScore(SignalType.NEUTRAL, 0, 0);
        }
    }

    /**
     * 아직 안 만났으면 Lv.1 · 50점 · 0초.
     *
     * @param level 함께 공부한 자리 지킴 시간의 레벨 (상한 없음)
     * @param levelName 옛 필드: stageName과 같은 값. 지울 예정 (명세 12)
     * @param points 호감도 0~100
     * @param nextLevelPoints 옛 필드: 늘 null. 지울 예정
     * @param stage 관계 단계 (호감도 구간, 1부터)
     * @param nextLevelSeconds 다음 레벨이 되는 누적 자리 지킴 시간
     */
    public record RelationView(
            long characterId,
            int level,
            @Deprecated String levelName,
            int points,
            int totalStudySeconds,
            @Deprecated Integer nextLevelPoints,
            int stage,
            String stageName,
            int totalPresentSeconds,
            int nextLevelSeconds) {

        /** @deprecated 단계 · 레벨 칸이 있는 생성자를 쓸 것. 옛 테스트 · 브랜치가 옮기면 지운다 */
        @Deprecated(forRemoval = true)
        public RelationView(
                long characterId,
                int level,
                String levelName,
                int points,
                int totalStudySeconds,
                Integer nextLevelPoints) {
            this(characterId, level, levelName, points, totalStudySeconds, nextLevelPoints, 1, levelName, 0, 3600);
        }
    }

    /**
     * 한 번의 이벤트·대화로 바뀐 호감도 (api.md 18·20의 affinity). delta는 실제로 바뀐 만큼이다:
     * 100에서 +2면 0, 1에서 −2면 −1. delta 0이면 변화 없음.
     * level은 공부 시간 레벨이라 이벤트·대화로는 바뀌지 않는다 (levelUp은 늘 false, 지울 예정)
     */
    public record AffinityChange(
            int delta, int points, int level, @Deprecated boolean levelUp, int stage, String stageName) {

        /** @deprecated 단계 칸이 있는 생성자를 쓸 것. 옛 테스트 · 브랜치가 옮기면 지운다 */
        @Deprecated(forRemoval = true)
        public AffinityChange(int delta, int points, int level, boolean levelUp) {
            this(delta, points, level, levelUp, 1, null);
        }
    }

    /**
     * 상태 이벤트 하나 (api.md 18).
     *
     * @param eventType 상태 FOCUS · AWAY · RETURN · BREAK, 동작 DROWSY · YAWN · WAVE · HEAD_TURN (동작은 0점)
     * @param firstFocusOfSession 이 세션의 첫 FOCUS인지 (연속 출석 판단용)
     * @param awaySeconds 이 상태 이벤트가 자리 비움(AWAY)을 끝냈으면 비운 시간(초), 아니면 null (자리 비움 감점용)
     */
    public record SessionEventRef(
            long userId,
            long characterId,
            long sessionId,
            long sessionEventId,
            String eventType,
            boolean firstFocusOfSession,
            Integer awaySeconds) {

        /** @deprecated awaySeconds를 넘기는 생성자를 쓸 것 (없으면 자리 비움 감점이 없다). session이 옮기면 지운다 */
        @Deprecated(forRemoval = true)
        public SessionEventRef(
                long userId,
                long characterId,
                long sessionId,
                long sessionEventId,
                String eventType,
                boolean firstFocusOfSession) {
            this(userId, characterId, sessionId, sessionEventId, eventType, firstFocusOfSession, null);
        }
    }

    /**
     * 공부 종료 정산 결과 (api.md 22의 relation). pointsBefore = 이 세션 시작 때 점수.
     * 레벨은 공부 시간, 단계(stage)는 호감도에서 온다.
     *
     * @param levelName 옛 필드: stageName과 같은 값. 지울 예정
     * @param nextLevelPoints 옛 필드: 늘 null. 지울 예정
     * @param nextLevelSeconds 다음 레벨이 되는 누적 자리 지킴 시간
     */
    public record StudySettlement(
            int levelBefore,
            int levelAfter,
            @Deprecated String levelName,
            int pointsBefore,
            int pointsAfter,
            @Deprecated Integer nextLevelPoints,
            boolean levelUp,
            int stage,
            String stageName,
            int nextLevelSeconds) {

        /** @deprecated 단계 · 레벨 칸이 있는 생성자를 쓸 것. 옛 테스트 · 브랜치가 옮기면 지운다 */
        @Deprecated(forRemoval = true)
        public StudySettlement(
                int levelBefore,
                int levelAfter,
                String levelName,
                int pointsBefore,
                int pointsAfter,
                Integer nextLevelPoints,
                boolean levelUp) {
            this(
                    levelBefore,
                    levelAfter,
                    levelName,
                    pointsBefore,
                    pointsAfter,
                    nextLevelPoints,
                    levelUp,
                    1,
                    levelName,
                    3600);
        }
    }
}
