package com.capsule.dialogue.service;

import com.capsule.common.settings.RuntimeSettings;
import com.capsule.dialogue.client.Llm;
import com.capsule.dialogue.client.LlmGateway;
import com.capsule.dialogue.client.LlmJson;
import com.capsule.dialogue.config.DialogueProperties;
import com.capsule.dialogue.domain.PromptKey;
import com.capsule.dialogue.domain.PromptVersion;
import com.capsule.dialogue.dto.ChatInput;
import com.capsule.dialogue.dto.ChatOutcome;
import com.capsule.dialogue.dto.LlmContext;
import com.capsule.dialogue.dto.LlmPurpose;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * LLM 한 번 → 답장 + 감정(JSON). 호감도는 여기서 정하지 않는다: ChatReplier가 결정 모델로 신호를 같이 읽는다 (ADR 0022).
 * 시스템 = [캐릭터·규칙(chat.system, 캐시)] + [상황(chat.context, 매번 바뀜)], 메시지 = 이번 세션 대화 + 이번 말.
 */
@Component
@RequiredArgsConstructor
public class SimpleChatPipeline implements ChatPipeline {

    private final LlmGateway llm;
    private final PromptService prompts;
    private final RuntimeSettings<DialogueProperties> props;
    private final FactPicker factPicker;
    private final Clock clock;

    @Override
    public String name() {
        return SIMPLE;
    }

    @Override
    public ChatOutcome reply(ChatInput in, LlmContext ctx, Consumer<String> onText) {
        long characterId = in.character().id();
        PromptVersion systemPrompt = prompts.resolve(PromptKey.CHAT_SYSTEM, characterId, in.pinnedPrompts());
        PromptVersion contextPrompt = prompts.resolve(PromptKey.CHAT_CONTEXT, characterId, in.pinnedPrompts());
        Map<String, String> vars = PromptVars.character(in.character());
        vars.put("emotions", String.join(", ", props.current().emotions()));
        vars.put("now", PromptVars.now(clock));
        vars.put(
                "length",
                SceneChatPipeline.length(
                        props.current().replyMinSentences(), props.current().replyMaxSentences()));
        vars.put("nickname", in.nickname());
        vars.put(
                "mode",
                in.onBreak()
                        ? "쉬는 시간 (3~6문장 안팎, 공부 재촉 없이 수다)"
                        : "공부 중 (3~6문장 안팎)"
                                + (in.chatty()
                                        ? ". "
                                                + SceneChatPipeline.chattyDirective(
                                                        SceneChatPipeline.userTurns(in.history()))
                                        : ""));
        vars.put("relationLevel", "Lv." + in.stage()); // 프롬프트 변수 이름은 그대로 (바꾸면 프롬프트 새 버전)
        vars.put("summary", PromptVars.orNone(in.summary(), "없음 (처음 만남)"));
        // 아는 사실은 결정 모델이 이번 말에 맞춰 고른 하나만 (ADR 0022 결정 16)
        vars.put("facts", factPicker.pick(in, ctx).map(f -> "- " + f).orElse("없음"));
        vars.put("memories", in.memories().isEmpty() ? "없음" : "- " + String.join("\n- ", in.memories()));

        List<Llm.SystemBlock> system = List.of(
                new Llm.SystemBlock(PromptService.render(systemPrompt.getBody(), vars), true),
                new Llm.SystemBlock(PromptService.render(contextPrompt.getBody(), vars), false));
        // TODO(judgment): J-05 LLM에 넘길 대화 수 (승희 2026-11-04: 15줄 history-lines. 프롬프트가 길수록 지시를 덜 따름)
        List<Llm.ChatTurn> messages =
                new ArrayList<>(PromptVars.turns(in.history(), props.current().historyLines()));
        messages.add(new Llm.ChatTurn("user", in.userMessage()));
        List<PromptVersion> used = List.of(systemPrompt, contextPrompt);
        Llm.Result r = llm.complete(LlmPurpose.CHAT, system, messages, ctx.withPrompts(PromptVars.ids(used)), onText);

        // JSON 없이 글만 왔으면 그 글을 답장으로 쓴다 (감정 없음).
        // 단 한도에 걸려 잘린 JSON('{"reply":"…')은 말풍선에 내보내지 않고 실패로 본다 → 대체 대사
        JsonNode json = SceneChatPipeline.replyJson(r);
        String reply = json != null ? LlmJson.str(json.path("reply")) : r.text();
        if (reply == null || reply.isBlank()) throw new IllegalStateException("빈 답장: " + r.text());
        String emotion = json == null
                ? null
                : PromptVars.emotion(
                        LlmJson.str(json.path("emotion")), props.current().emotions());

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("pipeline", SIMPLE);
        trace.put("prompts", PromptVars.versions(used));
        trace.put("jsonParsed", json != null);
        trace.put("raw", r.text());
        return ChatOutcome.of(reply.strip(), emotion, trace);
    }
}
