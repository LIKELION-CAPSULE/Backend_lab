package com.capsule.user.dto;

/** 설정 7개 (api.md 29 · 30). 저장한 적 없는 키는 기본값 */
public record UserSettings(
        String talkFrequency,
        boolean voiceEnabled,
        int voiceVolume,
        String voiceSpeed,
        boolean pipBubble,
        String defaultMode,
        String pipSize) {}
