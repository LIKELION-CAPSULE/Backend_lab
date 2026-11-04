package com.capsule.dialogue.dto;

import com.capsule.dialogue.engine.DialogueState;
import java.util.Map;

/**
 * 답장 파이프라인 결과.
 *
 * @param emotion 캐릭터 감정 이름. 못 정했으면 null
 * @param affinity 이 대화의 호감도 점수 (relation이 −5~5로 자름). 파이프라인이 점수를 못 내면 null → 0
 * @param engineState ENGINE 파이프라인이 바꾼 상태 (저장할 것). SIMPLE이면 null
 * @param trace 실험실에 보여 줄 중간 결과 (쓴 프롬프트 버전, 감독 계획 등). 서비스 응답에는 넣지 않는다
 */
public record ChatOutcome(
        String reply, String emotion, Integer affinity, DialogueState engineState, Map<String, Object> trace) {}
