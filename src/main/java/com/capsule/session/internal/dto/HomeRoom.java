package com.capsule.session.internal.dto;

import java.util.List;

public record HomeRoom(long id, String name, String thumbnailUrl, List<CharacterBrief> characters) {}
