package com.capsule.dialogue.service;

import com.capsule.common.time.ClockConfig;
import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.dialogue.domain.ChatMessage;
import com.capsule.dialogue.domain.PromptVersion;
import com.capsule.dialogue.dto.ChatLine;
import com.capsule.dialogue.engine.Llm;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 프롬프트 변수({{이름}})와 대화 기록을 만드는 공통 함수. 파이프라인·작성기·실험실이 같은 값을 쓰도록 한곳에 둔다 */
final class PromptVars {
    private PromptVars() {}

    private static final DateTimeFormatter NOW = DateTimeFormatter.ofPattern("yyyy-MM-dd (E) HH:mm", Locale.KOREAN);

    /** characterName · personality · intro */
    static Map<String, String> character(CharacterInfo c) {
        Map<String, String> vars = new HashMap<>();
        vars.put("characterName", c.name());
        vars.put(
                "personality",
                c.personality() == null || c.personality().isEmpty() ? "정해지지 않음" : String.join(", ", c.personality()));
        vars.put("intro", c.intro() == null ? "" : c.intro());
        return vars;
    }

    static String now(Clock clock) {
        return ZonedDateTime.now(clock.withZone(ClockConfig.KST)).format(NOW);
    }

    /** 감정 이름을 후보 목록에 맞춘다. 후보에 없으면 null */
    static String emotion(String raw, List<String> allowed) {
        if (raw == null) return null;
        String e = raw.strip().toUpperCase(Locale.ROOT);
        return allowed.contains(e) ? e : null;
    }

    /** 최근 limit개를 LLM 대화 턴으로. 사용자 말로 시작해야 해서 앞쪽 캐릭터 말은 버린다 */
    static List<Llm.ChatTurn> turns(List<ChatLine> history, int limit) {
        List<ChatLine> recent = history.subList(Math.max(0, history.size() - limit), history.size());
        List<Llm.ChatTurn> turns = new ArrayList<>();
        for (ChatLine l : recent) {
            boolean user = ChatMessage.USER.equals(l.sender());
            if (turns.isEmpty() && !user) continue;
            turns.add(new Llm.ChatTurn(user ? "user" : "assistant", l.content()));
        }
        return turns;
    }

    /** 요약·평가용 대화록: "사용자: …" / "채린: …" */
    static String transcript(List<ChatLine> lines, String characterName) {
        StringBuilder sb = new StringBuilder();
        for (ChatLine l : lines)
            sb.append(ChatMessage.USER.equals(l.sender()) ? "사용자" : characterName)
                    .append(": ")
                    .append(l.content())
                    .append('\n');
        return sb.toString().strip();
    }

    /** 실험실 trace용: 키 → {id, version, status} */
    static Map<String, Object> versions(List<PromptVersion> used) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (PromptVersion v : used)
            m.put(v.promptKey(), Map.of("id", v.id(), "version", v.version(), "status", v.status()));
        return m;
    }

    static List<Long> ids(List<PromptVersion> used) {
        return used.stream().map(PromptVersion::id).toList();
    }

    static String orNone(String s, String none) {
        return s == null || s.isBlank() ? none : s;
    }
}
