package com.capsule.relation.service;

import com.capsule.common.time.ClockConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/**
 * 친밀도 숫자 (PRD 8장 추천값, 확정 필요 · 베타 데이터로 조정). 운영 설정으로 빼려면 application.yml(공용)에 칸을 더해야 해서
 * 일단 상수로 둔다
 */
public final class IntimacyPolicy {

    /** 하루(오전 5시 기준) 인정 한도 2시간. DB CHECK relation_daily.credited_seconds ≤ 7200과 같아야 한다 */
    public static final int DAILY_CAP_SECONDS = 7200;

    /** 단계 n이 되는 누적 인정 시간 (1단계 = 0) : 0 · 1시간 · 5시간 · 15시간 · 40시간 */
    private static final int[] STAGE_STARTS = {0, 3600, 18000, 54000, 144000};

    public static final int MAX_STAGE = STAGE_STARTS.length;

    /** 하루는 오전 5시(KST)에 바뀐다 (PRD, session의 '오늘'과 같음) */
    private static final LocalTime DAY_STARTS_AT = LocalTime.of(5, 0);

    private IntimacyPolicy() {}

    public static LocalDate dayOf(Instant at) {
        ZonedDateTime local = at.atZone(ClockConfig.KST);
        return local.toLocalTime().isBefore(DAY_STARTS_AT) ? local.toLocalDate().minusDays(1) : local.toLocalDate();
    }

    public static int stageOf(int creditedSeconds) {
        int stage = 1;
        for (int i = 1; i < STAGE_STARTS.length; i++) {
            if (creditedSeconds >= STAGE_STARTS[i]) stage = i + 1;
        }
        return stage;
    }

    /** 다음 단계까지 남은 인정 시간. 마지막 단계면 null */
    public static Integer secondsToNextStage(int creditedSeconds) {
        int stage = stageOf(creditedSeconds);
        return stage >= MAX_STAGE ? null : STAGE_STARTS[stage] - creditedSeconds;
    }

    /** 지금 단계 안에서 얼마나 왔는지 0~100 (5단계면 100). SCR-21 · 37 친밀도 막대 (2026-11-24) */
    public static int stageProgress(int creditedSeconds) {
        int stage = stageOf(creditedSeconds);
        if (stage >= MAX_STAGE) return 100;
        int from = STAGE_STARTS[stage - 1];
        int to = STAGE_STARTS[stage];
        return (int) Math.min(100, Math.max(0, (long) (creditedSeconds - from) * 100 / (to - from)));
    }

    /** 이번 인정 시간: 그날 남은 한도까지, 그날 대화가 없으면 절반 */
    public static int credit(int presentSeconds, int creditedToday, boolean chattedToday) {
        int capped = Math.max(0, Math.min(presentSeconds, DAILY_CAP_SECONDS - creditedToday));
        return chattedToday ? capped : capped / 2;
    }
}
