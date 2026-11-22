package com.capsule.session.service;

import com.capsule.common.time.ClockConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/** 하루는 한국 시간 오전 5시에 바뀐다 (api.md 16 '하루 기준'). 홈 오늘 시간 · 청소년 3시간 · 내 기록(38)이 같이 쓴다 */
public final class ConnectionDay {

    public static final LocalTime DAY_STARTS_AT = LocalTime.of(5, 0);

    private ConnectionDay() {}

    /** 이 시각이 속한 날 (오전 5시 전이면 전날) */
    public static LocalDate dayOf(Instant at) {
        ZonedDateTime local = at.atZone(ClockConfig.KST);
        return local.toLocalTime().isBefore(DAY_STARTS_AT) ? local.toLocalDate().minusDays(1) : local.toLocalDate();
    }

    /** 그날이 시작하는 시각 (그날 오전 5시 KST) */
    public static Instant startOf(LocalDate day) {
        return day.atTime(DAY_STARTS_AT).atZone(ClockConfig.KST).toInstant();
    }
}
