package com.capsule.dialogue.dto;

/** LLM이 만든 이벤트 반응: 말풍선 대사와 그 감정 태그 */
public record LlmReaction(String content, String emotion) {}
