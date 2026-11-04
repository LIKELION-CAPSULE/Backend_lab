package com.capsule.dialogue.dto;

import com.capsule.dialogue.config.DialogueProperties.ScoreRule;
import com.capsule.relation.api.RelationTypes.SignalType;
import java.util.List;
import java.util.Map;

/**
 * 캐릭터 한 명의 대사 설정 전체 (DB: character_profiles · character_* · worlds · world_*, ADR 0022 결정 15).
 * 장면 카드 파이프라인 · 상황 대사 · 점수표가 이것만 보고 돈다: 캐릭터를 더해도 코드는 바뀌지 않는다.
 * 실험실 API가 이 모양 그대로 JSON으로 읽고 쓴다.
 *
 * @param expressionFallbacks 에셋이 없는 표정 → 대신 쓸 표정
 * @param stageTones 관계 단계 → 말투 지시 한 줄
 */
public record CharacterContent(
        long characterId,
        String code,
        String speaker,
        String bible,
        String rules,
        String safety,
        List<String> expressions,
        Map<String, String> expressionFallbacks,
        Map<Integer, String> stageTones,
        String studyStyle,
        String breakStyle,
        World world,
        List<Move> moves,
        List<Line> lines,
        Map<SignalType, ScoreRule> scores,
        Map<String, String> habits) {

    public static final String MINOR = "MINOR";
    public static final String NO_OPENING = "NO_OPENING";
    public static final String ONCE_PER_SESSION = "ONCE_PER_SESSION";

    public record World(String code, String name, String setting, List<Lore> lore, List<Event> events) {}

    public record Lore(String code, String title, List<String> keywords, String body) {}

    public record Event(String code, String text, String hook, String loreCode, int weight) {}

    public record Example(String user, String reply) {}

    public static final String STUDY = "STUDY";
    public static final String BREAK = "BREAK";

    /**
     * @param signals 어울리는 사용자 말의 종류. 비면 평소에 쓰는 기술
     * @param modes 쓰는 때 (STUDY · BREAK). 비면 언제나
     */
    public record Move(
            String code,
            String name,
            String how,
            List<SignalType> signals,
            List<String> modes,
            List<Example> examples) {

        public boolean usableIn(String mode) {
            return modes == null || modes.isEmpty() || modes.contains(mode);
        }
    }

    /** @param minStage 이 관계 단계부터 쓰는 대사 */
    public record Line(String situation, int minStage, int sort, String text, String expression) {}

    /** 관계 단계에 맞는 말투 한 줄: 지금 단계 이하 중 가장 높은 단계 */
    public String toneFor(int stage) {
        String best = null;
        int bestStage = 0;
        for (var e : stageTones.entrySet()) {
            if (e.getKey() <= stage && e.getKey() > bestStage) {
                best = e.getValue();
                bestStage = e.getKey();
            }
        }
        return best;
    }

    /** 이 상황 · 단계에서 쓸 대사 (순서대로). 지금 단계 이하 중 가장 높은 min_stage 묶음 */
    public List<Line> linesFor(String situation, int stage) {
        int best = lines.stream()
                .filter(l -> l.situation().equals(situation) && l.minStage() <= stage)
                .mapToInt(Line::minStage)
                .max()
                .orElse(0);
        return lines.stream()
                .filter(l -> l.situation().equals(situation) && l.minStage() == best)
                .sorted(java.util.Comparator.comparingInt(Line::sort))
                .toList();
    }

    /** 에셋이 없는 표정이면 대신 쓸 표정 (없으면 그대로) */
    public String fallbackExpression(String expression) {
        return expression == null ? null : expressionFallbacks.getOrDefault(expression, expression);
    }
}
