package com.capsule.session.dto;

/** 홈(api.md 11) 포스터 아래 캐릭터. homeImageUrl = 포스터 배경 위에 겹치는 전신 그림, 없으면 null */
public record CharacterBrief(long id, String name, String profileImageUrl, String homeImageUrl) {}
