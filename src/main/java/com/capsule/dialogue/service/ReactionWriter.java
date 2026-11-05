package com.capsule.dialogue.service;

import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.dialogue.client.Llm;
import com.capsule.dialogue.client.LlmGateway;
import com.capsule.dialogue.client.LlmJson;
import com.capsule.dialogue.config.DialogueProperties;
import com.capsule.dialogue.domain.PromptKey;
import com.capsule.dialogue.domain.PromptVersion;
import com.capsule.dialogue.dto.CharacterContent;
import com.capsule.dialogue.dto.ChatLine;
import com.capsule.dialogue.dto.LlmContext;
import com.capsule.dialogue.dto.LlmPurpose;
import com.capsule.dialogue.dto.ReactionLine;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 상태 반응 대사(명세 19)를 LLM으로 쓴다. 상태 변화(인사 · 자리 비움 · 복귀 · 쉬기 · 쉬기 끝)는 LLM이 대사와 표정을 같이 고르고,
 * 동작 이벤트(졸음 · 하품 · 손 흔들기 · 고개 돌림)는 미리 쓴 대사 (어떤 trigger를 LLM으로 할지는 reaction-llm-triggers, ADR 0022 결정 18).
 * 캐릭터 설정(DB)이 있으면 답장과 같은 고정 블록(scene.system, 캐시) + 반응 카드, 없으면 공통 프롬프트(reaction.system).
 * 시간 제한(5초)은 부르는 쪽이 지킨다: 실험실은 늦어도 끝까지 받아서 실제 지연을 본다.
 */
@Component
public class ReactionWriter {

    private final LlmGateway llm;
    private final PromptService prompts;
    private final FixedLines fixed;
    private final DialogueProperties props;
    /** 오늘 근황을 넣는 상태 변화: 공부 시작 인사만 (쉬기 시작에도 넣었더니 한마디마다 근황을 꺼냈다) */
    static final List<String> TODAY_TRIGGERS = List.of("GREETING");

    private final CharacterContents contents;
    private final Clock clock;

    ReactionWriter(
            LlmGateway llm,
            PromptService prompts,
            FixedLines fixed,
            DialogueProperties props,
            CharacterContents contents,
            Clock clock) {
        this.llm = llm;
        this.prompts = prompts;
        this.fixed = fixed;
        this.props = props;
        this.contents = contents;
        this.clock = clock;
    }

    public boolean usesLlm(String trigger) {
        return props.reactionLlmTriggers() != null
                && props.reactionLlmTriggers().contains(trigger);
    }

    /** 형식이 틀리면(대사 없음) 예외 → 부르는 쪽이 고정 대사로 */
    public ReactionLine generate(
            CharacterInfo character,
            String trigger,
            Integer intensity,
            int stage,
            String nickname,
            String summary,
            List<ChatLine> recent,
            Map<String, Long> pinned,
            LlmContext ctx) {
        Optional<CharacterContent> content = contents.find(character.id());
        if (content.isPresent())
            return scene(content.get(), trigger, intensity, stage, nickname, summary, recent, pinned, ctx);
        long id = character.id();
        PromptVersion system = prompts.resolve(PromptKey.REACTION_SYSTEM, id, pinned);
        PromptVersion lines = fixed.version(id, pinned);
        Map<String, String> vars = PromptVars.character(character);
        vars.put("nickname", nickname);
        vars.put("relationLevel", "Lv." + stage); // 프롬프트 변수 이름은 그대로 (바꾸면 프롬프트 새 버전)
        vars.put("summary", PromptVars.orNone(summary, "없음 (처음 만남)"));
        vars.put(
                "recent",
                recent.isEmpty() ? "없음" : PromptVars.transcript(recent, PromptVars.name(nickname), character.name()));
        vars.put("situation", fixed.situation(id, trigger, intensity, pinned));
        List<String> examples = fixed.examples(id, trigger, intensity, stage, pinned);
        vars.put("examples", examples.isEmpty() ? "없음" : "- " + String.join("\n- ", examples));
        vars.put("emotions", String.join(", ", props.emotions()));
        Llm.Result r = llm.complete(
                LlmPurpose.REACTION,
                List.of(new Llm.SystemBlock(PromptService.render(system.body(), vars), false)),
                List.of(new Llm.ChatTurn("user", "지금 상황에 맞는 한마디를 해 줘.")),
                ctx.withPrompts(PromptVars.ids(List.of(system, lines))),
                null);
        return parse(r, e -> PromptVars.emotion(e, props.emotions()));
    }

    /** 캐릭터 설정이 있는 캐릭터: 답장과 같은 고정 블록 + 반응 카드. 표정은 그 캐릭터의 표정 목록에서 */
    private ReactionLine scene(
            CharacterContent c,
            String trigger,
            Integer intensity,
            int stage,
            String nickname,
            String summary,
            List<ChatLine> recent,
            Map<String, Long> pinned,
            LlmContext ctx) {
        PromptVersion system = prompts.resolve(PromptKey.SCENE_SYSTEM, c.characterId(), pinned);
        String situation = fixed.situation(c.characterId(), trigger, intensity, pinned);
        // 방금 나눈 대화는 답장처럼 메시지로, 일어난 일은 사용자 차례로 ("상태가 바뀌었다"만 주면 외모가 바뀐 걸로 읽었다)
        List<Llm.ChatTurn> messages = new ArrayList<>(PromptVars.turns(recent, recent.size()));
        messages.add(new Llm.ChatTurn("user", "(방금 일어난 일: " + situation + ")"));
        Llm.Result r = llm.complete(
                LlmPurpose.REACTION,
                List.of(
                        new Llm.SystemBlock(
                                PromptService.render(
                                        system.body(),
                                        SceneChatPipeline.systemVars(
                                                c,
                                                stage,
                                                nickname,
                                                s -> fixed.situation(c.characterId(), s, null, pinned))),
                                true),
                        new Llm.SystemBlock(
                                card(
                                        c,
                                        nickname,
                                        summary,
                                        recent,
                                        TODAY_TRIGGERS.contains(trigger) ? SceneChatPipeline.today(c, clock) : null),
                                false)),
                messages,
                ctx.withPrompts(List.of(system.id())),
                null);
        return parse(r, c::emotion);
    }

    /** 상태 반응의 상황 카드. 지난 세션 흐름 · 오늘 근황이 있으면 다시 만난 사이처럼 (공부 시작 인사) */
    static String card(CharacterContent c, String nickname, String summary, List<ChatLine> recent, String today) {
        List<String> L = new ArrayList<>();
        L.add("<상황>");
        L.add("사용자: " + nickname);
        if (today != null) L.add(c.speaker() + " 오늘: " + today);
        if (summary != null && !summary.isBlank()) L.add("<지난 세션 흐름 (최근 것이 아래)>\n" + summary + "\n</지난 세션 흐름>");
        List<String> avoid = SceneChatPipeline.avoid(c, recent);
        if (!avoid.isEmpty()) L.add("피할 것: " + String.join(" / ", avoid));
        L.add("이번 말: 방금 일어난 일에 바로 반응하는 한마디. 한 문장, 말풍선 두 줄 이내. 사용자를 탓하거나 다그치지 않는다");
        L.add("</상황>");
        return String.join("\n", L);
    }

    /**
     * 장면 블록은 {reply, emotion}, 공통 프롬프트는 {content, emotion}. 답장(20)과 같이 JSON 없는 맨 글은 그 글을 대사로(감정 없음),
     * 깨진 JSON은 IllegalStateException (스모크에서 맨 글 한 줄을 형식 오류로 버려 고정 대사로 갔다)
     */
    static ReactionLine parse(Llm.Result r, UnaryOperator<String> emotion) {
        JsonNode json = SceneChatPipeline.replyJson(r);
        String content = SceneChatPipeline.replyText(r.text(), json);
        if (content == null && json != null) content = LlmJson.str(json.path("content"));
        if (content == null || content.isBlank()) throw new IllegalStateException("반응 대사 형식 오류: " + r.text());
        return new ReactionLine(
                content.strip(), json == null ? null : emotion.apply(LlmJson.str(json.path("emotion"))));
    }
}
