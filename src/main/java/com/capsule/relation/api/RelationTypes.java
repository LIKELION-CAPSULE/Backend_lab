package com.capsule.relation.api;

/** relation 모듈이 내보내는 DTO 모음. */
public final class RelationTypes {

    private RelationTypes() {}

    /**
     * 호감도는 0~100 (%처럼 읽는다). 쌓이는 점수가 아니라 지금 얼마나 가까운지. 0 아래·100 위로는 가지 않는다.
     * 설정이 아니라 상수: DB CHECK user_character_relations_points_range(0~100, V20261104_165439)와 같아야 한다
     */
    public static final int MIN_POINTS = 0;

    public static final int MAX_POINTS = 100;

    /** 처음 만나면 50에서 시작 (PM 확정). 상수: DB 기본값 relation_points DEFAULT 50(V20261104_165439)과 같아야 한다 */
    public static final int START_POINTS = 50;

    /**
     * 사용자가 캐릭터에게 보낸 말의 종류 (명세 20). 종류는 코드, 점수는 캐릭터 콘텐츠가 정한다.
     * meaning은 결정 모델(Jev)이 사용자 말을 보고 하나를 고를 때 보는 기준이다(명세 20, 2026-11-07). 바꾸면 분류가 바뀐다.
     * 이웃 종류와 헷갈리는 경계(칭찬 ↔ 애정, 불평 ↔ 놀림 ↔ 무례)도 여기에 적는다.
     */
    public enum SignalType {
        NEUTRAL("평범한 말: 공부 내용 질문, 일정 · 날씨 같은 잡담, 앞 말에 대한 보통의 대답. 다른 종류에 뚜렷하게 들지 않으면 이것"),
        AFFECTION("캐릭터를 좋아하거나 가깝게 느끼는 마음을 드러낸다 (좋아해, 보고 싶었어, 너랑 공부하니까 좋다, 네가 있어서 든든해). "
                + "외모 · 능력을 높이 사는 말은 COMPLIMENT"),
        COMPLIMENT("캐릭터의 외모 · 능력 · 말을 좋게 평가한다 (멋있다, 똑똑하다, 설명 잘한다, 미친 천재다). 부정하거나 비꼬는 말(안 귀엽거든)은 아니다"),
        THANKS("캐릭터에게 고마워한다 (고마워, 덕분이야, 땡큐, ㄱㅅ)"),
        CONFIDE("자기 고민이나 힘든 마음을 털어놓는다 (요즘 너무 힘들어, 시험이 불안해, 포기하고 싶어). 공부가 지겹다는 투정은 COMPLAINT"),
        SHARE("좋든 나쁘든 자기 공부 성과나 근황을 들려준다 (문제 다 풀었어, 오늘 3시간 했어, 시험 망했어). 힘든 마음을 털어놓으면 CONFIDE"),
        CURIOUS("캐릭터 자신에 대해 궁금해서 묻는다 (넌 뭐 좋아해?, 너는 오늘 뭐 했어?, 네가 사는 곳은 어때?). 공부 내용을 묻는 말은 NEUTRAL"),
        LOW_EFFORT("대화를 이어 갈 마음이 안 보이는 성의 없는 단답 (ㅇㅇ, ㄴㄴ, 몰라). 캐릭터 말에 웃는 반응(ㅋㅋ)은 NEUTRAL"),
        COMPLAINT("공부나 상황에 짜증 · 불평을 하거나 캐릭터의 말을 귀찮아한다 (지겹다, 재미없다, 잔소리 그만). 캐릭터를 깎아내리면 TEASE, 욕이면 RUDE"),
        TEASE("캐릭터를 놀리거나 깎아내린다. 욕은 아니다 (찌질하다, 한심하다, 겁쟁이, 안 귀엽거든)"),
        RUDE("캐릭터에게 욕을 하거나 무례하게 군다 (꺼져, 닥쳐, 욕설). 감탄으로 쓴 거친 말(미친 천재다)은 아니다"),
        HATE_OR_SEXUAL("혐오 표현이나 성적인 말을 한다. 혐오 표현은 성별 · 나이 · 지역 · 장애 · 인종 · 종교 · 성소수자를 비하하는 말과 패드립 등");

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
     * @param points 더할 점수. lossPercent가 있으면 최소 감점 (예: −10)
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
            int nextLevelSeconds) {}

    /**
     * 한 번의 이벤트·대화로 바뀐 호감도 (api.md 18·20의 affinity). delta는 실제로 바뀐 만큼이다:
     * 100에서 +2면 0, 1에서 −2면 −1. delta 0이면 변화 없음.
     * level은 공부 시간 레벨이라 이벤트·대화로는 바뀌지 않는다 (levelUp은 늘 false, 지울 예정)
     */
    public record AffinityChange(
            int delta, int points, int level, @Deprecated boolean levelUp, int stage, String stageName) {}

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
            Integer awaySeconds) {}

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
            int nextLevelSeconds) {}
}
