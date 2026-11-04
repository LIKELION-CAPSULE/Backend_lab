package com.capsule.dialogue.service;

import com.capsule.dialogue.client.LlmJson;
import com.capsule.dialogue.domain.PromptKey;
import com.capsule.dialogue.domain.PromptVersion;
import com.capsule.dialogue.dto.ReactionLine;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * LLM 없이 쓰는 대사 (프롬프트 키 fixed.lines, YAML). 캐릭터 전용 버전이 있으면 그걸, 없으면 공통.
 * 상태 반응(19)의 고정 대사·감정·상황 설명과, LLM이 5초 안에 못 만들 때의 대체 대사(20·22)를 준다.
 */
@Component
public class FixedLines {

    private static final Logger log = LoggerFactory.getLogger(FixedLines.class);
    /** 대체 대사 표까지 못 읽을 때(내용 오류) 마지막 안전장치: 응답은 나가야 하므로 */
    private static final String LAST_RESORT = "…";

    /** byLevel: 호감도 단계 N 이상에서 쓰는 대사. 현재 단계 이하 중 가장 높은 칸, 없으면 lines */
    private record Entry(String situation, String emotion, List<String> lines, Map<Integer, List<String>> byLevel) {

        List<String> linesFor(int level) {
            List<String> best = null;
            int bestLevel = Integer.MIN_VALUE;
            if (byLevel != null) {
                for (var e : byLevel.entrySet()) {
                    if (e.getKey() <= level && e.getKey() > bestLevel && notEmpty(e.getValue())) {
                        best = e.getValue();
                        bestLevel = e.getKey();
                    }
                }
            }
            return best != null ? best : notEmpty(lines) ? lines : null;
        }
    }

    private record Fallbacks(List<String> reply, List<String> closing) {}

    private record Table(Map<String, Entry> reactions, Fallbacks fallbacks) {}

    private final PromptService prompts;

    FixedLines(PromptService prompts) {
        this.prompts = prompts;
    }

    /**
     * trigger 고정 대사(호감도 단계에 맞는 묶음에서 하나)와 감정.
     * AWAY는 강도별 칸(AWAY_2 · AWAY_3)에 대사가 없으면 AWAY 대사
     */
    public ReactionLine reaction(
            long characterId, String trigger, Integer intensity, int level, Map<String, Long> pinned) {
        Table t = table(characterId, pinned);
        Entry exact = entry(t, key(trigger, intensity));
        Entry base = entry(t, trigger);
        List<String> lines = lines(exact, base, level);
        if (lines == null) {
            // 콘텐츠 빠짐: 반응 API를 500으로 만들지 않고 자리 표시 대사로 (로그로 알림)
            log.error("fixed.lines에 {} 대사가 없음 (캐릭터 {})", trigger, characterId);
            return new ReactionLine(LAST_RESORT, null);
        }
        String emotion =
                exact != null && exact.emotion() != null ? exact.emotion() : base == null ? null : base.emotion();
        return new ReactionLine(pick(lines), emotion);
    }

    /** LLM 반응 프롬프트에 넣을 상황 설명과 예시 대사 */
    public String situation(long characterId, String trigger, Integer intensity, Map<String, Long> pinned) {
        Table t = table(characterId, pinned);
        Entry e = entry(t, key(trigger, intensity));
        if (e == null) e = entry(t, trigger);
        return e == null || e.situation() == null ? trigger : e.situation();
    }

    /** 고정 대사와 같은 묶음 (강도·호감도 단계 기준). LLM이 이 단계의 말투를 따라 하게 */
    public List<String> examples(
            long characterId, String trigger, Integer intensity, int level, Map<String, Long> pinned) {
        Table t = table(characterId, pinned);
        List<String> lines = lines(entry(t, key(trigger, intensity)), entry(t, trigger), level);
        return lines == null ? List.of() : lines;
    }

    public String replyFallback(long characterId) {
        return fallback(characterId, true);
    }

    public String closingFallback(long characterId) {
        return fallback(characterId, false);
    }

    public PromptVersion version(long characterId, Map<String, Long> pinned) {
        return prompts.resolve(PromptKey.FIXED_LINES, characterId, pinned);
    }

    private String fallback(long characterId, boolean reply) {
        try {
            Fallbacks f = table(characterId, Map.of()).fallbacks();
            return pick(reply ? f.reply() : f.closing());
        } catch (RuntimeException e) {
            log.error("fixed.lines의 대체 대사를 못 읽음 (캐릭터 {})", characterId, e);
            return LAST_RESORT;
        }
    }

    private Table table(long characterId, Map<String, Long> pinned) {
        return LlmJson.JSON.convertValue(
                new Yaml().load(version(characterId, pinned).body()), Table.class);
    }

    private static List<String> lines(Entry exact, Entry base, int level) {
        List<String> lines = exact == null ? null : exact.linesFor(level);
        return lines != null || base == null ? lines : base.linesFor(level);
    }

    private static boolean notEmpty(List<String> lines) {
        return lines != null && !lines.isEmpty();
    }

    private static Entry entry(Table t, String key) {
        return t.reactions() == null ? null : t.reactions().get(key);
    }

    private static String key(String trigger, Integer intensity) {
        return intensity != null && intensity > 1 ? trigger + "_" + intensity : trigger;
    }

    private static String pick(List<String> lines) {
        return lines.get(ThreadLocalRandom.current().nextInt(lines.size()));
    }
}
