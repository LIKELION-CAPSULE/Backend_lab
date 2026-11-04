package com.capsule.dialogue.dto;

import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.dialogue.engine.DialogueState;
import java.util.List;
import java.util.Map;

/**
 * 답장 파이프라인 입력. 메시지 전송(20)은 DB 값으로, 실험실은 LabConfig로 채운다. 파이프라인은 둘을 구분하지 않는다.
 *
 * @param memories 대화에 넣을 일정 한 줄씩 (예: "시험: 중간고사 (2026-11-20)")
 * @param history 이번 세션의 앞선 메시지 (이번 userMessage는 빠짐)
 * @param engineState ENGINE 파이프라인만. null이면 새로 시작
 * @param pinnedPrompts 실험실에서 고정한 프롬프트 버전 (키 → id). 서비스는 빈 값
 */
public record ChatInput(
        CharacterInfo character,
        String nickname,
        int relationLevel,
        String summary,
        List<String> memories,
        List<ChatLine> history,
        String userMessage,
        DialogueState engineState,
        Map<String, Long> pinnedPrompts,
        EngineOptions engine) {}
