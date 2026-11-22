package com.capsule.dialogue.dto;

/** api.md 40 답변 평가. rating이 null이면 취소 */
public record MessageRatingRequest(Rating rating) {

    public enum Rating {
        UP,
        DOWN
    }
}
