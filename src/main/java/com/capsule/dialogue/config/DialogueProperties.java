package com.capsule.dialogue.config;

import com.capsule.relation.api.RelationTypes.SignalType;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 대화 기능 설정.
 *
 * @param chatPipeline 메시지 답장(20)에 쓸 파이프라인: SIMPLE · ENGINE
 * @param reactionLlmTriggers 상태 반응(19)을 LLM으로 만들 trigger. 나머지는 고정 대사 (J-04)
 * @param emotions 답장·반응 감정 후보 (명세 19·20)
 * @param signalDetector 사용자 말의 종류를 고르는 쪽: PATTERN(A안) · DECIDER(B안)
 * @param signalMinConfidence DECIDER일 때 이보다 낮은 확신도면 NEUTRAL(0점)
 * @param severeMinConfidence 패턴이 혐오 · 성적이라고 할 때 결정 모델 확인의 최소 확신도. 낮으면 RUDE
 * @param chatScores 말의 종류별 점수표 (없는 종류는 0)
 * @param chatRepeatWindow · chatRepeatLimit 최근 window턴 안에 같은 종류가 이미 limit번 있으면 오르는 점수는 0
 * @param reactionContextLines 상태 반응 LLM에 같이 넣을 이번 세션 최근 대화 수
 * @param memoryMax 캐릭터가 아는 사실 최대 개수
 * @param recapMax 지난 세션 흐름 카드 최대 장수
 * @param memoryAsync 기억 정리를 답장 뒤 따로 돌릴지 (false면 요청 안에서 바로)
 */
@ConfigurationProperties("capsule.dialogue")
public record DialogueProperties(
        String chatPipeline,
        List<String> reactionLlmTriggers,
        List<String> emotions,
        String signalDetector,
        double signalMinConfidence,
        double severeMinConfidence,
        Map<SignalType, ScoreRule> chatScores,
        int chatRepeatWindow,
        int chatRepeatLimit,
        int reactionContextLines,
        int memoryMax,
        int recapMax,
        boolean memoryAsync) {

    public static final String PATTERN = "PATTERN";
    public static final String DECIDER = "DECIDER";

    /**
     * @param points 더할 점수 (lossPercent가 있으면 최소 감점)
     * @param lossPercent 0보다 크면 지금 값의 이 %를 깎는다
     * @param oncePerSession 세션당 1번만 점수
     * @param repeatPoints 같은 세션에서 또 나오면 이 점수 (null이면 points)
     */
    public record ScoreRule(int points, int lossPercent, boolean oncePerSession, Integer repeatPoints) {}
}
