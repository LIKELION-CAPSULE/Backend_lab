package com.capsule.dialogue.dto;

import com.capsule.dialogue.engine.DialogueState;
import com.capsule.relation.api.RelationTypes.ChatSignal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 답장 한 턴의 결과.
 *
 * @param emotion 캐릭터 감정 이름. 못 정했으면 null
 * @param signal 사용자 말의 신호 (결정 모델이 고름, 점수는 relation이 정함). 파이프라인은 null로 내고 ChatReplier가 채운다
 * @param signalConfidence signal의 확신도 0~1. 못 읽었으면 null (signal은 NEUTRAL)
 * @param engineState ENGINE 파이프라인이 바꾼 상태 (저장할 것). SIMPLE이면 null
 * @param trace 실험실에 보여 줄 중간 결과 (쓴 프롬프트 버전, 감독 계획 등). 서비스 응답에는 넣지 않는다
 */
public record ChatOutcome(
        String reply,
        String emotion,
        ChatSignal signal,
        Double signalConfidence,
        DialogueState engineState,
        Map<String, Object> trace) {

    public static ChatOutcome of(String reply, String emotion, DialogueState engineState, Map<String, Object> trace) {
        return new ChatOutcome(reply, emotion, null, null, engineState, trace);
    }

    public ChatOutcome withSignal(ChatSignal signal, Double confidence) {
        Map<String, Object> t = new LinkedHashMap<>(trace == null ? Map.of() : trace);
        t.put("signal", signal);
        t.put("signalConfidence", confidence);
        return new ChatOutcome(reply, emotion, signal, confidence, engineState, t);
    }
}
