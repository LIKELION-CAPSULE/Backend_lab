package com.capsule.session.dto;

/**
 * 홈(api.md 11) 포스터 카드의 캐릭터. profileImageUrl = 배경 위에 겹치는 전신 그림(characters.home_image_url),
 * 없으면 캐릭터 사진. 룸 상세 · 공부 화면의 profileImageUrl과 다른 그림일 수 있다 (2026-11-06)
 */
public record CharacterBrief(long id, String name, String profileImageUrl) {}
