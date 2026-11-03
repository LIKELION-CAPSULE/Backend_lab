package com.capsule.session.internal.dto;

import java.util.List;

public record RoomDetail(
        long id,
        String name,
        String description,
        String thumbnailUrl,
        List<RoomCharacter> characters,
        Long activeSessionId) {}
