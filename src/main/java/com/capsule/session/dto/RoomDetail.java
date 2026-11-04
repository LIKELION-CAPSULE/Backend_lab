package com.capsule.session.dto;

import java.util.List;

public record RoomDetail(
        long id,
        String name,
        String description,
        String thumbnailUrl,
        String bannerUrl, // H04 가로 배너, 없으면 null (api.md 12)
        List<RoomCharacter> characters,
        Long activeSessionId) {}
