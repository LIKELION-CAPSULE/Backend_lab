package com.capsule.dialogue.service;

import com.capsule.common.error.ApiException;
import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.dialogue.client.Llm;
import com.capsule.dialogue.client.LlmGateway;
import com.capsule.dialogue.client.LlmJson;
import com.capsule.dialogue.config.DialogueProperties;
import com.capsule.dialogue.config.LlmProperties;
import com.capsule.dialogue.domain.ChatMessage;
import com.capsule.dialogue.domain.EvalCase;
import com.capsule.dialogue.domain.EvalResult;
import com.capsule.dialogue.domain.EvalRun;
import com.capsule.dialogue.domain.PromptKey;
import com.capsule.dialogue.domain.PromptVersion;
import com.capsule.dialogue.dto.ChatLine;
import com.capsule.dialogue.dto.ChatOutcome;
import com.capsule.dialogue.dto.EvalCaseRequest;
import com.capsule.dialogue.dto.EvalCaseView;
import com.capsule.dialogue.dto.EvalResultView;
import com.capsule.dialogue.dto.EvalRunRequest;
import com.capsule.dialogue.dto.EvalRunView;
import com.capsule.dialogue.dto.LabConfig;
import com.capsule.dialogue.dto.LlmContext;
import com.capsule.dialogue.dto.LlmPurpose;
import com.capsule.dialogue.dto.RatingRequest;
import com.capsule.dialogue.dto.TranscriptTurn;
import com.capsule.dialogue.engine.DialogueState;
import com.capsule.dialogue.repository.EvalCaseRepository;
import com.capsule.dialogue.repository.EvalResultRepository;
import com.capsule.dialogue.repository.EvalRunRepository;
import com.capsule.dialogue.repository.LlmUsageRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * 실험실 평가: 골든 대본(케이스)을 한 조건으로 여러 번 돌려 결과를 쌓고, LLM 심사관 점수와 사람 점수로 비교한다.
 * 실행은 백그라운드에서 돌고(동시에 lab.eval-parallelism개), 화면은 결과가 쌓이는 것을 다시 읽는다.
 */
@Service
public class EvalService {

    private static final Logger log = LoggerFactory.getLogger(EvalService.class);
    private static final ExecutorService RUNNER = Executors.newVirtualThreadPerTaskExecutor();
    static final String DEFAULT_RUBRIC = """
            - 캐릭터의 성격과 말투가 처음부터 끝까지 같은가
            - 사용자의 말에 맞게 반응했는가 (딴소리·공부 재촉만 하지 않음)
            - 답이 1~2문장으로 짧은가
            - 같은 말이나 같은 시작을 되풀이하지 않는가
            - AI라는 티를 내거나 규칙을 어기지 않는가""";

    private final EvalCaseRepository cases;
    private final EvalRunRepository runs;
    private final EvalResultRepository results;
    private final LlmUsageRepository usages;
    private final LabService lab;
    private final PromptService prompts;
    private final LlmGateway gateway;
    private final LlmProperties llmProps;
    private final DialogueProperties dialogueProps;
    private final Clock clock;

    EvalService(
            EvalCaseRepository cases,
            EvalRunRepository runs,
            EvalResultRepository results,
            LlmUsageRepository usages,
            LabService lab,
            PromptService prompts,
            LlmGateway gateway,
            LlmProperties llmProps,
            DialogueProperties dialogueProps,
            Clock clock) {
        this.cases = cases;
        this.runs = runs;
        this.results = results;
        this.usages = usages;
        this.lab = lab;
        this.prompts = prompts;
        this.gateway = gateway;
        this.llmProps = llmProps;
        this.dialogueProps = dialogueProps;
        this.clock = clock;
    }

    /** 서버가 실행 도중 꺼졌으면 RUNNING으로 남은 실행을 실패로 정리 */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void failInterruptedRuns() {
        runs.findByStatus(EvalRun.RUNNING).forEach(r -> r.finish(EvalRun.FAILED, Instant.now(clock)));
    }

    // --- 케이스 ---

    public List<EvalCaseView> cases() {
        return cases.findAllByOrderByIdDesc().stream().map(EvalService::view).toList();
    }

    public EvalCaseView createCase(EvalCaseRequest r) {
        lab.character(r.characterId());
        return view(cases.save(new EvalCase(
                r.name(),
                r.characterId(),
                LlmJson.write(r.turns()),
                LlmJson.write(r.context() == null ? LabConfig.EMPTY : r.context()),
                r.rubric(),
                Instant.now(clock))));
    }

    @Transactional
    public EvalCaseView updateCase(long id, EvalCaseRequest r) {
        lab.character(r.characterId());
        EvalCase c = findCase(id);
        c.update(
                r.name(),
                r.characterId(),
                LlmJson.write(r.turns()),
                LlmJson.write(r.context() == null ? LabConfig.EMPTY : r.context()),
                r.rubric());
        return view(c);
    }

    @Transactional
    public void deleteCase(long id) {
        cases.delete(findCase(id)); // 결과도 같이 지워진다 (FK ON DELETE CASCADE)
    }

    // --- 실행 ---

    public EvalRunView start(EvalRunRequest r) {
        List<EvalCase> selected = cases.findAllById(r.caseIds());
        if (selected.size() != r.caseIds().size()) throw new ApiException(HttpStatus.NOT_FOUND, "EVAL_CASE_NOT_FOUND");
        String pipeline = lab.pipeline(r.pipeline() == null ? dialogueProps.chatPipeline() : r.pipeline())
                .name();
        LabConfig config = r.config() == null ? LabConfig.EMPTY : r.config();
        EvalRun run = runs.save(new EvalRun(
                r.label(),
                pipeline,
                LlmJson.write(config),
                List.copyOf(r.caseIds()),
                r.repeats(),
                r.judge(),
                Instant.now(clock)));
        RUNNER.submit(() -> execute(run, selected, config));
        return view(run, List.of(), Map.of());
    }

    public List<EvalRunView> runs() {
        return runs.findAllByOrderByIdDesc().stream()
                .map(run -> view(run, results.findByRunIdOrderByCaseIdAscRepeatNoAsc(run.id()), null))
                .toList();
    }

    public EvalRunView run(long id) {
        EvalRun run = runs.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVAL_RUN_NOT_FOUND"));
        Map<Long, String> names =
                cases.findAllById(run.caseIds()).stream().collect(Collectors.toMap(EvalCase::id, EvalCase::name));
        return view(run, results.findByRunIdOrderByCaseIdAscRepeatNoAsc(id), names);
    }

    @Transactional
    public EvalResultView rate(long resultId, RatingRequest r) {
        EvalResult result = results.findById(resultId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVAL_RESULT_NOT_FOUND"));
        result.rate(r.score(), r.note());
        String name = cases.findById(result.caseId()).map(EvalCase::name).orElse(null);
        return view(result, name);
    }

    private void execute(EvalRun run, List<EvalCase> selected, LabConfig config) {
        Semaphore slots = new Semaphore(Math.max(1, llmProps.lab().evalParallelism()));
        List<Future<?>> jobs = new ArrayList<>();
        String status = EvalRun.DONE;
        try {
            for (EvalCase c : selected)
                for (int i = 1; i <= run.repeats(); i++) {
                    int repeatNo = i;
                    jobs.add(RUNNER.submit(() -> {
                        slots.acquireUninterruptibly();
                        try {
                            runCase(run, c, repeatNo, config);
                        } finally {
                            slots.release();
                        }
                    }));
                }
            for (Future<?> job : jobs) job.get();
        } catch (Exception e) {
            log.error("평가 실행 {} 실패", run.id(), e);
            status = EvalRun.FAILED;
        }
        EvalRun done = runs.findById(run.id()).orElseThrow();
        done.finish(status, Instant.now(clock));
        runs.save(done);
    }

    /** 케이스 대본을 처음부터 끝까지 한 번: 사용자 말을 차례로 보내고 답을 이어 붙인다. 턴이 실패하면 거기서 멈춘다 */
    private void runCase(EvalRun run, EvalCase c, int repeatNo, LabConfig runConfig) {
        LabConfig context = LabService.config(c.context());
        LabConfig config = new LabConfig(
                context.nickname(),
                context.relationLevel(),
                context.summary(),
                context.memories(),
                runConfig.provider(),
                runConfig.model(),
                runConfig.temperature(),
                runConfig.maxTokens(),
                runConfig.pinnedPrompts(),
                runConfig.engine(),
                context.facts(),
                context.onBreak(),
                context.chatty(),
                context.sessionMinutes());
        LlmContext ctx = LlmContext.lab(LlmContext.EVAL, config);
        CharacterInfo character = lab.character(c.characterId());
        ChatPipeline pipeline = lab.pipeline(run.pipeline());
        List<ChatLine> history = new ArrayList<>();
        List<TranscriptTurn> transcript = new ArrayList<>();
        DialogueState state = null;
        String error = null;
        long total = 0;
        for (String userText : Arrays.asList(LlmJson.read(c.turns(), String[].class))) {
            long t0 = System.nanoTime();
            try {
                ChatOutcome o = lab.chatTurn(
                        pipeline, LabService.input(character, config, List.copyOf(history), userText, state), ctx);
                long ms = LabService.elapsedMs(t0);
                total += ms;
                transcript.add(
                        new TranscriptTurn(userText, o.reply(), o.emotion(), lab.points(o, character.id()), ms, null));
                history.add(new ChatLine(ChatMessage.USER, userText));
                history.add(new ChatLine(ChatMessage.CHARACTER, o.reply()));
                state = o.engineState();
            } catch (RuntimeException e) {
                long ms = LabService.elapsedMs(t0);
                total += ms;
                transcript.add(new TranscriptTurn(userText, null, null, null, ms, e.toString()));
                error = e.toString();
                break;
            }
        }
        BigDecimal judgeScore = null;
        String judgeNote = null;
        if (run.judge() && error == null) {
            try {
                JsonNode verdict = judge(character, c.rubric(), transcript, config.pinnedOrEmpty(), ctx.traceId());
                judgeScore =
                        BigDecimal.valueOf(verdict.path("score").asDouble()).setScale(1, RoundingMode.HALF_UP);
                judgeNote = LlmJson.str(verdict.path("reason"));
            } catch (RuntimeException e) {
                judgeNote = "심사 실패: " + e;
            }
        }
        results.save(new EvalResult(
                run.id(),
                c.id(),
                repeatNo,
                ctx.traceId(),
                LlmJson.write(transcript),
                judgeScore,
                judgeNote,
                (int) total,
                LabService.cost(usages.findByTraceIdOrderById(ctx.traceId())),
                error,
                Instant.now(clock)));
    }

    /** 심사관은 실험 조건(공급자·모델 덮어쓰기)을 따르지 않는다: 실행끼리 같은 잣대로 비교하려고 */
    private JsonNode judge(
            CharacterInfo character,
            String rubric,
            List<TranscriptTurn> transcript,
            Map<String, Long> pinned,
            UUID traceId) {
        PromptVersion p = prompts.resolve(PromptKey.EVAL_JUDGE, character.id(), pinned);
        Map<String, String> vars = PromptVars.character(character);
        vars.put("rubric", PromptVars.orNone(rubric, DEFAULT_RUBRIC));
        List<ChatLine> lines = new ArrayList<>();
        for (TranscriptTurn t : transcript) {
            lines.add(new ChatLine(ChatMessage.USER, t.user()));
            lines.add(new ChatLine(ChatMessage.CHARACTER, t.reply()));
        }
        vars.put("transcript", PromptVars.transcript(lines, character.name()));
        LlmContext judgeCtx =
                new LlmContext(LlmContext.EVAL, traceId, null, null, List.of(p.id()), null, null, null, null, null);
        Llm.Result r = gateway.complete(
                LlmPurpose.EVAL_JUDGE,
                List.of(new Llm.SystemBlock(PromptService.render(p.body(), vars), false)),
                List.of(new Llm.ChatTurn("user", "채점해 줘.")),
                judgeCtx,
                null);
        JsonNode json = LlmJson.objectIn(r.text());
        if (json == null || !json.path("score").isNumber()) throw new IllegalStateException("심사 형식 오류: " + r.text());
        return json;
    }

    private EvalCase findCase(long id) {
        return cases.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVAL_CASE_NOT_FOUND"));
    }

    private static EvalCaseView view(EvalCase c) {
        return new EvalCaseView(
                c.id(),
                c.name(),
                c.characterId(),
                Arrays.asList(LlmJson.read(c.turns(), String[].class)),
                LabService.config(c.context()),
                c.rubric(),
                c.createdAt());
    }

    /** names가 null이면 결과 목록은 빼고 집계만 */
    private EvalRunView view(EvalRun run, List<EvalResult> list, Map<Long, String> names) {
        OptionalDouble judge = list.stream()
                .map(EvalResult::judgeScore)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .average();
        OptionalDouble human = list.stream()
                .map(EvalResult::humanScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average();
        OptionalDouble latency = list.stream().mapToInt(EvalResult::latencyMs).average();
        return new EvalRunView(
                run.id(),
                run.label(),
                run.pipeline(),
                LabService.config(run.config()),
                run.caseIds(),
                run.repeats(),
                run.judge(),
                run.status(),
                run.createdAt(),
                run.finishedAt(),
                run.caseIds().size() * run.repeats(),
                list.size(),
                (int) list.stream().filter(x -> x.error() != null).count(),
                judge.isPresent() ? judge.getAsDouble() : null,
                human.isPresent() ? human.getAsDouble() : null,
                latency.isPresent() ? latency.getAsDouble() : null,
                list.stream()
                        .map(EvalResult::costUsd)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                names == null
                        ? List.of()
                        : list.stream().map(x -> view(x, names.get(x.caseId()))).toList());
    }

    private static EvalResultView view(EvalResult r, String caseName) {
        return new EvalResultView(
                r.id(),
                r.caseId(),
                caseName,
                r.repeatNo(),
                r.traceId(),
                Arrays.asList(LlmJson.read(r.transcript(), TranscriptTurn[].class)),
                r.judgeScore(),
                r.judgeNote(),
                r.humanScore(),
                r.humanNote(),
                r.latencyMs(),
                r.costUsd(),
                r.error(),
                r.createdAt());
    }
}
