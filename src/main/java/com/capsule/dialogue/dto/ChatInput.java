package com.capsule.dialogue.dto;

import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.dialogue.engine.DialogueState;
import java.util.List;
import java.util.Map;

/**
 * 답장 파이프라인 입력. 메시지 전송(20)은 DB 값으로, 실험실은 LabConfig로 채운다. 파이프라인은 둘을 구분하지 않는다.
 * 기억은 세 겹 (ADR 0022): facts(아는 사실) · summary(지난 세션 흐름 카드) · history(이번 세션 원문)
 *
 * @param relationLevel 관계 단계 (호감도 구간, 1부터)
 * @param summary 지난 세션 흐름 카드들을 줄마다 (오래된 것부터). 없으면 null
 * @param facts 캐릭터가 아는 사실 한 줄씩
 * @param memories 사용자가 넣은 일정 한 줄씩 (예: "시험: 중간고사 (2026-11-20)")
 * @param history 이번 세션의 앞선 메시지 (이번 userMessage는 빠짐)
 * @param engineState ENGINE 파이프라인만. null이면 새로 시작
 * @param pinnedPrompts 실험실에서 고정한 프롬프트 버전 (키 → id). 서비스는 빈 값
 * @param onBreak 지금 쉬는 시간 · 일시정지 중인지 (답 길이 · 말하는 방식이 달라짐)
 * @param chatty 공부 중인데 최근 수다가 길어졌는지 (캐릭터가 공부로 돌려보냄)
 * @param sessionMinutes 이번 공부를 시작한 지 몇 분 (쉬는 시간 포함, 장시간 공부를 알아보게). 모르면 0
 */
public record ChatInput(
        CharacterInfo character,
        String nickname,
        int relationLevel,
        String summary,
        List<String> facts,
        List<String> memories,
        List<ChatLine> history,
        String userMessage,
        DialogueState engineState,
        Map<String, Long> pinnedPrompts,
        EngineOptions engine,
        boolean onBreak,
        boolean chatty,
        int sessionMinutes) {}
