package com.capsule.dialogue.service;

import com.capsule.common.error.ApiException;
import com.capsule.content.api.CharacterQuery;
import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.content.api.RoomQuery;
import com.capsule.dialogue.client.LlmGateway;
import com.capsule.dialogue.client.LlmJson;
import com.capsule.dialogue.config.DialogueProperties;
import com.capsule.dialogue.config.LlmProperties;
import com.capsule.dialogue.domain.ChatMessage;
import com.capsule.dialogue.domain.LabSession;
import com.capsule.dialogue.domain.LabTurn;
import com.capsule.dialogue.domain.LlmUsage;
import com.capsule.dialogue.domain.PromptKey;
import com.capsule.dialogue.dto.ChatInput;
import com.capsule.dialogue.dto.ChatLine;
import com.capsule.dialogue.dto.ChatOutcome;
import com.capsule.dialogue.dto.LabConfig;
import com.capsule.dialogue.dto.LabMeta;
import com.capsule.dialogue.dto.LabSessionRequest;
import com.capsule.dialogue.dto.LabSessionView;
import com.capsule.dialogue.dto.LabTurnView;
import com.capsule.dialogue.dto.LlmContext;
import com.capsule.dialogue.dto.LlmPurpose;
import com.capsule.dialogue.dto.PlaygroundRequest;
import com.capsule.dialogue.dto.PlaygroundResult;
import com.capsule.dialogue.dto.RatingRequest;
import com.capsule.dialogue.dto.ReactionLine;
import com.capsule.dialogue.dto.UsageSummary;
import com.capsule.dialogue.dto.UsageView;
import com.capsule.dialogue.engine.DialogueState;
import com.capsule.dialogue.repository.LabSessionRepository;
import com.capsule.dialogue.repository.LabTurnRepository;
import com.capsule.dialogue.repository.LlmUsageRepository;
import com.capsule.relation.api.RelationQuery;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LLM 실험실 (ADR 0017): 서비스와 같은 파이프라인·작성기를 실험 조건(LabConfig)으로 불러 품질을 본다.
 * 실제 세션·사용자 데이터는 건드리지 않고, 결과는 실험실 테이블과 llm_usages(source=LAB)에만 남긴다.
 */
@Service
public class LabService {

    /** 서비스의 대체 대사 기준(명세 19·20). 실험실은 기다리되 넘었는지 표시만 한다 */
    static final long BUDGET_MS = 5000;

    static final List<String> TRIGGERS =
            List.of("GREETING", "AWAY", "RETURN", "BREAK", "BREAK_END", "DROWSY", "YAWN", "WAVE", "HEAD_TURN");

    private static final ExecutorService THREADS = Executors.newVirtualThreadPerTaskExecutor();

    private static final String USAGE_SUMMARY = """
            SELECT (created_at AT TIME ZONE 'Asia/Seoul')::date AS day, source, provider, model, purpose,
                   count(*) AS calls,
                   count(*) FILTER (WHERE status = 'ERROR') AS errors,
                   sum(input_tokens) AS input_tokens,
                   sum(output_tokens) AS output_tokens,
                   sum(cache_read_tokens) AS cache_read_tokens,
                   sum(cache_write_tokens) AS cache_write_tokens,
                   sum(cost_usd) AS cost_usd,
                   avg(latency_ms) AS avg_latency_ms,
                   percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms) AS p95_latency_ms,
                   count(*) FILTER (WHERE latency_ms > 5000) AS over5s
            FROM llm_usages
            WHERE created_at >= now() - make_interval(days => :days)
            GROUP BY 1, 2, 3, 4, 5
            ORDER BY 1 DESC, cost_usd DESC""";

    private final LabSessionRepository sessions;
    private final LabTurnRepository turns;
    private final LlmUsageRepository usages;
    private final Map<String, ChatPipeline> pipelines;
    private final ChatReplier replier;
    private final RelationQuery relationQuery;
    private final ReactionWriter reactionWriter;
    private final ClosingWriter closingWriter;
    private final LlmGateway gateway;
    private final CharacterQuery characters;
    private final RoomQuery rooms;
    private final LlmProperties llmProps;
    private final DialogueProperties dialogueProps;
    private final JdbcClient jdbc;
    private final Clock clock;

    LabService(
            LabSessionRepository sessions,
            LabTurnRepository turns,
            LlmUsageRepository usages,
            List<ChatPipeline> pipelines,
            ChatReplier replier,
            RelationQuery relationQuery,
            ReactionWriter reactionWriter,
            ClosingWriter closingWriter,
            LlmGateway gateway,
            CharacterQuery characters,
            RoomQuery rooms,
            LlmProperties llmProps,
            DialogueProperties dialogueProps,
            JdbcTemplate jdbcTemplate,
            Clock clock) {
        this.sessions = sessions;
        this.turns = turns;
        this.usages = usages;
        this.pipelines = pipelines.stream().collect(Collectors.toMap(ChatPipeline::name, Function.identity()));
        this.replier = replier;
        this.relationQuery = relationQuery;
        this.reactionWriter = reactionWriter;
        this.closingWriter = closingWriter;
        this.gateway = gateway;
        this.characters = characters;
        this.rooms = rooms;
        this.llmProps = llmProps;
        this.dialogueProps = dialogueProps;
        this.jdbc = JdbcClient.create(jdbcTemplate);
        this.clock = clock;
    }

    public LabMeta meta() {
        List<LabMeta.Route> routes = Arrays.stream(LlmPurpose.values())
                .map(p -> {
                    LlmProperties.Route r = llmProps.route(p);
                    Map<String, String> models = new LinkedHashMap<>();
                    for (String name : List.of("anthropic", "openai")) models.put(name, modelOrNull(name, r));
                    return new LabMeta.Route(p.name(), r.tier(), r.maxTokens(), r.temperature(), models);
                })
                .toList();
        return new LabMeta(
                gateway.defaultProvider(),
                gateway.readiness().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> new LabMeta.Provider(e.getKey(), e.getValue()))
                        .toList(),
                pipelines.keySet().stream().sorted().toList(),
                dialogueProps.chatPipeline(),
                routes,
                Arrays.stream(PromptKey.values())
                        .map(k -> new LabMeta.Key(k.key(), k.format(), k.characterOnly(), k.description()))
                        .toList(),
                rooms.activeRooms().stream()
                        .flatMap(room -> room.characters().stream())
                        .map(c -> new LabMeta.Character(c.id(), c.name()))
                        .toList(),
                TRIGGERS,
                dialogueProps.emotions(),
                llmProps.lab().token() != null && !llmProps.lab().token().isBlank());
    }

    // --- 실험 대화방 ---

    public List<LabSessionView> sessions() {
        return sessions.findAllByOrderByIdDesc().stream()
                .map(s -> view(s, List.of(), turns.countByLabSessionId(s.id())))
                .toList();
    }

    public LabSessionView session(long id) {
        LabSession s = find(id);
        List<LabTurnView> list =
                turns.findByLabSessionIdOrderById(id).stream().map(this::view).toList();
        return view(s, list, list.size());
    }

    public LabSessionView create(LabSessionRequest r) {
        if (r.characterId() == null) throw new ApiException(HttpStatus.BAD_REQUEST, "CHARACTER_REQUIRED");
        character(r.characterId());
        String pipeline = pipeline(r.pipeline() == null ? dialogueProps.chatPipeline() : r.pipeline())
                .name();
        String title = r.title() == null || r.title().isBlank() ? "실험 " + Instant.now(clock) : r.title();
        LabSession s = sessions.save(new LabSession(
                title,
                r.characterId(),
                pipeline,
                LlmJson.write(r.config() == null ? LabConfig.EMPTY : r.config()),
                Instant.now(clock)));
        return view(s, List.of(), 0);
    }

    public LabSessionView update(long id, LabSessionRequest r) {
        LabSession s = find(id);
        if (r.pipeline() != null) pipeline(r.pipeline());
        s.update(r.title(), r.pipeline(), r.config() == null ? null : LlmJson.write(r.config()), Instant.now(clock));
        sessions.save(s);
        return session(id);
    }

    @Transactional
    public LabSessionView reset(long id) {
        LabSession s = find(id);
        turns.deleteBySession(id);
        s.saveState(null, Instant.now(clock));
        return view(s, List.of(), 0);
    }

    @Transactional
    public void delete(long id) {
        sessions.delete(find(id)); // 턴은 FK ON DELETE CASCADE
    }

    /** 한 턴: 서비스와 같은 파이프라인을 실험 조건으로 부른다. 실패한 턴도 남긴다 (오류를 보려고) */
    public LabTurnView turn(long sessionId, String text) {
        LabSession s = find(sessionId);
        LabConfig config = config(s.config());
        List<ChatLine> history = new ArrayList<>();
        for (LabTurn t : turns.findByLabSessionIdOrderById(sessionId)) {
            if (t.reply() == null) continue;
            history.add(new ChatLine(ChatMessage.USER, t.userText()));
            history.add(new ChatLine(ChatMessage.CHARACTER, t.reply()));
        }
        DialogueState state = s.state() == null ? null : LlmJson.read(s.state(), DialogueState.class);
        LlmContext ctx = LlmContext.lab(LlmContext.LAB, config);
        long t0 = System.nanoTime();
        ChatOutcome out = null;
        String error = null;
        try {
            out = chatTurn(
                    pipeline(s.pipeline()), input(character(s.characterId()), config, history, text, state), ctx);
        } catch (RuntimeException e) {
            error = e.toString();
        }
        int ms = (int) elapsedMs(t0);
        List<LlmUsage> used = usages.findByTraceIdOrderById(ctx.traceId());
        LabTurn turn = turns.save(new LabTurn(
                sessionId,
                ctx.traceId(),
                text,
                out == null ? null : out.reply(),
                out == null ? null : out.emotion(),
                points(out),
                error,
                ms,
                cost(used),
                LlmJson.write(out == null ? Map.of() : out.trace()),
                Instant.now(clock)));
        if (out != null && out.engineState() != null) {
            s.saveState(LlmJson.write(out.engineState()), Instant.now(clock));
            sessions.save(s);
        }
        return view(turn, used);
    }

    @Transactional
    public LabTurnView rate(long turnId, RatingRequest r) {
        LabTurn t =
                turns.findById(turnId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LAB_TURN_NOT_FOUND"));
        t.rate(r.score(), r.note());
        return view(t);
    }

    // --- 한 번 실행 (같은 입력을 n번: 답이 얼마나 흔들리는지) ---

    public List<PlaygroundResult> playground(PlaygroundRequest r) {
        CharacterInfo c = character(r.characterId());
        LabConfig config = r.config() == null ? LabConfig.EMPTY : r.config();
        List<ChatLine> history = r.history() == null ? List.of() : r.history();
        String nickname = config.nicknameOrDefault();
        Map<String, Long> pinned = config.pinnedOrEmpty();
        Function<LlmContext, Map<String, Object>> call = switch (r.kind()) {
            case "chat" -> {
                ChatPipeline p = pipeline(r.pipeline() == null ? dialogueProps.chatPipeline() : r.pipeline());
                String message = required(r.message(), "message");
                yield ctx -> {
                    ChatOutcome o = chatTurn(p, input(c, config, history, message, null), ctx);
                    return map(
                            "reply",
                            o.reply(),
                            "emotion",
                            o.emotion(),
                            "signal",
                            o.signal(),
                            "affinity",
                            points(o),
                            "trace",
                            o.trace());
                };
            }
            case "reaction" -> {
                String trigger = required(r.trigger(), "trigger");
                Integer intensity = "AWAY".equals(trigger) ? (r.intensity() == null ? 1 : r.intensity()) : null;
                yield ctx -> {
                    ReactionLine l = reactionWriter.generate(
                            c,
                            trigger,
                            intensity,
                            config.relationLevelOrDefault(),
                            nickname,
                            config.summary(),
                            history,
                            pinned,
                            ctx);
                    return map("content", l.content(), "emotion", l.emotion());
                };
            }
            case "summary" ->
                ctx -> map(
                        "summary", closingWriter.mergeSummary(c, nickname, r.previousSummary(), history, pinned, ctx));
            case "closing" ->
                ctx -> map(
                        "closingLine",
                        closingWriter.closingLine(
                                c,
                                nickname,
                                r.studySeconds() == null ? 3300 : r.studySeconds(),
                                r.endType() == null ? "NORMAL" : r.endType(),
                                pinned,
                                ctx));
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_KIND");
        };
        int n = Math.max(1, Math.min(10, r.repeat() == null ? 1 : r.repeat()));
        List<CompletableFuture<PlaygroundResult>> runs = IntStream.rangeClosed(1, n)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> once(i, call, config), THREADS))
                .toList();
        return runs.stream().map(CompletableFuture::join).toList();
    }

    // --- 호출 기록 ---

    public List<UsageView> recentUsages() {
        return usages.findTop100ByOrderByIdDesc().stream().map(UsageView::from).toList();
    }

    public List<UsageView> trace(UUID traceId) {
        return usages.findByTraceIdOrderById(traceId).stream()
                .map(UsageView::from)
                .toList();
    }

    public List<UsageSummary> usageSummary(int days) {
        return jdbc.sql(USAGE_SUMMARY)
                .param("days", days)
                .query(UsageSummary.class)
                .list();
    }

    // --- 공통 (EvalService도 씀) ---

    static ChatInput input(
            CharacterInfo c, LabConfig config, List<ChatLine> history, String text, DialogueState state) {
        return new ChatInput(
                c,
                config.nicknameOrDefault(),
                config.relationLevelOrDefault(),
                config.summary(),
                config.memoriesOrEmpty(),
                history,
                text,
                state,
                config.pinnedOrEmpty(),
                config.engineOrDefault());
    }

    static BigDecimal cost(List<LlmUsage> used) {
        return used.stream().map(LlmUsage::costUsd).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    static LabConfig config(String json) {
        return json == null ? LabConfig.EMPTY : LlmJson.read(json, LabConfig.class);
    }

    /** 서비스(20)와 같은 한 턴: 답장 + 신호 */
    ChatOutcome chatTurn(ChatPipeline pipeline, ChatInput in, LlmContext ctx) {
        return replier.reply(pipeline, in, ctx);
    }

    /** 서비스였다면 이 턴의 호감도 점수 (하루 상한 전). 실험실은 호감도를 바꾸지 않는다 */
    Integer points(ChatOutcome o) {
        return o == null || o.signal() == null ? null : relationQuery.chatPoints(o.signal());
    }

    ChatPipeline pipeline(String name) {
        ChatPipeline p = pipelines.get(name);
        if (p == null) throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_PIPELINE");
        return p;
    }

    CharacterInfo character(long id) {
        return characters.find(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHARACTER_NOT_FOUND"));
    }

    private PlaygroundResult once(int n, Function<LlmContext, Map<String, Object>> call, LabConfig config) {
        LlmContext ctx = LlmContext.lab(LlmContext.LAB, config);
        long t0 = System.nanoTime();
        Map<String, Object> output = null;
        String error = null;
        try {
            output = call.apply(ctx);
        } catch (RuntimeException e) {
            error = e.toString();
        }
        long ms = elapsedMs(t0);
        List<LlmUsage> used = usages.findByTraceIdOrderById(ctx.traceId());
        return new PlaygroundResult(
                n,
                output,
                error,
                ms,
                ms > BUDGET_MS,
                cost(used),
                used.stream().map(UsageView::from).toList());
    }

    private LabSession find(long id) {
        return sessions.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LAB_SESSION_NOT_FOUND"));
    }

    private LabSessionView view(LabSession s, List<LabTurnView> list, long count) {
        return new LabSessionView(
                s.id(),
                s.title(),
                s.characterId(),
                s.pipeline(),
                config(s.config()),
                count,
                s.createdAt(),
                s.updatedAt(),
                list);
    }

    private LabTurnView view(LabTurn t) {
        return view(t, usages.findByTraceIdOrderById(t.traceId()));
    }

    private LabTurnView view(LabTurn t, List<LlmUsage> used) {
        return new LabTurnView(
                t.id(),
                t.traceId(),
                t.userText(),
                t.reply(),
                t.emotion(),
                t.affinity(),
                t.error(),
                t.latencyMs(),
                t.latencyMs() > BUDGET_MS,
                t.costUsd(),
                LlmJson.tree(t.trace()),
                t.rating(),
                t.ratingNote(),
                t.createdAt(),
                used.stream().map(UsageView::from).toList());
    }

    private String modelOrNull(String provider, LlmProperties.Route route) {
        try {
            return llmProps.model(provider, route);
        } catch (IllegalStateException e) {
            return null;
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank())
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", Map.<String, Object>of("field", name));
        return value;
    }

    /** null 값을 허용하는 순서 있는 Map (Map.of는 null을 못 넣음) */
    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
