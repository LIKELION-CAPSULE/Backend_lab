package com.capsule.dialogue.dto;

import com.capsule.dialogue.config.DialogueProperties.ScoreRule;
import com.capsule.relation.api.RelationTypes.SignalType;
import java.util.List;
import java.util.Map;

/**
 * 캐릭터 한 명의 대사 설정 전체 (DB: character_profiles · character_* · worlds · world_*, ADR 0022 결정 15).
 * 답장 · 상태 반응(결정 19) · 상황 대사 · 점수표가 이것만 보고 돈다: 캐릭터를 더해도 코드는 바뀌지 않는다.
 * 실험실 API가 이 모양 그대로 JSON으로 읽고 쓴다.
 *
 * @param bible 공통 페르소나 (누구 · 지금 상황 · 성격 · 말하는 법)
 * @param expressionFallbacks 대체 표. 목록 안 키 = 에셋이 없는 표정 → 대신 쓸 표정, 목록 밖 키 = 모델이 쓴 감정 이름 → 목록 안 표정
 * @param stageTones 관계 단계 → 그 단계의 관계 설명 (거리감 · 호칭 · 챙김 수위)
 * @param samples 관계 단계별 말투 샘플 (ADR 0022 결정 19)
 * @param world · moves 세계 지식 · 대화 기술. 결정 19부터 답장에 쓰지 않는다 (다음 정리 때 테이블째 뺀다)
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
        List<Sample> samples,
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

    /** 말투 샘플 한 쌍. @param minStage 이 관계 단계부터 */
    public record Sample(int minStage, String user, String reply) {}

    /** 이 단계의 말투 샘플: 지금 단계 이하 중 가장 높은 min_stage 묶음 (대사와 같은 규칙) */
    public List<Sample> samplesFor(int stage) {
        int best = samples.stream()
                .filter(s -> s.minStage() <= stage)
                .mapToInt(Sample::minStage)
                .max()
                .orElse(0);
        return samples.stream().filter(s -> s.minStage() == best).toList();
    }

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

    /** 모델이 쓴 감정 이름을 표정 목록에 맞춘다: 목록에 있으면 그대로, 없으면 대체 표(예: 걱정 → 집중)로, 그래도 없으면 null */
    public String emotion(String raw) {
        if (raw == null) return null;
        String e = raw.strip();
        // 목록 안 이름이 먼저: 대체 표의 목록 안 키(에셋이 없는 표정의 대체)는 동작을 고를 때만 쓴다 (DialogueService)
        if (expressions.contains(e)) return e;
        String mapped = expressionFallbacks.get(e);
        return mapped != null && expressions.contains(mapped) ? mapped : null;
    }
}
