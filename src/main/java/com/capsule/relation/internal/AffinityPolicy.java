package com.capsule.relation.internal;

/** 호감도 공식 (판단 백로그 J-01~J-03). 기획이 정해지면 이 클래스만 바꾼다. */
final class AffinityPolicy {

    private AffinityPolicy() {}

    // TODO(judgment): J-01 첫 만남 보너스 값 (명세 예시 +5: 이 관계의 첫 FIRST_MEET이 없을 때 세션 첫 FOCUS)
    static final int FIRST_MEET_POINTS = 5;

    // TODO(judgment): J-01 상태 이벤트별 변화량 (명세 예시 AWAY −1 · RETURN +1 · BREAK 0, 첫 만남이 아닌 FOCUS 0)
    static int sessionEventDelta(String eventType) {
        return switch (eventType) {
            case "AWAY" -> -1;
            case "RETURN" -> 1;
            default -> 0;
        };
    }

    // TODO(judgment): J-02 공부 시간 → STUDY_TIME 점수 공식 (Fake: 1분당 +1, 내림)
    static int studyTimeDelta(int studySeconds) {
        return studySeconds / 60;
    }

    // TODO(judgment): J-03 레벨 기준 점수·이름 (Fake: 0점 '아는 사이' · 100점 '친한 사이' · 300점 이름 미정)
    private static final int[] LEVEL_POINTS = {0, 100, 300};
    private static final String[] LEVEL_NAMES = {"아는 사이", "친한 사이", "Lv.3"};

    static int levelOf(int points) {
        int level = 1;
        while (level < LEVEL_POINTS.length && points >= LEVEL_POINTS[level]) level++;
        return level;
    }

    static String levelName(int level) {
        return LEVEL_NAMES[level - 1];
    }

    /** 최고 단계면 null */
    static Integer nextLevelPoints(int level) {
        return level < LEVEL_POINTS.length ? LEVEL_POINTS[level] : null;
    }
}
