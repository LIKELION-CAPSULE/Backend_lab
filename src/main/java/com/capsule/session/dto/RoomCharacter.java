package com.capsule.session.dto;

import java.util.List;

public record RoomCharacter(
        long id, String name, String profileImageUrl, List<String> personality, RelationBrief relation) {}
