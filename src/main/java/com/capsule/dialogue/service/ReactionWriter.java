package com.capsule.dialogue.service;

import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.dialogue.client.LlmGateway;
import com.capsule.dialogue.client.LlmJson;
import com.capsule.dialogue.config.DialogueProperties;
import com.capsule.dialogue.domain.ChatMessage;
import com.capsule.dialogue.domain.PromptKey;
import com.capsule.dialogue.domain.PromptVersion;
import com.capsule.dialogue.dto.ChatLine;
import com.capsule.dialogue.dto.LlmContext;
import com.capsule.dialogue.dto.LlmPurpose;
import com.capsule.dialogue.dto.ReactionLine;
import com.capsule.dialogue.engine.Llm;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 상태 반응 대사(명세 19)를 LLM으로 쓴다. 합의: 상태 변화(인사 · 자리 비움 · 복귀 · 휴식)는 호감도와 대화 맥락을 넣은 LLM,
 * 동작 이벤트(졸음 · 하품 · 손 흔들기 · 고개 돌림)는 FixedLines의 고정 대사 (어떤 trigger를 LLM으로 할지는 reaction-llm-triggers).
 * 시간 제한(5초)은 부르는 쪽이 지킨다: 실험실은 늦어도 끝까지 받아서 실제 지연을 본다.
 */
@Component
public class ReactionWriter {

    private final LlmGateway llm;
    private final PromptService prompts;
    private final FixedLines fixed;
    private final DialogueProperties props;

    ReactionWriter(LlmGateway llm, PromptService prompts, FixedLines fixed, DialogueProperties props) {
        this.llm = llm;
        this.prompts = prompts;
        this.fixed = fixed;
        this.props = props;
    }

    // TODO(judgment): J-04 상태 반응을 LLM으로 할지 고정 대사로 할지 (설정으로 trigger마다 고를 수 있게만 해 둠)
    public boolean usesLlm(String trigger) {
        return props.reactionLlmTriggers() != null
                && props.reactionLlmTriggers().contains(trigger);
    }

    /** 형식이 틀리면(대사 없음) 예외 → 부르는 쪽이 고정 대사로 */
    public ReactionLine generate(
            CharacterInfo character,
            String trigger,
            Integer intensity,
            int relationLevel,
            String nickname,
            String summary,
            List<ChatLine> recent,
            Map<String, Long> pinned,
            LlmContext ctx) {
        long id = character.id();
        PromptVersion system = prompts.resolve(PromptKey.REACTION_SYSTEM, id, pinned);
        PromptVersion lines = fixed.version(id, pinned);
        Map<String, String> vars = PromptVars.character(character);
        vars.put("nickname", nickname);
        vars.put("relationLevel", "Lv." + relationLevel);
        vars.put("summary", PromptVars.orNone(summary, "없음 (처음 만남)"));
        vars.put(
                "recent",
                recent.isEmpty()
                        ? "없음"
                        : recent.stream()
                                .map(l -> (ChatMessage.USER.equals(l.sender()) ? nickname : character.name()) + ": "
                                        + l.content())
                                .collect(Collectors.joining("\n")));
        vars.put("situation", fixed.situation(id, trigger, intensity, pinned));
        List<String> examples = fixed.examples(id, trigger, intensity, relationLevel, pinned);
        vars.put("examples", examples.isEmpty() ? "없음" : "- " + String.join("\n- ", examples));
        vars.put("emotions", String.join(", ", props.emotions()));
        Llm.Result r = llm.complete(
                LlmPurpose.REACTION,
                List.of(new Llm.SystemBlock(PromptService.render(system.body(), vars), false)),
                List.of(new Llm.ChatTurn("user", "지금 상황에 맞는 한마디를 해 줘.")),
                ctx.withPrompts(PromptVars.ids(List.of(system, lines))),
                null);
        JsonNode json = LlmJson.objectIn(r.text());
        String content = json == null ? null : LlmJson.str(json.path("content"));
        if (content == null || content.isBlank()) throw new IllegalStateException("반응 대사 형식 오류: " + r.text());
        return new ReactionLine(
                content.strip(), PromptVars.emotion(LlmJson.str(json.path("emotion")), props.emotions()));
    }
}
