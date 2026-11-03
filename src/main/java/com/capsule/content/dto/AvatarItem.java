package com.capsule.content.dto;

import com.capsule.content.api.ContentTypes.ModelInfo;

public record AvatarItem(long id, String name, String thumbnailUrl, ModelInfo model) {}
