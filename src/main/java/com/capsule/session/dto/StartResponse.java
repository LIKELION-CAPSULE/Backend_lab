package com.capsule.session.dto;

import com.capsule.credit.api.CreditTypes.Credits;
import com.capsule.user.api.UserTypes.SettingsInfo;
import java.time.Instant;
import java.util.List;

/**
 * api.md 16 연결 시작. settings = 29의 설정 전체 (2026-11-23).
 * backgrounds = 메이트 배경 전체, background = 지금 배경 code (없는 메이트면 빈 목록 · null, ADR 0032)
 */
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
        Credits credits,
        List<BackgroundView> backgrounds,
        String background) {}
