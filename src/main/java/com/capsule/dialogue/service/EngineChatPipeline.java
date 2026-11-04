package com.capsule.dialogue.service;

import com.capsule.dialogue.client.LlmGateway;
import com.capsule.dialogue.client.LlmJson;
import com.capsule.dialogue.config.ContentLoader;
import com.capsule.dialogue.domain.PromptKey;
import com.capsule.dialogue.domain.PromptVersion;
import com.capsule.dialogue.dto.ChatInput;
import com.capsule.dialogue.dto.ChatOutcome;
import com.capsule.dialogue.dto.EngineOptions;
import com.capsule.dialogue.dto.LlmContext;
import com.capsule.dialogue.dto.LlmPurpose;
import com.capsule.dialogue.engine.Content;
import com.capsule.dialogue.engine.DialogueState;
import com.capsule.dialogue.engine.Engine;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 대화 엔진(#11, dialogue/engine)으로 답장: 감독(계획 JSON) → 배우(대사) [→ 품질 모드면 후보 3개 + 심사].
 * 캐릭터 콘텐츠는 그 캐릭터의 engine.* 프롬프트 버전 네 개에서 읽는다 (실험실에서 올림). 상태(DialogueState)는 부르는 쪽이 저장한다.
 */
@Component
class EngineChatPipeline implements ChatPipeline {

    /** dialogue_states.engine_version. DialogueState 형식을 바꾸면 올린다 (옛 상태는 새로 시작) */
    static final int STATE_VERSION = 1;

    private static final List<PromptKey> PACK = List.of(
            PromptKey.ENGINE_CHARACTER, PromptKey.ENGINE_ACTOR, PromptKey.ENGINE_DIRECTOR, PromptKey.ENGINE_JUDGE);

    private final LlmGateway llm;
    private final PromptService prompts;
    private final Clock clock;

    EngineChatPipeline(LlmGateway llm, PromptService prompts, Clock clock) {
        this.llm = llm;
        this.prompts = prompts;
        this.clock = clock;
    }

    @Override
    public String name() {
        return ENGINE;
    }

    @Override
    public ChatOutcome reply(ChatInput in, LlmContext ctx) {
        List<PromptVersion> pack = PACK.stream()
                .map(k -> prompts.resolve(k, in.character().id(), in.pinnedPrompts()))
                .toList();
        Content.Character c = ContentLoader.fromTexts(
                        pack.get(0).body(),
                        pack.get(1).body(),
                        pack.get(2).body(),
                        pack.get(3).body())
                .character();
        DialogueState st = in.engineState() != null
                ? in.engineState()
                : DialogueState.start(c.id(), in.nickname(), clock.millis());
        LlmContext engineCtx = ctx.withPrompts(PromptVars.ids(pack));
        // TODO(judgment): 지난 대화 요약·일정(명세 20 입력)을 엔진 프롬프트에 넣는 자리. 엔진은 아직 받지 않는다
        Engine engine = new Engine(c, llm.engineClient(engineCtx), LlmJson::plan, LlmJson::verdict, clock::millis);
        Engine.TurnResult t =
                engine.runTurn(st, in.userMessage(), settings(in.engine(), engineCtx), false, false, null);
        if (t.replyClean().isBlank()) throw new IllegalStateException("빈 답장: " + t.replyRaw());

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("pipeline", ENGINE);
        trace.put("prompts", PromptVars.versions(pack));
        trace.put("emotionTag", t.emotion());
        trace.put("plan", t.plan());
        trace.put("chosen", t.chosen());
        trace.put("event", t.event());
        trace.put("lore", t.loreIds());
        trace.put("featuredBeat", t.featured());
        trace.put("penalties", t.penalties());
        trace.put("candidates", t.candidates()); // 품질 모드만: 후보 3개와 심사·감점 점수
        trace.put("judgeWhy", t.judgeWhy());
        trace.put("signals", t.signals());
        trace.put("metrics", t.metrics());
        trace.put("tensionAfter", t.tensionAfter());
        trace.put("statusAfter", t.statusAfter());
        trace.put("moodAfter", t.moodAfter());
        // 호감도는 엔진이 아니라 ChatReplier의 신호 읽기가 정한다 (SIMPLE과 같은 규칙, ADR 0022)
        // TODO(judgment): J-07 엔진 표정 태그(캐릭터 파일 emotions) → 명세 감정 이름. 지금은 태그 그대로
        return ChatOutcome.of(t.replyClean(), t.emotion(), st, trace);
    }

    /** 모델은 설정(routes)이 정하고, 엔진 비용 계산도 그 모델 가격으로 */
    private Engine.Settings settings(EngineOptions o, LlmContext ctx) {
        Engine.Settings d = Engine.Settings.defaults();
        return new Engine.Settings(
                llm.modelFor(LlmPurpose.ACTOR, ctx),
                llm.modelFor(LlmPurpose.DIRECTOR, ctx),
                llm.modelFor(LlmPurpose.JUDGE, ctx),
                o.useDirector() != null ? o.useDirector() : d.useDirector(),
                o.quality() != null ? o.quality() : d.quality(),
                o.eventFreq() != null ? o.eventFreq() : d.eventFreq(),
                d.directorMaxTokensFast(),
                d.directorMaxTokensQuality(),
                d.actorMaxTokens(),
                d.judgeMaxTokens(),
                llm.prices());
    }
}
