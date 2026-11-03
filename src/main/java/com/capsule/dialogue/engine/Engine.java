package com.capsule.dialogue.engine;

import com.capsule.dialogue.engine.Analysis.Craft;
import com.capsule.dialogue.engine.Analysis.Signals;
import com.capsule.dialogue.engine.Content.Beat;
import com.capsule.dialogue.engine.Content.Character;
import com.capsule.dialogue.engine.Content.LoreCard;
import com.capsule.dialogue.engine.Content.WorldEvent;
import com.capsule.dialogue.engine.DialogueState.HistoryMessage;
import com.capsule.dialogue.engine.DialogueState.TurnSummary;
import com.capsule.dialogue.engine.Llm.ChatTurn;
import com.capsule.dialogue.engine.Llm.Role;
import com.capsule.dialogue.engine.Llm.SystemBlock;
import com.capsule.dialogue.engine.Planning.DirectorPlan;
import com.capsule.dialogue.engine.Planning.Plan;
import com.capsule.dialogue.engine.Planning.SubPlan;
import com.capsule.dialogue.engine.Scoring.Penalty;
import com.capsule.dialogue.engine.Scoring.Tagged;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * 한 턴 파이프라인: 사용자 분석 → 사건 → 대화 기술 리포트 → 비트 후보 → 감독(LLM) → 계획 정규화 → 세계 지식
 * → 배우(LLM, 1개 스트리밍 또는 3개 병렬) → 코드 감점 → 심사(LLM, 품질 모드) → 상태 갱신.
 * 상태는 제자리에서 바뀐다. 예외가 나면 호출자는 상태를 저장하지 말 것(원본 상태 유지).
 */
public final class Engine {

    public record Settings(
            String actorModel,
            String directorModel,
            String judgeModel,
            boolean useDirector,
            boolean quality,
            double eventFreq,
            int directorMaxTokensFast,
            int directorMaxTokensQuality,
            int actorMaxTokens,
            int judgeMaxTokens,
            Map<String, Llm.Price> prices) {
        public static Settings defaults() {
            return new Settings(
                    "claude-sonnet-5-5",
                    "claude-haiku-5-5",
                    "claude-haiku-5-5",
                    true,
                    false,
                    0.3,
                    500,
                    900,
                    700,
                    300,
                    Llm.DEFAULT_PRICES);
        }

        double cost(String model, Llm.Usage u) {
            Llm.Price p = prices.get(model);
            return p == null ? 0 : p.cost(u);
        }
    }

    public record Verdict(List<Double> scores, Integer pick, String why) {}

    public interface Listener {
        default void onEvent(WorldEvent e) {}

        default void onStage(String stage) {}

        default void onText(String clean, String emotion) {}
    }

    public record Candidate(
            SubPlan sub,
            String raw,
            String clean,
            String tag,
            List<Penalty> penalties,
            double judge,
            double fin,
            Llm.Result result) {}

    public record Metrics(
            Long directorMs,
            double directorCost,
            boolean directorParsed,
            Long actorTtftMs,
            long actorTotalMs,
            double actorCost,
            double judgeCost,
            long waitMs,
            double costUsd,
            Llm.Usage actorUsage,
            String actorModel,
            boolean thinkingSeen,
            String stopReason) {}

    public record TurnResult(
            TurnSummary summary,
            String replyRaw,
            String replyClean,
            String emotion,
            Plan plan,
            SubPlan chosen,
            WorldEvent event,
            List<String> loreIds,
            Beat featured,
            List<Penalty> penalties,
            List<Candidate> candidates,
            String judgeWhy,
            Signals signals,
            Metrics metrics,
            int tensionAfter,
            int statusAfter,
            String moodAfter) {}

    private final Character c;
    private final Llm.Client llm;
    private final Function<String, DirectorPlan> planParser;
    private final Function<String, Verdict> verdictParser;
    private final LongSupplier clock;
    private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();

    public Engine(
            Character c,
            Llm.Client llm,
            Function<String, DirectorPlan> planParser,
            Function<String, Verdict> verdictParser,
            LongSupplier clock) {
        this.c = c;
        this.llm = llm;
        this.planParser = planParser;
        this.verdictParser = verdictParser;
        this.clock = clock;
    }

    public Character character() {
        return c;
    }

    public TurnResult runTurn(
            DialogueState st, String userText, Settings s, boolean forceEvent, boolean stream, Listener ls) {
        Listener L = ls != null ? ls : new Listener() {};
        long now = clock.getAsLong();
        Signals sig = Analysis.analyzeUser(st, userText, c);
        WorldEvent event = Analysis.rollEvent(st, c, s.eventFreq(), forceEvent, sig);
        if (event != null) L.onEvent(event);
        Craft craft = Analysis.craft(st);
        List<Beat> beats = Analysis.eligibleBeats(st, c);
        Beat featured =
                !beats.isEmpty() && st.rng() < 0.35 ? beats.get((int) Math.floor(st.rng() * beats.size())) : null;
        int nPlans = s.quality() ? 3 : 1;

        Llm.Result director = null;
        DirectorPlan raw = null;
        if (s.useDirector()) {
            List<String> directives = Prompts.directives(st, craft, beats, featured, sig, nPlans, c);
            String input = Prompts.directorInput(st, userText, event, directives, c, now);
            director = llm.call(new Llm.Call(
                    Role.DIRECTOR,
                    s.directorModel(),
                    List.of(new SystemBlock(c.directorSystem(), true)),
                    List.of(new ChatTurn("user", input)),
                    s.quality() ? s.directorMaxTokensQuality() : s.directorMaxTokensFast()));
            raw = parse(planParser, director.text());
        }
        Plan plan = Planning.normalize(st, raw, craft, beats, sig, c);
        List<LoreCard> lore = Planning.pickLore(userText, plan, event, c);

        List<Candidate> cands = new ArrayList<>();
        Llm.Result judge = null;
        String judgeWhy = "";
        Candidate chosen;
        if (s.quality()) {
            List<SubPlan> subs = new ArrayList<>();
            for (int i = 0; i < 3; i++)
                subs.add(plan.plans().get(i % plan.plans().size()));
            L.onStage("후보 3개 쓰는 중");
            List<Future<Llm.Result>> fs = new ArrayList<>();
            for (SubPlan sub : subs) {
                String card = Prompts.sceneCard(st, userText, plan, sub, lore, event, craft, sig, c, now);
                Callable<Llm.Result> task = () -> llm.call(actorCall(st, s, card));
                fs.add(pool.submit(task));
            }
            for (int i = 0; i < 3; i++) {
                Llm.Result r = await(fs.get(i));
                Tagged t = Scoring.splitTag(r.text());
                String clean = t.text().trim();
                cands.add(new Candidate(
                        subs.get(i),
                        r.text(),
                        clean,
                        t.tag(),
                        Scoring.penalties(st, clean, t.tag(), subs.get(i), c),
                        0,
                        0,
                        r));
            }
            L.onStage("심사 중");
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < cands.size(); i++)
                lines.add("후보 " + (i + 1) + " [수: "
                        + c.move(cands.get(i).sub().move()).name() + "]: "
                        + cands.get(i).clean());
            judge = llm.call(new Llm.Call(
                    Role.JUDGE,
                    s.judgeModel(),
                    List.of(new SystemBlock(c.judgeSystem(), false)),
                    List.of(new ChatTurn("user", Prompts.judgeInput(st, userText, lines, c))),
                    s.judgeMaxTokens()));
            Verdict v = parse(verdictParser, judge.text());
            judgeWhy = v != null && v.why() != null ? v.why() : "";
            List<Candidate> scored = new ArrayList<>();
            for (int i = 0; i < cands.size(); i++) {
                Candidate k = cands.get(i);
                double js = v != null
                                && v.scores() != null
                                && i < v.scores().size()
                                && v.scores().get(i) != null
                        ? v.scores().get(i)
                        : 0;
                scored.add(new Candidate(
                        k.sub(),
                        k.raw(),
                        k.clean(),
                        k.tag(),
                        k.penalties(),
                        js,
                        js + Scoring.sum(k.penalties()),
                        k.result()));
            }
            cands = scored;
            chosen = cands.get(0);
            for (Candidate k : cands)
                if (k.fin() > chosen.fin() || (k.fin() == chosen.fin() && k.judge() > chosen.judge())) chosen = k;
        } else {
            SubPlan sub = plan.plans().get(0);
            String card = Prompts.sceneCard(st, userText, plan, sub, lore, event, craft, sig, c, now);
            Llm.Result r = stream
                    ? llm.stream(actorCall(st, s, card), acc -> {
                        Tagged t = Scoring.splitTag(acc);
                        L.onText(t.text(), t.tag());
                    })
                    : llm.call(actorCall(st, s, card));
            Tagged t = Scoring.splitTag(r.text());
            String clean = t.text().trim();
            chosen = new Candidate(
                    sub, r.text(), clean, t.tag(), Scoring.penalties(st, clean, t.tag(), sub, c), 0, 0, r);
            cands.add(chosen);
        }

        st.history.add(new HistoryMessage("user", userText == null || userText.isEmpty() ? "(사용자는 말이 없었다)" : userText));
        st.history.add(new HistoryMessage("assistant", chosen.raw().isEmpty() ? "(빈 응답)" : chosen.raw()));
        Effects.apply(st, plan, chosen.sub(), event, chosen.tag(), c);
        st.pendingFixed.clear();

        List<String> signals = new ArrayList<>(sig.flags(false));
        if (sig.motif() != null) signals.add("motif");
        TurnSummary summary = new TurnSummary(
                st.turns.size() + 1,
                userText,
                chosen.clean(),
                chosen.tag(),
                chosen.sub().move(),
                chosen.sub().askType(),
                event != null ? event.id() : null,
                signals,
                chosen.penalties().stream().map(Penalty::label).toList());
        st.turns.add(summary);

        double actorCost = cands.stream()
                .mapToDouble(k -> s.cost(s.actorModel(), k.result().usage()))
                .sum();
        double directorCost = director != null ? s.cost(s.directorModel(), director.usage()) : 0;
        double judgeCost = judge != null ? s.cost(s.judgeModel(), judge.usage()) : 0;
        long actorTotal =
                cands.stream().mapToLong(k -> k.result().totalMs()).max().orElse(0);
        Long ttft = chosen.result().ttftMs();
        long wait = (director != null ? director.totalMs() : 0)
                + (s.quality()
                        ? actorTotal + (judge != null ? judge.totalMs() : 0)
                        : (ttft != null ? ttft : actorTotal));
        Metrics m = new Metrics(
                director != null ? director.totalMs() : null,
                directorCost,
                raw != null,
                ttft,
                actorTotal,
                actorCost,
                judgeCost,
                wait,
                directorCost + actorCost + judgeCost,
                chosen.result().usage(),
                s.actorModel(),
                chosen.result().thinkingSeen(),
                chosen.result().stopReason());
        return new TurnResult(
                summary,
                chosen.raw(),
                chosen.clean(),
                chosen.tag(),
                plan,
                chosen.sub(),
                event,
                lore.stream().map(LoreCard::id).toList(),
                featured,
                chosen.penalties(),
                s.quality() ? cands : List.of(),
                judgeWhy,
                sig,
                m,
                st.tension,
                st.status,
                st.mood);
    }

    private Llm.Call actorCall(DialogueState st, Settings s, String card) {
        List<ChatTurn> msgs = new ArrayList<>();
        for (HistoryMessage h : st.history) msgs.add(new ChatTurn(h.role(), h.text()));
        msgs.add(new ChatTurn("user", card));
        return new Llm.Call(
                Role.ACTOR, s.actorModel(), List.of(new SystemBlock(c.actorSystem(), true)), msgs, s.actorMaxTokens());
    }

    /** 첫 '{'부터 마지막 '}'까지만 잘라 파서에 넘긴다. 실패하면 null(엔진은 기본 계획으로 진행). */
    static <T> T parse(Function<String, T> parser, String text) {
        if (text == null) return null;
        int a = text.indexOf('{'), b = text.lastIndexOf('}');
        if (a < 0 || b <= a) return null;
        try {
            return parser.apply(text.substring(a, b + 1));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static <T> T await(Future<T> f) {
        try {
            return f.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof RuntimeException re ? re : new IllegalStateException(e.getCause());
        }
    }

    // ---------- 세션 이벤트(고정 대사): LLM 없이 즉시 처리 ----------

    public String greet(DialogueState st) {
        String g = c.greeting(st.userName);
        st.pendingFixed.clear();
        st.pendingFixed.add(g);
        return g;
    }

    /** 공부 ↔ 쉬는 시간 전환. 다음 턴 감독·배우가 알 수 있게 재생한 고정 대사를 pendingFixed에 남긴다. */
    public String toggleMode(DialogueState st) {
        long now = clock.getAsLong();
        if (DialogueState.STUDY.equals(st.mode)) {
            if (st.simMinutes == null) st.studyMs += now - st.studyStartMs;
            st.mode = DialogueState.BREAK;
            st.breakStartMs = now;
            st.tension = clamp(st.tension - 10, 0, 100);
        } else {
            st.energyBonus += st.simMinutes == null
                    ? Math.min(
                            15,
                            (int) Math.floor((now - (st.breakStartMs != null ? st.breakStartMs : now)) / 60000.0) * 3
                                    + 5)
                    : 10;
            st.mode = DialogueState.STUDY;
            st.studyStartMs = now;
        }
        String kind = DialogueState.BREAK.equals(st.mode) ? "break" : "resume";
        return playFixed(st, kind);
    }

    /** 웹캠 감지: 자리 비움. 이미 비운 상태면 null. */
    public String away(DialogueState st) {
        if (st.awayMs != null) return null;
        st.awayMs = clock.getAsLong();
        return playFixed(st, "away");
    }

    /** 웹캠 감지: 복귀. 자리 비움 상태가 아니면 null. */
    public String back(DialogueState st) {
        if (st.awayMs == null) return null;
        st.returnedMinutes = Math.max(1, (int) Math.round((clock.getAsLong() - st.awayMs) / 60000.0));
        st.awayMs = null;
        return playFixed(st, "back");
    }

    private String playFixed(DialogueState st, String kind) {
        List<String> lines = c.fixedLines().getOrDefault(kind, List.of());
        if (lines.isEmpty()) return null;
        int i = st.fixedIdx.getOrDefault(kind, 0);
        String line = lines.get(i % lines.size());
        st.fixedIdx.put(kind, i + 1);
        st.pendingFixed.add(line);
        return line;
    }

    public static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
