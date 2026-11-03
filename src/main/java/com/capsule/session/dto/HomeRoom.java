package com.capsule.session.dto;

import java.util.List;

public record HomeRoom(long id, String name, String thumbnailUrl, List<CharacterBrief> characters) {}
