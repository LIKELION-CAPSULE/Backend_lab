package com.capsule.dialogue.engine;

import static com.capsule.dialogue.engine.Rules.*;

import com.capsule.dialogue.engine.Content.Character;
import com.capsule.dialogue.engine.DialogueState.TurnSummary;
import com.capsule.dialogue.engine.Planning.SubPlan;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 응답 파싱(표정 태그)과 코드 감점. 품질 모드에선 심사 점수 + 감점으로 후보를 고른다. */
public final class Scoring {
    private Scoring() {}

    public record Tagged(String tag, String text) {}

    public record Penalty(String label, int points) {}

    private static final Pattern TAG = Pattern.compile("^\\s*\\[([^\\]\\n]{1,8})\\]\\s*");
    private static final Pattern TAG_OPEN = Pattern.compile("^\\s*\\[[^\\]\\n]{0,8}$");

    /** "[당황] 뭐?!" → (당황, 뭐?!). 스트리밍 중 "[당"처럼 태그가 덜 왔으면 빈 텍스트. */
    public static Tagged splitTag(String s) {
        if (s == null) return new Tagged(null, "");
        Matcher m = TAG.matcher(s);
        if (m.find()) return new Tagged(m.group(1), s.substring(m.end()));
        if (TAG_OPEN.matcher(s).find()) return new Tagged(null, "");
        return new Tagged(null, s);
    }

    static Set<String> grams(String s) {
        String t = (s == null ? "" : s).replaceAll("[\\s.,!?…~\\[\\]]", "");
        Set<String> g = new HashSet<>();
        for (int i = 0; i + 3 <= t.length(); i++) g.add(t.substring(i, i + 3));
        return g;
    }

    public static double jaccard(String a, String b) {
        Set<String> A = grams(a), B = grams(b);
        if (A.isEmpty() || B.isEmpty()) return 0;
        long inter = A.stream().filter(B::contains).count();
        return (double) inter / (A.size() + B.size() - inter);
    }

    public static double maxOverlap(DialogueState st, String text) {
        List<TurnSummary> ts = st.turns.subList(Math.max(0, st.turns.size() - 6), st.turns.size());
        return ts.stream().mapToDouble(t -> jaccard(text, t.replyClean())).max().orElse(0);
    }

    public static List<Penalty> penalties(DialogueState st, String clean, String tag, SubPlan sub, Character c) {
        List<Penalty> p = new ArrayList<>();
        int limit = DialogueState.STUDY.equals(st.mode) ? 70 : 140;
        if (clean.isEmpty()) p.add(new Penalty("빈 응답", -10));
        if (clean.length() > limit) p.add(new Penalty("길이 " + clean.length() + "자", -1));
        if (tag == null || !c.emotions().contains(tag)) p.add(new Penalty("표정 태그", -1));
        if (clean.startsWith("흥")) p.add(new Penalty("\"흥\" 시작", -1));
        double ov = maxOverlap(st, clean);
        if (ov > 0.3) p.add(new Penalty("반복 " + pct(ov), -2));
        if (sub != null && "followup".equals(sub.askType()) && !hasQ(clean)) p.add(new Penalty("질문 누락", -1));
        if (sub != null && "none".equals(sub.askType()) && hasQ(clean)) p.add(new Penalty("불필요한 질문", -1));
        if (st.prefs.stream().anyMatch(x -> x.contains("흥")) && clean.contains("흥")) p.add(new Penalty("요청 위반: 흥", -2));
        if (st.prefs.stream().anyMatch(x -> x.contains("재촉")) && find(NAG, clean)) p.add(new Penalty("요청 위반: 재촉", -2));
        if (st.prefs.stream().anyMatch(x -> x.contains("다시 묻지")) && clean.contains("기숙사") && hasQ(clean))
            p.add(new Penalty("요청 위반: 재질문", -2));
        if (find(DEFLECT, clean)) p.add(new Penalty("회피", -1));
        return p;
    }

    public static int sum(List<Penalty> ps) {
        return ps.stream().mapToInt(Penalty::points).sum();
    }
}
