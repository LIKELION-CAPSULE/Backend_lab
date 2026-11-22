package com.capsule.session.dto;

import com.capsule.credit.api.CreditTypes.Credits;
import com.capsule.user.api.UserTypes.SettingsInfo;
import java.time.Instant;

/** api.md 16 연결 시작. settings = 29의 설정 전체 (2026-11-23) */
public record StartResponse(
        long connectionId,
        Instant startedAt,
        Figure mate,
        Figure avatar,
        Me me,
        SettingsInfo settings,
        Instant serverTime,
        boolean firstMeet,
        ReactionView greeting,
        Integer minorRemainingSeconds,
        IntimacyView.Now relation,
        Credits credits) {}
