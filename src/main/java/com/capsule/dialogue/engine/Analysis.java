package com.capsule.dialogue.engine;

import static com.capsule.dialogue.engine.Rules.*;

import com.capsule.dialogue.engine.Content.Beat;
import com.capsule.dialogue.engine.Content.Character;
import com.capsule.dialogue.engine.Content.LoreCard;
import com.capsule.dialogue.engine.Content.WorldEvent;
import com.capsule.dialogue.engine.DialogueState.Bit;
import com.capsule.dialogue.engine.DialogueState.Loop;
import com.capsule.dialogue.engine.DialogueState.TurnSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 사용자 행동 분석, 대화 기술 리포트, 비트 후보, 세계 사건. 모두 결정적 코드(LLM 없음). */
public final class Analysis {
    private Analysis() {}

    public record Motif(String id, String title, int count) {}

    public record Signals(
            boolean empty,
            boolean lowEffort,
            boolean asksInfo,
            boolean asksQuestion,
            boolean complaint,
            boolean negative,
            boolean rude,
            boolean compliment,
            boolean disclosure,
            boolean repeatWord,
            boolean bait,
            boolean busy,
            Motif motif) {
        /** 켜진 신호 이름(알파벳순). includeBusy=false면 내부용 busy는 뺀다. */
        public List<String> flags(boolean includeBusy) {
            List<String> f = new ArrayList<>();
            if (asksInfo) f.add("asksInfo");
            if (asksQuestion) f.add("asksQuestion");
            if (bait) f.add("bait");
            if (busy && includeBusy) f.add("busy");
            if (complaint) f.add("complaint");
            if (compliment) f.add("compliment");
            if (disclosure) f.add("disclosure");
            if (empty) f.add("empty");
            if (lowEffort) f.add("lowEffort");
            if (negative) f.add("negative");
            if (repeatWord) f.add("repeatWord");
            if (rude) f.add("rude");
            return f;
        }
    }

    /** 사용자 말 분석. 부수효과: engagement, motifs, prefs 갱신(원본 JS와 동일). */
    public static Signals analyzeUser(DialogueState st, String text, Character c) {
        String t = text == null ? "" : text.trim();
        String flat = t.replaceAll("\\s", "");
        boolean empty = t.isEmpty();
        boolean lowEffort = !empty && (flat.length() <= 3 || find(LOW_EFFORT, t));
        boolean asksQuestion = find(ASKS_QUESTION, t);
        boolean complaint = find(COMPLAINT, t);
        Motif motif = null;
        for (LoreCard l : c.lore()) {
            if (l.id().equals("muggle_study")) continue;
            if (l.keys().stream().anyMatch(t::contains)) {
                int n = st.motifs.merge(l.id(), 1, Integer::sum);
                if (n >= 2) motif = new Motif(l.id(), l.title(), n);
            }
        }
        boolean bait = !empty
                && find(BAIT, t)
                && c.lore().stream().anyMatch(l -> l.keys().stream().anyMatch(t::contains));
        double score = empty ? 0.2 : lowEffort ? 0.1 : Math.min(1, flat.length() / 25.0) + (asksQuestion ? 0.15 : 0);
        st.engagement = 0.6 * st.engagement + 0.4 * Math.min(1, score);
        if (find(HMM, t) && (complaint || find(HMM_COMPLAINT, t))) addPref(st, PREF_HMM_TEXT);
        if (find(PREF_NAG, t)) addPref(st, PREF_NAG_TEXT);
        if (find(PREF_FUN, t)) addPref(st, PREF_FUN_TEXT);
        if (find(PREF_NO_ASK, t)) addPref(st, PREF_NO_ASK_TEXT);
        boolean asksInfo = find(ASKS_INFO, t), negative = find(NEGATIVE, t), repeatWord = find(REPEAT_WORD, t);
        boolean busy = bait || asksInfo || complaint || negative || repeatWord || motif != null;
        return new Signals(
                empty,
                lowEffort,
                asksInfo,
                asksQuestion,
                complaint,
                negative,
                find(RUDE, t),
                find(COMPLIMENT, t),
                find(DISCLOSURE, t),
                repeatWord,
                bait,
                busy,
                motif);
    }

    public static void addPref(DialogueState st, String p) {
        if (p == null || p.isBlank() || st.prefs.contains(p)) return;
        st.prefs.add(p);
        if (st.prefs.size() > 6) st.prefs.remove(0);
    }

    public record Craft(
            double qRate,
            String q,
            int rhetStreak,
            int deflects,
            List<String> firsts,
            List<String> lastMoves,
            int hmm,
            boolean nagged,
            boolean father,
            List<Loop> due,
            List<Bit> callable) {}

    /** 최근 대사들로 질문 비율(꼬리 질문만), 반문 연속, 회피, 반복 신호를 계산한다. */
    public static Craft craft(DialogueState st) {
        List<TurnSummary> all = st.turns;
        List<TurnSummary> recent6 = all.subList(Math.max(0, all.size() - 6), all.size());
        long follow = recent6.stream().filter(Analysis::isFollow).count();
        double qRate = recent6.isEmpty() ? 0.35 : (double) follow / recent6.size();
        int rhet = 0;
        for (int i = all.size() - 1; i >= 0 && isRhet(all.get(i)); i--) rhet++;
        List<TurnSummary> recent3 = all.subList(Math.max(0, all.size() - 3), all.size());
        int deflects = (int)
                recent3.stream().filter(t -> find(DEFLECT, t.replyClean())).count();
        String q = "free";
        if (recent6.size() >= 2 && qRate > 0.5) q = "avoid";
        else if (recent6.size() >= 2 && qRate < 0.2) q = "ask";
        List<String> firsts = new ArrayList<>();
        for (TurnSummary t : recent3) {
            String[] parts = t.replyClean().replaceFirst("^[\\s.…]+", "").split("[\\s,.?!…~]", -1);
            if (parts.length > 0 && !parts[0].isEmpty()) firsts.add(parts[0]);
        }
        List<String> lastMoves =
                recent3.stream().map(TurnSummary::move).filter(Objects::nonNull).toList();
        int hmm = (int) recent3.stream().filter(t -> find(HMM, t.replyClean())).count()
                + (int) st.pendingFixed.stream().filter(s -> find(HMM, s)).count();
        boolean nagged =
                !recent3.isEmpty() && find(NAG, recent3.get(recent3.size() - 1).replyClean());
        boolean father = all.stream().anyMatch(t -> t.replyClean().contains("아버지"));
        int n = all.size();
        List<Loop> due =
                st.loops.stream().filter(l -> !l.paid() && n >= l.due()).toList();
        List<Bit> callable =
                st.bits.stream().filter(b -> b.uses() < 3 && n - b.at() >= 2).toList();
        return new Craft(qRate, q, rhet, deflects, firsts, lastMoves, hmm, nagged, father, due, callable);
    }

    static boolean isFollow(TurnSummary t) {
        if ("followup".equals(t.askType())) return hasQ(t.replyClean());
        return t.askType() == null && hasQ(t.replyClean()) && !find(RHETORICAL, t.replyClean());
    }

    static boolean isRhet(TurnSummary t) {
        return "rhetorical".equals(t.askType()) || find(RHETORICAL, t.replyClean());
    }

    /** 지금 쓸 수 있는 드라마 비트(Façade식 전제조건). */
    public static List<Beat> eligibleBeats(DialogueState st, Character c) {
        int n = st.turns.size();
        return c.beats().stream()
                .filter(b -> {
                    if (b.mode() != null && !b.mode().equals(st.mode)) return false;
                    if (st.tension < b.tMin() || st.tension > b.tMax()) return false;
                    if (n < b.minTurn()) return false;
                    if (b.once() && st.beatsOnce.contains(b.id())) return false;
                    Integer last = st.beatLast.get(b.id());
                    return b.cooldown() == null || b.cooldown() == 0 || last == null || n - last >= b.cooldown();
                })
                .toList();
    }

    /** 세계 사건. 사용자가 미끼·질문·불평·힘듦을 표현한 턴엔 사건을 일으키지 않는다. 몰입도가 낮으면 빈도 2배. */
    public static WorldEvent rollEvent(DialogueState st, Character c, double freq, boolean force, Signals sig) {
        if (!force) {
            if (freq <= 0) return null;
            if (sig != null && (sig.bait() || sig.asksInfo() || sig.complaint() || sig.negative())) return null;
            int boost = sig != null && (sig.lowEffort() || st.engagement < 0.35) ? 2 : 1;
            if (st.turns.size() - st.lastEventTurn < (boost > 1 ? 2 : 3)) return null;
            if (st.evRng() > Math.min(0.8, freq * boost)) return null;
        }
        List<WorldEvent> pool =
                c.events().stream().filter(e -> !st.usedEvents.contains(e.id())).toList();
        if (pool.isEmpty()) {
            st.usedEvents.clear();
            pool = c.events();
        }
        WorldEvent ev = pool.get((int) Math.floor(st.evRng() * pool.size()));
        st.usedEvents.add(ev.id());
        st.lastEventTurn = st.turns.size();
        return ev;
    }
}
