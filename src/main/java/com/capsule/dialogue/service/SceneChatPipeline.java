package com.capsule.dialogue.service;

import com.capsule.common.settings.RuntimeSettings;
import com.capsule.common.time.ClockConfig;
import com.capsule.dialogue.client.Llm;
import com.capsule.dialogue.client.LlmGateway;
import com.capsule.dialogue.client.LlmJson;
import com.capsule.dialogue.config.DialogueProperties;
import com.capsule.dialogue.domain.ChatMessage;
import com.capsule.dialogue.domain.PromptKey;
import com.capsule.dialogue.domain.PromptVersion;
import com.capsule.dialogue.dto.CharacterContent;
import com.capsule.dialogue.dto.ChatInput;
import com.capsule.dialogue.dto.ChatLine;
import com.capsule.dialogue.dto.ChatOutcome;
import com.capsule.dialogue.dto.LlmContext;
import com.capsule.dialogue.dto.LlmPurpose;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
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
    // 돌려보내는 방법을 돌려 쓴다: 한 문장("공부로 돌려보낸다")만 주면 매 턴 "공부나 해" 잔소리가 됐다
    static final List<String> CHATTY_MOVES = List.of(
            "이 얘기는 쉬는 시간에 마저 하자고 찜해 두고 공부로 돌아간다",
            "수다 떠는 사이 네가 몇 줄 더 썼는지 자랑하며 경쟁심을 건드린다",
            "다음 문제를 먼저 끝내는 쪽이 이기는 내기를 건다",
            "얘기의 뒷부분은 한 문제 풀고 오면 해 주겠다며 미끼를 던진다");

    /** @param turn 이번 세션 사용자 말 수: 수다가 이어지는 동안 매 턴 다른 방법 */
    static String chattyDirective(int turn) {
        String move = CHATTY_MOVES.get(turn % CHATTY_MOVES.size());
        return "공부 중인데 수다가 길어지고 있다. 방금 한 말에는 제대로 받아치되 짧게 하고, " + move + ". '공부나 해' 같은 잔소리는 하지 않는다.";
    }

    static final String TODAY = "TODAY";
    /** 한 말에 넣는 로어북 카드 수 상한: 여러 이름을 한꺼번에 꺼내도 프롬프트가 커지지 않게 */
    static final int LORE_CARDS = 2;

    private final LlmGateway llm;
    private final PromptService prompts;
    private final CharacterContents contents;
    private final SimpleChatPipeline generic;
    private final FactPicker factPicker;
    private final ExpressionPicker expressionPicker;
    private final RuntimeSettings<DialogueProperties> props;
    private final Clock clock;

    SceneChatPipeline(
            LlmGateway llm,
            PromptService prompts,
            CharacterContents contents,
            SimpleChatPipeline generic,
            FactPicker factPicker,
            ExpressionPicker expressionPicker,
            RuntimeSettings<DialogueProperties> props,
            Clock clock) {
        this.llm = llm;
        this.prompts = prompts;
        this.contents = contents;
        this.generic = generic;
        this.factPicker = factPicker;
        this.expressionPicker = expressionPicker;
        this.props = props;
        this.clock = clock;
    }

    @Override
    public String name() {
        return SCENE;
    }

    @Override
    public ChatOutcome reply(ChatInput in, LlmContext ctx, Consumer<String> onText) {
        long characterId = in.character().id();
        // 설정을 아직 안 채운 캐릭터는 공통 프롬프트(SIMPLE)로 말한다: 새 캐릭터 행만 넣어도 대화는 된다
        java.util.Optional<CharacterContent> found = contents.find(characterId);
        if (found.isEmpty()) return generic.reply(in, ctx, onText);
        CharacterContent c = found.get();
        PromptVersion system = prompts.resolve(PromptKey.SCENE_SYSTEM, characterId, in.pinnedPrompts());
        String fact = factPicker.pick(in, ctx).orElse(null); // 아는 사실은 결정 모델이 고른 하나만 (없으면 안 넣음)
        String card = card(c, in, fact, PromptVars.now(clock), today(c, clock));
        List<Llm.SystemBlock> blocks = List.of(
                new Llm.SystemBlock(
                        PromptService.render(system.body(), systemVars(c, in.stage(), in.nickname())), true),
                new Llm.SystemBlock(card, false));
        List<Llm.ChatTurn> messages =
                new ArrayList<>(PromptVars.turns(in.history(), props.current().historyLines()));
        messages.add(new Llm.ChatTurn("user", in.userMessage()));
        Llm.Result r = llm.complete(LlmPurpose.CHAT, blocks, messages, ctx.withPrompts(List.of(system.id())), onText);

        String reply = spoken(r);
        String emotion = expressionPicker.pick(c, in.userMessage(), reply, ctx); // 표정은 다 쓴 대사를 보고 결정 모델이

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("pipeline", SCENE);
        trace.put("prompts", PromptVars.versions(List.of(system)));
        trace.put("stage", in.stage());
        trace.put("fact", fact);
        trace.put("scene", card);
        trace.put("raw", r.text());
        return ChatOutcome.of(reply.strip(), emotion, trace);
    }

    /**
     * 답장 고정 블록(scene.system) 변수: 페르소나 + 지금 관계 단계의 관계 설명 · 말투 샘플.
     * 상태 반응은 따로(scene.reaction: 바이블 · 규칙 + 반응 카드에 그 상황 예시만)
     */
    static Map<String, String> systemVars(CharacterContent c, int stage, String nickname) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("bible", c.bible());
        vars.put("rules", c.rules());
        vars.put("speaker", c.speaker());
        vars.put("relation", PromptVars.orNone(c.toneFor(stage), "보통"));
        vars.put(
                "samples",
                c.samplesFor(stage).stream()
                        .map(s -> "사용자: " + s.user() + "\n" + c.speaker() + ": " + named(s.reply(), nickname))
                        .collect(Collectors.joining("\n\n")));
        return vars;
    }

    static String named(String text, String nickname) {
        return text.replace("{name}", PromptVars.name(nickname));
    }

    /**
     * 답장 JSON. JSON이 아닌 맨 글이면 null(그 글을 답장으로 씀).
     * 깨진 JSON(토큰 한도로 잘림, 앞이 잘린 `…","emotion":"경멸"}`)은 IllegalStateException: 그대로 두면 말풍선에 JSON 꼬리가 나가서
     * 부르는 쪽이 대체 대사로 돌린다
     */
    static JsonNode replyJson(Llm.Result r) {
        JsonNode json = LlmJson.objectIn(r.text());
        boolean truncated = "max_tokens".equals(r.stopReason()) || "length".equals(r.stopReason());
        // 깨진 JSON은 JSON 모양일 때만: 맨 글이 기본 형식이라 대사 속 "emotion"이나 {1, 2} 같은 글은 깨진 게 아니다
        if (json == null
                && (truncated
                        || r.text().strip().startsWith("{\"")
                        || JSON_KEY.matcher(r.text()).find()))
            throw new IllegalStateException("답장 JSON이 깨짐 (stop_reason=" + r.stopReason() + "): " + r.text());
        return json;
    }

    /** 이번 턴의 사실만: 시각 · 기억 · 지난 세션 · 일정(첫 대화) · 피할 말버릇 · 수다 길어짐 · 지금(길이 · 구성, 맨 끝) */
    static String card(CharacterContent c, ChatInput in, String fact, String now, String today) {
        List<String> L = new ArrayList<>();
        L.add("<상황>");
        L.add("사용자: " + in.nickname() + " / 시각: " + now
                + (in.sessionMinutes() > 0 ? " / 공부한 지: " + in.sessionMinutes() + "분" : "")
                // 안 붙이면 매 턴 "새벽 네 시에", "40분이나"를 꺼냈다 (2026-11-06 품질 테스트 11턴 중 4~5번)
                + " (시각 · 공부 시간은 물어보거나 꼭 필요할 때만 꺼낸다)");
        // 근황은 이 세션 첫 대화에만: 매 턴 · 쉬는 시간마다 넣었더니 묻지 않아도 매번 꺼냈다(실제 키 평가)
        if (today != null && in.history().isEmpty()) L.add(c.speaker() + " 오늘: " + today);
        // 기억 (ADR 0022 결정 16): 아는 사실은 결정 모델이 이번 말에 맞춰 고른 하나만, 세션 카드는 최근 2장, 원문은 메시지로 15줄
        if (fact != null) L.add("<꺼낼 수 있는 기억> " + fact + " (자연스러울 때만, 단정하지 말고)");
        // 로어북: 사용자가 방금 꺼낸 원작 얘기만 (키워드가 걸린 카드 최대 2장, 안 걸리면 아무것도 안 넣음)
        List<CharacterContent.Lore> lore = c.loreFor(in.userMessage(), LORE_CARDS);
        if (!lore.isEmpty())
            L.add("<사용자가 꺼낸 원작 얘기 (네 입장)>\n"
                    + lore.stream().map(l -> "- " + l.title() + ": " + l.text()).collect(Collectors.joining("\n"))
                    + "\n</사용자가 꺼낸 원작 얘기>");
        if (in.summary() != null && !in.summary().isBlank())
            L.add("<지난 세션 흐름 (최근 것이 아래)>\n" + in.summary() + "\n</지난 세션 흐름>");
        // 사용자 일정은 공부 시작(이 세션 첫 대화)에만 (PM 확정: 시작과 끝에만)
        if (in.history().isEmpty() && !in.memories().isEmpty())
            L.add("<사용자 일정>\n- " + String.join("\n- ", in.memories()) + "\n</사용자 일정>");
        List<String> avoid = avoid(c, in.history());
        if (!avoid.isEmpty()) L.add("피할 것: " + String.join(" / ", avoid));
        // 공부 중 · 쉬는 시간에 따라 말하는 법이 다르다 (명세 20, 캐릭터 설정 studyStyle · breakStyle). 맨 끝에 둔다
        if (!in.onBreak() && in.chatty()) L.add(chattyDirective(userTurns(in.history())));
        L.add("지금: " + (in.onBreak() ? c.breakStyle() : c.studyStyle()));
        L.add("</상황>");
        return String.join("\n", L);
    }

    /** JSON 답의 키 ("reply": · "emotion":) */
    private static final Pattern JSON_KEY = Pattern.compile("\"(reply|emotion)\"\\s*:");

    /** 답장 대사: 글 그대로 (scene.system은 대사만 쓰게 한다, 표정은 ExpressionPicker). 옛 형식 · 가짜 AI의 JSON이면 그 reply */
    static String spoken(Llm.Result r) {
        JsonNode json = replyJson(r);
        String reply = replyText(r.text(), json);
        if ((reply == null || reply.isBlank()) && json != null) reply = LlmJson.str(json.path("content")); // 공통 반응 형식
        if (reply == null || reply.isBlank()) throw new IllegalStateException("빈 답장: " + r.text());
        return reply.strip();
    }

    /** 답장 글: JSON의 reply. 대사를 글로 쓰고 뒤에 {"emotion": …}만 붙인 답(실제 키 평가 76번 중 1번)은 앞의 글 */
    static String replyText(String text, JsonNode json) {
        if (json == null) return text;
        String reply = LlmJson.str(json.path("reply"));
        if ((reply == null || reply.isBlank()) && text.indexOf('{') > 0) return text.substring(0, text.indexOf('{'));
        return reply;
    }

    /** 캐릭터의 오늘 근황: TODAY 대사를 요일(월 = 0)로 고른다 (결정 19). 없으면 null */
    static String today(CharacterContent c, Clock clock) {
        List<CharacterContent.Line> notes = c.linesFor(TODAY, 1);
        if (notes.isEmpty()) return null;
        int day = LocalDate.now(clock.withZone(ClockConfig.KST)).getDayOfWeek().getValue() - 1;
        return notes.get(day % notes.size()).text();
    }

    static int userTurns(List<ChatLine> history) {
        return (int) history.stream()
                .filter(l -> ChatMessage.USER.equals(l.sender()))
                .count();
    }

    /** 말버릇 규칙: 첫마디 금지는 늘, 세션 한 번은 이번 세션 캐릭터 대사에 이미 있으면 */
    /**
     * 첫마디 금지(NO_OPENING) 말로 시작하면 그 말과 뒤의 쉼표 · 공백을 뗀다 ("흥, 왔네" → "왔네").
     * '피할 것' 부탁만으로는 상태 반응(haiku)이 12번 중 5번 "흥,"으로 시작했다 (2026-11-06 품질 테스트).
     * 낱말 경계만: "흥미롭네"는 그대로. 떼고 남는 게 없으면 원문
     */
    static String dropOpening(CharacterContent c, String line) {
        String out = line;
        for (Map.Entry<String, String> h : c.habits().entrySet()) {
            if (!CharacterContent.NO_OPENING.equals(h.getValue())) continue;
            out = out.replaceFirst("^\\s*" + Pattern.quote(h.getKey()) + "(?=[\\s,.…!?~]|$)[\\s,.…!?~]*", "");
        }
        return out.isBlank() ? line : out;
    }

    static List<String> avoid(CharacterContent c, List<ChatLine> history) {
        List<String> out = new ArrayList<>();
        c.habits().forEach((phrase, rule) -> {
            if (CharacterContent.NO_OPENING.equals(rule)) out.add("첫마디에 \"" + phrase + "\" 쓰지 않기");
            else if (CharacterContent.ONCE_PER_SESSION.equals(rule)
                    && history.stream()
                            .anyMatch(l -> ChatMessage.CHARACTER.equals(l.sender())
                                    && l.content().contains(phrase))) out.add("\"" + phrase + "\" 쓰지 않기 (이번 세션에 이미 씀)");
        });
        return out;
    }
}
