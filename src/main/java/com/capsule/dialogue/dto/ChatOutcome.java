package com.capsule.dialogue.dto;

import com.capsule.relation.api.RelationTypes.SignalType;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 답장 한 턴의 결과.
 *
 * @param emotion 캐릭터 감정 이름. 못 정했으면 null
 * @param signal 사용자 말의 종류 (점수는 ChatScorer · relation이 정함). 파이프라인은 null로 내고 ChatReplier가 채운다
 * @param signalConfidence 결정 모델이 고르거나 확인했으면 그 확신도 0~1, 패턴만 썼으면 null
 * @param trace 실험실에 보여 줄 중간 결과 (쓴 프롬프트 버전 등). 서비스 응답에는 넣지 않는다
 */
public record ChatOutcome(
        String reply, String emotion, SignalType signal, Double signalConfidence, Map<String, Object> trace) {

    public static ChatOutcome of(String reply, String emotion, Map<String, Object> trace) {
        return new ChatOutcome(reply, emotion, null, null, trace);
    }

    public ChatOutcome withSignal(SignalType signal, Double confidence) {
        Map<String, Object> t = new LinkedHashMap<>(trace == null ? Map.of() : trace);
        t.put("signal", signal);
        t.put("signalConfidence", confidence);
        return new ChatOutcome(reply, emotion, signal, confidence, t);
    }
}
