package com.capsule.session.dto;

/** api.md 16 · 17 me. honorific = 메이트가 부르는 이름, 없으면 null (2026-11-23) */
public record Me(String nickname, String honorific, String profileImageUrl) {}
