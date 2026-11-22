package com.capsule.user.domain;

import com.capsule.common.error.ApiException;
import com.capsule.common.time.ClockConfig;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;

/** 생년월일 · 만 나이 (api.md 4 · 8 · 10). 나이는 한국 날짜 기준 */
public final class Age {

    public static final int SIGNUP_MIN = 14;
    public static final int ADULT = 19;

    private Age() {}

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ClockConfig.KST));
    }

    public static int of(LocalDate birthDate, LocalDate today) {
        return Period.between(birthDate, today).getYears();
    }

    /** YYYY-MM-DD, 미래 날짜 아님 → 만 14세 미만이면 UNDER_14 */
    public static LocalDate validBirthDate(String raw, LocalDate today) {
        LocalDate birthDate;
        try {
            birthDate = LocalDate.parse(raw == null ? "" : raw.strip());
        } catch (DateTimeParseException e) {
            throw new ApiException(UserErrorCode.INVALID_BIRTH_DATE);
        }
        if (birthDate.isAfter(today)) {
            throw new ApiException(UserErrorCode.INVALID_BIRTH_DATE);
        }
        if (of(birthDate, today) < SIGNUP_MIN) {
            throw new ApiException(UserErrorCode.UNDER_14);
        }
        return birthDate;
    }
}
