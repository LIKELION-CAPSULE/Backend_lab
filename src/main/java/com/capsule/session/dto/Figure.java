package com.capsule.session.dto;

/**
 * 메이트 · 아바타 공용. 아바타의 profileImageUrl = 아바타 썸네일. format = CUBISM · ALIVING2D_V2 (2026-11-23).
 * engineUrl · playerUrl · reactionsUrl = Aliving2D 런타임 (없으면 null, 2026-11-25)
 */
public record Figure(
        long id,
        String name,
        String profileImageUrl,
        String format,
        String modelJsonUrl,
        double scale,
        int offsetX,
        int offsetY,
        String engineUrl,
        String playerUrl,
        String reactionsUrl) {}
