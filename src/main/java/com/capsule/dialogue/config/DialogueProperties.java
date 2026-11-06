package com.capsule.dialogue.config;

import com.capsule.relation.api.RelationTypes.SignalType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 대화 기능 설정.
 *
 * @param chatPipeline 메시지 답장(20)에 쓸 파이프라인: SCENE · SIMPLE
 * @param reactionLlmTriggers 상태 반응(19)을 LLM으로 만들 trigger. 나머지는 고정 대사 (J-04)
 * @param emotions 답장·반응 감정 후보 (명세 19·20)
 * @param signalMinConfidence 결정 모델이 고른 말의 종류가 이보다 낮은 확신도면 NEUTRAL(0점)
 * @param severeMinConfidence 혐오 · 성적(HATE_OR_SEXUAL)으로 인정할 최소 확신도. 낮으면 RUDE
 * @param chatScores 말의 종류별 점수표 (없는 종류는 0)
 * @param chatRepeatWindow · chatRepeatLimit 최근 window턴 안에 같은 종류가 이미 limit번 있으면 오르는 점수는 0
 * @param reactionContextLines 상태 반응 LLM에 같이 넣을 이번 세션 최근 대화 수
 * @param memoryMax 캐릭터가 아는 사실 최대 개수
 * @param recapMax 지난 세션 흐름 카드 최대 장수
 * @param memoryAsync 기억 정리를 답장 뒤 따로 돌릴지 (false면 요청 안에서 바로)
 * @param memoryEnabled 매 턴 아는 사실을 정리할지. 끄면 새 기억을 만들지 않는다 (호출 비용 0, PM이 데모데이 뒤로 미룰 때)
 * @param llmBudget 명세 19·20·22: 이 안에 LLM이 못 만들면 고정 대사. 실험실은 넘었는지 표시만
 * @param inFlightWait · inFlightPoll 같은 requestId가 처리 중이면 기다리는 한도와 확인 간격 (명세 20)
 * @param messageLimit · messageLimitWindow 사용자 한 명이 window 안에 보낼 수 있는 메시지 수 (명세 20, 넘으면 429)
 * @param chattyMessages · chattyWindow 최근 window 안 사용자 말이 이번 말까지 이만큼이면 수다가 길어진 것 (J-14)
 * @param giveUpRatio 직접 끝냈는데 목표 시간의 이 비율 미만이면 공부 포기 (J-15)
 * @param awayIntensityMax 상태 반응 AWAY intensity의 최대 (명세 19)
 * @param replyMinSentences · replyMaxSentences 채팅 답장 길이 'N~M문장 안팎' (명세 20, 공부 중 · 쉬는 시간 같음). 상황 카드 '지금:' 줄에 들어간다
 * @param historyLines 답장 LLM에 넘길 이번 세션 대화 수 (J-05)
 * @param signalContextLines 결정 모델(말의 종류 · 기억 정리)에 같이 보여 줄 앞 대화 수 (J-05)
 * @param factPickWait 아는 사실 고르기를 기다리는 한도. 답장이 llmBudget 안에 나가야 해서 짧게 (J-05)
 * @param contentCacheTtl DB 캐릭터 설정을 들고 있는 시간 (실험실에서 고치면 바로 비움)
 * @param contentMaxCursors 순서대로 돌려 쓰는 상황 대사 자리를 몇 개까지 들고 있을지 (넘으면 오래된 것부터 버림)
 */
@ConfigurationProperties("capsule.dialogue")
public record DialogueProperties(
        String chatPipeline,
        List<String> reactionLlmTriggers,
        List<String> emotions,
        double signalMinConfidence,
        double severeMinConfidence,
        Map<SignalType, ScoreRule> chatScores,
        int chatRepeatWindow,
        int chatRepeatLimit,
        int reactionContextLines,
        int memoryMax,
        int recapMax,
        boolean memoryAsync,
        boolean memoryEnabled,
        Duration llmBudget,
        Duration inFlightWait,
        Duration inFlightPoll,
        int messageLimit,
        Duration messageLimitWindow,
        int chattyMessages,
        Duration chattyWindow,
        double giveUpRatio,
        int awayIntensityMax,
        int replyMinSentences,
        int replyMaxSentences,
        int historyLines,
        int signalContextLines,
        Duration factPickWait,
        Duration contentCacheTtl,
        int contentMaxCursors) {

    /**
     * @param points 더할 점수 (lossPercent가 있으면 최소 감점)
     * @param lossPercent 0보다 크면 지금 값의 이 %를 깎는다
     * @param oncePerSession 세션당 1번만 점수
     * @param repeatPoints 같은 세션에서 또 나오면 이 점수 (null이면 points)
     */
    public record ScoreRule(int points, int lossPercent, boolean oncePerSession, Integer repeatPoints) {}
}
