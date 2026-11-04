package com.capsule.dialogue.dto;

/** 상태 반응 대사 하나와 그 감정 (명세 19). emotion은 못 정했으면 null */
public record ReactionLine(String content, String emotion) {}
