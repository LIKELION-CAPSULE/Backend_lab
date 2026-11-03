package com.capsule.dialogue.dto;

/** LLM 답장. emotion = 표정 태그, affinityScore = 이 대화의 호감도 점수 (relation이 −5~5로 자름) */
public record LlmReply(String content, String emotion, int affinityScore) {}
