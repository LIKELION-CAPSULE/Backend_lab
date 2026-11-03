package com.capsule.session.internal.dto;

/** 캐릭터·아바타 공용. 아바타의 profileImageUrl = 아바타 썸네일 */
public record Figure(
        long id, String name, String profileImageUrl, String modelJsonUrl, double scale, int offsetX, int offsetY) {}
