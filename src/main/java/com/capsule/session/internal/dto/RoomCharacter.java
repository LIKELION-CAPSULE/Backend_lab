package com.capsule.session.internal.dto;

import java.util.List;

public record RoomCharacter(
        long id, String name, String profileImageUrl, List<String> personality, RelationBrief relation) {}
