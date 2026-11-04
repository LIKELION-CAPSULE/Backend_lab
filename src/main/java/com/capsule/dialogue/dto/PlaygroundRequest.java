package com.capsule.dialogue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 실험실: LLM 단계 하나를 repeat번 따로 돌린다 (같은 입력의 답이 얼마나 흔들리는지).
 *
 * @param kind chat(20 답장) · memory(기억 만들기 미리보기, 저장 안 함) · reaction(19 상태 반응 AI) · summary(22 세션 카드) · closing(22 완료 대사 AI)
 * @param pipeline chat만: SIMPLE · ENGINE (비우면 서비스 설정)
 * @param history chat: 앞선 대화 / summary: 요약에 합칠 대화
 */
public record PlaygroundRequest(
        @NotBlank String kind,
        @NotNull Long characterId,
        Integer repeat,
        String pipeline,
        LabConfig config,
        String message,
        List<ChatLine> history,
        String trigger,
        Integer intensity,
        String previousSummary,
        Integer studySeconds,
        String endType) {}
