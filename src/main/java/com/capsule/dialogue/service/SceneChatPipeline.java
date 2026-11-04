package com.capsule.dialogue.service;

import com.capsule.dialogue.client.LlmGateway;
import com.capsule.dialogue.client.LlmJson;
import com.capsule.dialogue.domain.ChatMessage;
import com.capsule.dialogue.domain.PromptKey;
import com.capsule.dialogue.domain.PromptVersion;
import com.capsule.dialogue.dto.CharacterContent;
import com.capsule.dialogue.dto.ChatInput;
import com.capsule.dialogue.dto.ChatLine;
import com.capsule.dialogue.dto.ChatOutcome;
import com.capsule.dialogue.dto.LlmContext;
import com.capsule.dialogue.dto.LlmPurpose;
import com.capsule.dialogue.engine.Llm;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 캐릭터 페르소나 답장 (ADR 0022 결정 19). 캐릭터 설정은 DB(CharacterContent).
 * LLM은 한 번: [페르소나 · 지금 관계 단계의 관계 설명 · 말투 샘플 · 상태 변화 예시 · 규칙 (단계마다 같아서 캐시)]
 * + [상황 카드: 지금 · 기억 · 지난 세션 같은 사실만] + 이번 세션 대화.
 * 어떻게 말할지는 모델에 맡기고, 코드는 틀릴 수 없는 사실만 넘긴다 (결정 15의 매 턴 기술 · 지식 카드 · 무작위 사건은 뺐다).
 */
@Component
class SceneChatPipeline implements ChatPipeline {

    // 공부 중에 수다가 길어지면 (ChatInput.chatty, 기준은 DialogueService) 공부로 돌려보낸다. 막지는 않는다 (ADR 0022 결정 18)
    static final String CHATTY = "수다가 길어졌다: 공부 중인데 대화만 계속하고 있다. 이번 말은 짧게 받아 주고 공부로 돌려보낸다 (다그치지 말고 캐릭터 말투로)";
    /** 고정 블록에 예시로 넣는 상태 변화 (순간 동작은 미리 쓴 대사 그대로라 뺀다) */
    static final List<String> STATE_SITUATIONS =
            List.of("GREETING", "AWAY", "AWAY_2", "AWAY_3", "RETURN", "BREAK", "BREAK_END");

    private final LlmGateway llm;
    private final PromptService prompts;
    private final CharacterContents contents;
    private final SimpleChatPipeline generic;
    private final FactPicker factPicker;
    private final FixedLines fixed;
    private final Clock clock;

    SceneChatPipeline(
            LlmGateway llm,
            PromptService prompts,
            CharacterContents contents,
            SimpleChatPipeline generic,
            FactPicker factPicker,
            FixedLines fixed,
            Clock clock) {
        this.llm = llm;
        this.prompts = prompts;
        this.contents = contents;
        this.generic = generic;
        this.factPicker = factPicker;
        this.fixed = fixed;
        this.clock = clock;
    }

    @Override
    public String name() {
        return SCENE;
    }

    @Override
    public ChatOutcome reply(ChatInput in, LlmContext ctx) {
        long characterId = in.character().id();
        // 설정을 아직 안 채운 캐릭터는 공통 프롬프트(SIMPLE)로 말한다: 새 캐릭터 행만 넣어도 대화는 된다
        java.util.Optional<CharacterContent> found = contents.find(characterId);
        if (found.isEmpty()) return generic.reply(in, ctx);
        CharacterContent c = found.get();
        PromptVersion system = prompts.resolve(PromptKey.SCENE_SYSTEM, characterId, in.pinnedPrompts());
        String fact = factPicker.pick(in, ctx).orElse(null); // 아는 사실은 결정 모델이 고른 하나만 (없으면 안 넣음)
        String card = card(c, in, fact, PromptVars.now(clock));
        List<Llm.SystemBlock> blocks = List.of(
                new Llm.SystemBlock(
                        PromptService.render(
                                system.body(), systemVars(c, in.relationLevel(), in.nickname(), situation(c, in))),
                        true),
                new Llm.SystemBlock(card, false));
        List<Llm.ChatTurn> messages = new ArrayList<>(PromptVars.turns(in.history(), SimpleChatPipeline.HISTORY_LIMIT));
        messages.add(new Llm.ChatTurn("user", in.userMessage()));
        Llm.Result r = llm.complete(LlmPurpose.CHAT, blocks, messages, ctx.withPrompts(List.of(system.id())), null);

        JsonNode json = LlmJson.objectIn(r.text());
        boolean truncated = "max_tokens".equals(r.stopReason()) || "length".equals(r.stopReason());
        if (json == null && (truncated || r.text().strip().startsWith("{")))
            throw new IllegalStateException("답장 JSON이 깨짐 (stop_reason=" + r.stopReason() + "): " + r.text());
        String reply = json != null ? LlmJson.str(json.path("reply")) : r.text();
        if (reply == null || reply.isBlank()) throw new IllegalStateException("빈 답장: " + r.text());
        String emotion = json == null ? null : PromptVars.emotion(LlmJson.str(json.path("emotion")), c.expressions());

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("pipeline", SCENE);
        trace.put("prompts", PromptVars.versions(List.of(system)));
        trace.put("stage", in.relationLevel());
        trace.put("fact", fact);
        trace.put("scene", card);
        trace.put("raw", r.text());
        return ChatOutcome.of(reply.strip(), emotion, null, trace);
    }

    private Function<String, String> situation(CharacterContent c, ChatInput in) {
        return s -> fixed.situation(c.characterId(), s, null, in.pinnedPrompts());
    }

    /**
     * 고정 블록(scene.system) 변수: 페르소나 + 지금 관계 단계의 관계 설명 · 말투 샘플 · 상태 변화 예시.
     * 상태 반응(ReactionWriter)도 같은 블록을 쓴다 (캐시는 모델별이라 반응끼리만 같이 탄다)
     *
     * @param situation 상황 코드(GREETING · AWAY_3 …) → "방금 일어난 일" 문장 (fixed.lines의 situation)
     */
    static Map<String, String> systemVars(
            CharacterContent c, int stage, String nickname, Function<String, String> situation) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("bible", c.bible());
        vars.put("rules", c.rules());
        vars.put("speaker", c.speaker());
        vars.put("expressions", String.join(", ", c.expressions()));
        vars.put("relation", PromptVars.orNone(c.toneFor(stage), "보통"));
        vars.put(
                "samples",
                c.samplesFor(stage).stream()
                        .map(s -> "사용자: " + s.user() + "\n" + c.speaker() + ": " + named(s.reply(), nickname))
                        .collect(Collectors.joining("\n\n")));
        List<String> events = new ArrayList<>();
        for (String s : STATE_SITUATIONS)
            c.linesFor(s, stage).stream()
                    .findFirst()
                    .ifPresent(
                            l -> events.add("(방금 일어난 일: " + situation.apply(s) + ") → " + named(l.text(), nickname)));
        vars.put("events", String.join("\n", events));
        return vars;
    }

    private static String named(String text, String nickname) {
        return text.replace("{name}", nickname);
    }

    /** 이번 턴의 사실만: 시각 · 기억 · 지난 세션 · 일정(첫 대화) · 피할 말버릇 · 수다 길어짐 · 지금(길이 · 구성, 맨 끝) */
    static String card(CharacterContent c, ChatInput in, String fact, String now) {
        List<String> L = new ArrayList<>();
        L.add("<상황>");
        L.add("사용자: " + in.nickname() + " / 시각: " + now);
        // 기억 (ADR 0022 결정 16): 아는 사실은 결정 모델이 이번 말에 맞춰 고른 하나만, 세션 카드는 최근 2장, 원문은 메시지로 15줄
        if (fact != null) L.add("<꺼낼 수 있는 기억> " + fact + " (자연스러울 때만, 단정하지 말고)");
        if (in.summary() != null && !in.summary().isBlank())
            L.add("<지난 세션 흐름 (최근 것이 아래)>\n" + in.summary() + "\n</지난 세션 흐름>");
        // 사용자 일정은 공부 시작(이 세션 첫 대화)에만 (PM 확정: 시작과 끝에만)
        if (in.history().isEmpty() && !in.memories().isEmpty())
            L.add("<사용자 일정>\n- " + String.join("\n- ", in.memories()) + "\n</사용자 일정>");
        List<String> avoid = avoid(c, in.history());
        if (!avoid.isEmpty()) L.add("피할 것: " + String.join(" / ", avoid));
        // 공부 중 · 쉬는 시간에 따라 답 길이와 구성이 다르다 (PM 확정: 1~2문장 / 2~4문장, 구성은 캐릭터 설정). 맨 끝에 둔다
        if (!in.onBreak() && in.chatty()) L.add(CHATTY);
        L.add("지금: " + (in.onBreak() ? c.breakStyle() : c.studyStyle()));
        L.add("</상황>");
        return String.join("\n", L);
    }

    /** 말버릇 규칙: 첫마디 금지는 늘, 세션 한 번은 이번 세션 캐릭터 대사에 이미 있으면 */
    static List<String> avoid(CharacterContent c, List<ChatLine> history) {
        List<String> out = new ArrayList<>();
        c.habits().forEach((phrase, rule) -> {
            if (CharacterContent.NO_OPENING.equals(rule)) out.add("\"" + phrase + "\"로 말을 시작하지 않기");
            else if (CharacterContent.ONCE_PER_SESSION.equals(rule)
                    && history.stream()
                            .anyMatch(l -> ChatMessage.CHARACTER.equals(l.sender())
                                    && l.content().contains(phrase))) out.add("\"" + phrase + "\" 쓰지 않기 (이번 세션에 이미 씀)");
        });
        return out;
    }
}
