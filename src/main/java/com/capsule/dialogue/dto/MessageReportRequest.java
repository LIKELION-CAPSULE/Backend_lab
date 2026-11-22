package com.capsule.dialogue.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** api.md 41 답변 신고. OTHER면 detail 필수 (서비스가 검사) */
public record MessageReportRequest(
        @NotNull Reason reason, @Size(max = 300) String detail) {

    public enum Reason {
        /** 불쾌 · 선정적 */
        INAPPROPRIATE,
        /** 폭력 · 위험 */
        VIOLENT,
        /** 캐릭터와 안 맞음 */
        OUT_OF_CHARACTER,
        OTHER
    }
}
