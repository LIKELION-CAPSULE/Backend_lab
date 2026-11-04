package com.capsule.dialogue.service;

import com.capsule.dialogue.client.LlmGateway;
import com.capsule.dialogue.client.LlmJson;
import com.capsule.dialogue.domain.ChatMessage;
import com.capsule.dialogue.domain.PromptKey;
import com.capsule.dialogue.domain.PromptVersion;
import com.capsule.dialogue.dto.CharacterContent;
import com.capsule.dialogue.dto.CharacterContent.Event;
import com.capsule.dialogue.dto.CharacterContent.Lore;
import com.capsule.dialogue.dto.CharacterContent.Move;
import com.capsule.dialogue.dto.ChatInput;
import com.capsule.dialogue.dto.ChatLine;
import com.capsule.dialogue.dto.ChatOutcome;
import com.capsule.dialogue.dto.LlmContext;
import com.capsule.dialogue.dto.LlmPurpose;
import com.capsule.dialogue.engine.Llm;
import com.capsule.relation.api.RelationTypes.SignalType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 장면 카드 답장 (ADR 0022 결정 15). 캐릭터 · 세계 설정은 DB(CharacterContent)에서 읽고, 코드는 이번 턴에 쓸 것만 골라
 * 장면 카드 한 장을 만든다. LLM은 한 번: [바이블 · 규칙 · 세계 · 표정 (매번 같아서 캐시)] + [장면 카드] + 이번 세션 대화.
 * 캐릭터 고유 문구는 코드에 없다 (말버릇 · 대화 기술 · 지식 · 대사 모두 데이터).
 */
@Component
class SceneChatPipeline implements ChatPipeline {

    static final int MAX_LORE = 4;
    // TODO(judgment): J-05 세계 사건이 저절로 일어날 확률 (한 턴마다)
    static final double EVENT_CHANCE = 0.15;

    private final LlmGateway llm;
    private final PromptService prompts;
    private final CharacterContents contents;
    private final SimpleChatPipeline generic;

    SceneChatPipeline(LlmGateway llm, PromptService prompts, CharacterContents contents, SimpleChatPipeline generic) {
        this.llm = llm;
        this.prompts = prompts;
        this.contents = contents;
        this.generic = generic;
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
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("bible", c.bible());
        vars.put("rules", c.rules());
        vars.put("speaker", c.speaker());
        vars.put("worldName", c.world().name());
        vars.put("worldSetting", c.world().setting());
        vars.put("expressions", String.join(", ", c.expressions()));

        Scene scene = scene(c, in, ThreadLocalRandom.current().nextDouble() < EVENT_CHANCE);
        List<Llm.SystemBlock> blocks = List.of(
                new Llm.SystemBlock(PromptService.render(system.body(), vars), true),
                new Llm.SystemBlock(scene.card(), false));
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
        trace.put("move", scene.move() == null ? null : scene.move().code());
        trace.put("lore", scene.lore().stream().map(Lore::code).toList());
        trace.put("event", scene.event() == null ? null : scene.event().code());
        trace.put("scene", scene.card());
        trace.put("raw", r.text());
        return ChatOutcome.of(reply.strip(), emotion, null, trace);
    }

    record Scene(String card, Move move, List<Lore> lore, Event event) {}

    /** 이번 턴에 쓸 것만 고른다. 장면 카드는 매 턴 수십 줄 */
    static Scene scene(CharacterContent c, ChatInput in, boolean eventHappens) {
        SignalType signal = ChatScorer.detect(in.userMessage());
        Event event = eventHappens ? pickEvent(c.world().events()) : null;
        String hay = in.userMessage() + " "
                + in.history().stream()
                        .skip(Math.max(0, in.history().size() - 2))
                        .map(ChatLine::content)
                        .collect(Collectors.joining(" "));
        List<Lore> lore = pickLore(c.world().lore(), hay, event);
        Move move = pickMove(c.moves(), signal);

        List<String> L = new ArrayList<>();
        L.add("<장면>");
        L.add("사용자: " + in.nickname() + " / 관계: " + PromptVars.orNone(c.toneFor(in.relationLevel()), "보통"));
        if (event != null)
            L.add("방금 일어난 일: " + event.text() + (event.hook() == null ? "" : " (어울리는 반응: " + event.hook() + ")"));
        if (!lore.isEmpty())
            L.add("<세계 지식>\n" + lore.stream().map(l -> "- " + l.body()).collect(Collectors.joining("\n"))
                    + "\n</세계 지식>");
        // 기억 세 겹은 전부 넣는다 (ADR 0022 결정 14): 아는 사실 최대 20개 · 세션 카드 최근 5장 · 원문 20줄(메시지).
        // 크기가 정해져 있어 다 넣어도 1천 자 남짓. 쓸지 말지는 답장 모델이 정한다 (시스템 블록 <읽는 법>)
        if (!in.facts().isEmpty()) L.add("<아는 사실>\n- " + String.join("\n- ", in.facts()) + "\n</아는 사실>");
        if (in.summary() != null && !in.summary().isBlank())
            L.add("<지난 세션 흐름 (최근 것이 아래)>\n" + in.summary() + "\n</지난 세션 흐름>");
        // 사용자 일정은 공부 시작(이 세션 첫 대화)에만 (PM 확정: 시작과 끝에만)
        if (in.history().isEmpty() && !in.memories().isEmpty())
            L.add("<사용자 일정>\n- " + String.join("\n- ", in.memories()) + "\n</사용자 일정>");
        if (move != null) {
            L.add("이번 대화 기술: " + move.name() + " — " + move.how());
            String ex = move.examples().stream()
                    .limit(2)
                    .map(e -> "사용자: " + e.user() + "\n" + c.speaker() + ": " + e.reply())
                    .collect(Collectors.joining("\n\n"));
            if (!ex.isEmpty()) L.add("<이 기술의 예시 (톤 참고용, 베끼지 않기)>\n" + ex + "\n</이 기술의 예시>");
        }
        List<String> avoid = avoid(c, in.history());
        if (!avoid.isEmpty()) L.add("피할 것: " + String.join(" / ", avoid));
        L.add("</장면>");
        return new Scene(String.join("\n", L), move, lore, event);
    }

    /** 사건 카드 → 키워드가 맞는 카드 순으로 최대 4장 (엔진 Planning.pickLore와 같은 규칙) */
    static List<Lore> pickLore(List<Lore> all, String hay, Event event) {
        List<Lore> chosen = new ArrayList<>();
        if (event != null && event.loreCode() != null)
            all.stream()
                    .filter(l -> l.code().equals(event.loreCode()))
                    .findFirst()
                    .ifPresent(chosen::add);
        for (Lore l : all) {
            if (chosen.size() >= MAX_LORE) break;
            if (!chosen.contains(l) && l.keywords().stream().anyMatch(hay::contains)) chosen.add(l);
        }
        return chosen;
    }

    /** 이 말의 종류에 어울리는 기술이 있으면 그중에서, 없으면 평소 기술(signals가 빈 것) 중에서 하나 */
    static Move pickMove(List<Move> moves, SignalType signal) {
        List<Move> fit =
                moves.stream().filter(m -> m.signals().contains(signal)).toList();
        if (fit.isEmpty())
            fit = moves.stream().filter(m -> m.signals().isEmpty()).toList();
        return fit.isEmpty() ? null : fit.get(ThreadLocalRandom.current().nextInt(fit.size()));
    }

    static Event pickEvent(List<Event> events) {
        int total = events.stream().mapToInt(Event::weight).sum();
        if (total <= 0) return null;
        int r = ThreadLocalRandom.current().nextInt(total);
        for (Event e : events) {
            r -= e.weight();
            if (r < 0) return e;
        }
        return null;
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
