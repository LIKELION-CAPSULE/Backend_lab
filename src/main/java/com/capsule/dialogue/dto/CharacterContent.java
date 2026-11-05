package com.capsule.dialogue.dto;

import com.capsule.dialogue.config.DialogueProperties.ScoreRule;
import com.capsule.relation.api.RelationTypes.SignalType;
import java.util.List;
import java.util.Map;

/**
 * 캐릭터 한 명의 대사 설정 전체 (DB: character_profiles · character_*, ADR 0022 결정 15).
 * 답장 · 상태 반응(결정 19) · 상황 대사 · 점수표가 이것만 보고 돈다: 캐릭터를 더해도 코드는 바뀌지 않는다.
 * 실험실 API가 이 모양 그대로 JSON으로 읽고 쓴다.
 *
 * @param version 불러온 profile.yml의 version. 파일 쪽이 더 높으면 앱 시작 때 다시 불러온다 (실험실 저장은 바꾸지 않음)
 * @param bible 공통 페르소나 (누구 · 지금 상황 · 성격 · 말하는 법)
 * @param expressions 고를 수 있는 표정 (19 · 20 응답 emotion 값). 대사를 다 만든 뒤 결정 모델이 이 중 하나를 고른다 (ADR 0028)
 * @param expressionGuide 표정 → 언제 쓰는지 (결정 모델 보기 설명). 없으면 이름만 보기로
 * @param stageTones 관계 단계 → 그 단계의 관계 설명 (거리감 · 호칭 · 챙김 수위)
 * @param samples 관계 단계별 말투 샘플 (ADR 0022 결정 19)
 * @param lore 로어북: 사용자 말에 키워드가 있을 때만 답장에 넣는 원작 설정 카드. 실험실이 빼고 저장하면 빈 목록
 */
public record CharacterContent(
        long characterId,
        String code,
        int version,
        String speaker,
        String bible,
        String rules,
        String safety,
        List<String> expressions,
        Map<String, String> expressionGuide,
        Map<Integer, String> stageTones,
        String studyStyle,
        String breakStyle,
        List<Line> lines,
        List<Sample> samples,
        Map<SignalType, ScoreRule> scores,
        Map<String, String> habits,
        List<Lore> lore) {

    public CharacterContent {
        lore = lore == null ? List.of() : List.copyOf(lore);
        expressionGuide = expressionGuide == null ? Map.of() : expressionGuide;
    }

    public static final String MINOR = "MINOR";
    public static final String NO_OPENING = "NO_OPENING";
    public static final String ONCE_PER_SESSION = "ONCE_PER_SESSION";

    /** 버전만 바꾼 사본 */
    public CharacterContent withVersion(int v) {
        return new CharacterContent(
                characterId,
                code,
                v,
                speaker,
                bible,
                rules,
                safety,
                expressions,
                expressionGuide,
                stageTones,
                studyStyle,
                breakStyle,
                lines,
                samples,
                scores,
                habits,
                lore);
    }

    /** @param minStage 이 관계 단계부터 쓰는 대사 */
    public record Line(String situation, int minStage, int sort, String text, String expression) {}

    /** 말투 샘플 한 쌍. @param minStage 이 관계 단계부터 */
    public record Sample(int minStage, String user, String reply) {}

    /** 로어북 카드 한 장. @param keys 사용자 말에 이 중 하나가 들어 있으면(띄어쓰기 무시) 넣는다 */
    public record Lore(String title, List<String> keys, String text) {
        public Lore {
            // 실험실 저장 · yml 실수로 keys가 비어도 매 턴 부르는 loreFor가 터지지 않게
            keys = keys == null
                    ? List.of()
                    : keys.stream().filter(k -> k != null && !k.isBlank()).toList();
        }
    }

    /** 이 말에 걸린 카드: 파일 순서대로 최대 max장. 공백은 무시한다 ("해리 포터" = "해리포터") */
    public List<Lore> loreFor(String message, int max) {
        if (message == null || message.isBlank()) return List.of();
        String said = message.replaceAll("\\s", "");
        return lore.stream()
                .filter(l -> l.keys().stream().anyMatch(k -> said.contains(k.replaceAll("\\s", ""))))
                .limit(max)
                .toList();
    }

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

    /** 결정 모델 보기: 표정 목록 순서대로 이름 → 언제 쓰는지 (설명이 없으면 이름) */
    public Map<String, String> expressionChoices() {
        Map<String, String> choices = new java.util.LinkedHashMap<>();
        for (String e : expressions) choices.put(e, expressionGuide.getOrDefault(e, e));
        return choices;
    }
}
