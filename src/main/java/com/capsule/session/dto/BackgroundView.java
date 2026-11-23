package com.capsule.session.dto;

import com.capsule.content.api.ContentTypes.BackgroundInfo;

/** 연결 화면 배경 하나 (api.md 16 backgrounds, ADR 0032) */
public record BackgroundView(String code, String name, String imageUrl) {

    public static BackgroundView of(BackgroundInfo b) {
        return new BackgroundView(b.code(), b.name(), b.imageUrl());
    }
}
