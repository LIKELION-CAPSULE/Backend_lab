package com.capsule.user.dto;

/** api.md 42 요청. score는 RATING만(1~4), text는 BUG · SUGGESTION이면 필수(1,000자 이하) */
public record FeedbackRequest(String kind, Integer score, String text, Long connectionId) {}
