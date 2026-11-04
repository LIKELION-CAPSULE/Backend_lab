package com.capsule.dialogue.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 대화 기능 설정.
 *
 * @param chatPipeline 메시지 답장(20)에 쓸 파이프라인: SIMPLE · ENGINE
 * @param reactionLlmTriggers 상태 반응(19)을 LLM으로 만들 trigger (상태 변화). 나머지(동작 이벤트)는 고정 대사 (J-04)
 * @param emotions 답장·반응 감정 후보 (명세 19·20)
 * @param signalMinConfidence 사용자 말 신호를 믿을 최소 확신도. 이보다 낮으면 NEUTRAL(0점)
 * @param reactionContextLines 상태 반응 LLM에 같이 넣을 이번 세션 최근 대화 수
 */
@ConfigurationProperties("capsule.dialogue")
public record DialogueProperties(
        String chatPipeline,
        List<String> reactionLlmTriggers,
        List<String> emotions,
        double signalMinConfidence,
        int reactionContextLines) {}
