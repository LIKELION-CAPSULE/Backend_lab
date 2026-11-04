package com.capsule.relation.service;

import static com.capsule.relation.api.RelationTypes.MAX_POINTS;
import static com.capsule.relation.api.RelationTypes.MIN_POINTS;

import com.capsule.relation.api.RelationTypes.ChatSignal;
import java.time.ZoneId;

/**
 * 호감도 공식 (판단 백로그 J-01·J-02·J-03·J-06). 기획이 정해지면 이 클래스만 바꾼다.
 * 호감도는 0~100 (%처럼 읽음, ADR 0017): 계속 쌓이지 않고 끝이 있다. 그래서 한 번의 변화는 작게 둔다.
 */
final class AffinityPolicy {

    private AffinityPolicy() {}

    /** 하루 상한을 나누는 날짜 기준 */
    static final ZoneId DAY_ZONE = ZoneId.of("Asia/Seoul");

    static int clamp(int points) {
        return Math.clamp(points, MIN_POINTS, MAX_POINTS);
    }

    // TODO(judgment): J-01 첫 만남 보너스 값 (명세 예시 +5: 이 관계의 첫 FIRST_MEET이 없을 때 세션 첫 FOCUS)
    static final int FIRST_MEET_POINTS = 5;

    // TODO(judgment): J-01 상태 이벤트별 변화량 (명세 예시 AWAY −1 · RETURN +1 · BREAK 0, 첫 만남이 아닌 FOCUS 0, 동작 이벤트 0)
    static int sessionEventDelta(String eventType) {
        return switch (eventType) {
            case "AWAY" -> -1;
            case "RETURN" -> 1;
            default -> 0;
        };
    }

    // TODO(judgment): J-02 공부 시간 → STUDY_TIME 점수 공식. 0~100이라 임시로 10분당 +1 (1분당이면 두 시간이 안 돼 100)
    static int studyTimeDelta(int studySeconds) {
        return studySeconds / 600;
    }

    // TODO(judgment): J-06 ① 신호별 점수표 (임시: 털어놓는 말은 감점하지 않음, 판단 백로그 추천 '섞기')
    static int chatPoints(ChatSignal signal) {
        return switch (signal) {
            case PRAISE, STUDY_WILL -> 2;
            case NEUTRAL, STRUGGLE -> 0;
            case RUDE -> -2;
            case ABUSE -> -5;
        };
    }

    // TODO(judgment): J-06 ② 대화로 오를 수 있는 하루 상한 (한국 날짜). 내려가는 쪽은 상한 없음
    static final int CHAT_DAILY_GAIN_CAP = 5;

    // TODO(judgment): J-03 단계 기준(0~100 중 몇 %부터)과 이름 (임시: 0 '아는 사이' · 30 '친한 사이' · 70 이름 미정)
    private static final int[] LEVEL_POINTS = {0, 30, 70};
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
