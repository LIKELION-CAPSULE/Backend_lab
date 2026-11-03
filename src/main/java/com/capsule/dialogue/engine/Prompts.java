package com.capsule.dialogue.engine;

import static com.capsule.dialogue.engine.Rules.pct;

import com.capsule.dialogue.engine.Analysis.Craft;
import com.capsule.dialogue.engine.Analysis.Signals;
import com.capsule.dialogue.engine.Content.Beat;
import com.capsule.dialogue.engine.Content.Character;
import com.capsule.dialogue.engine.Content.LoreCard;
import com.capsule.dialogue.engine.Content.Move;
import com.capsule.dialogue.engine.Content.WorldEvent;
import com.capsule.dialogue.engine.DialogueState.TurnSummary;
import com.capsule.dialogue.engine.Planning.Plan;
import com.capsule.dialogue.engine.Planning.SubPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** 감독 입력, 코드 지시, 배우 장면 카드, 심사 입력, 대화록. 문구는 v5 콘솔과 같다(바꾸면 프롬프트 버전을 올릴 것). */
public final class Prompts {
    private Prompts() {}

    public static String transcript(DialogueState st, Character c, int n) {
        List<String> lines = new ArrayList<>();
        List<TurnSummary> ts = st.turns.subList(Math.max(0, st.turns.size() - n), st.turns.size());
        for (TurnSummary t : ts) {
            if (t.eventId() != null)
                c.events().stream()
                        .filter(e -> e.id().equals(t.eventId()))
                        .findFirst()
                        .ifPresent(e -> lines.add("(사건: " + e.text() + ")"));
            lines.add("사용자: " + (t.userText() == null || t.userText().isEmpty() ? "(말 없음)" : t.userText()));
            lines.add(c.speaker() + ": " + t.replyClean());
        }
        return lines.isEmpty() ? "(아직 대화 없음)" : String.join("\n", lines);
    }

    public static List<String> directives(
            DialogueState st, Craft craft, List<Beat> beats, Beat featured, Signals sig, int nPlans, Character c) {
        List<String> d = new ArrayList<>();
        d.add("계획 개수: " + nPlans);
        List<String> u = new ArrayList<>();
        String sp = c.speaker();
        if (!st.prefs.isEmpty()) u.add("사용자 요청(세션 내내 지킬 것): " + String.join(" / ", st.prefs));
        if (sig.complaint()) u.add(sp + "의 말투·행동을 불평함 → 첫 계획은 캐릭터답게 받아들이고 바로 태도를 바꾸는 수. 변명·잔소리 반복 금지.");
        if (sig.bait()) u.add("세계관 미끼(장난·주장)를 던짐 → 받고 더하기. 일단 걸려들거나 올라타기. '안 속아'류 회피 금지.");
        if (sig.motif() != null)
            u.add("'" + sig.motif().title() + "' 소재를 " + sig.motif().count() + "번째 꺼냄 → 같은 반응 금지, 반응 키우기"
                    + (sig.motif().count() >= 3 ? "(세 번째: 크게 터뜨리거나 역으로 장난, escalate 권장)" : "") + ".");
        if (sig.repeatWord()) u.add("같은 말을 연달아 반복함 → 판 키우기(escalate).");
        if (sig.asksInfo()) u.add("지식을 물음 → 먼저 정확한 기초를 한두 줄로 답하기(머글 연구면 secret_study). '몰라' 금지. 문제 답 전체는 금지.");
        long eng = Math.round(st.engagement * 100);
        if (sig.lowEffort())
            u.add("답이 짧음(몰입도 " + eng + ") → 질문으로 몰아붙이지 말고 " + sp
                    + "가 판을 깔기(gossip, challenge, borrow, self_disclose, melodrama, 사건 반응). 짧게.");
        else if (st.engagement < 0.35) u.add("최근 몰입도 낮음(" + eng + ") → " + sp + "가 흥미로운 화제를 먼저 던지기.");
        if (sig.negative()) u.add("지치거나 힘들다는 신호 → 긴장 낮추기, 비꼼 줄이기. 심각하면 sincere.");
        if (sig.rude()) u.add("거칠게 말함 → 정색·훈계 말고 한 수 위에서 비꼬아 받기(compare_up, melodrama).");
        if (sig.compliment()) u.add("칭찬함 → flustered(설렘 없이).");
        if (sig.disclosure() && !sig.lowEffort())
            u.add("자기 일을 말함 → 그 구체적인 부분을 붙잡기(꼬리 질문이나 비교로 올라서기). 속마음이면 작은 자기 고백으로 균형.");
        if (st.returnedMinutes != null) u.add(st.returnedMinutes + "분쯤 자리를 비웠다가 방금 돌아옴 → 그 사실을 소재로.");
        if (!u.isEmpty()) d.add("사용자 행동(최우선): " + String.join(" / ", u));
        if ("avoid".equals(craft.q()) || sig.lowEffort())
            d.add("최근 꼬리 질문 비율 " + pct(craft.qRate()) + " → 이번엔 묻지 않기(ask_type=none).");
        else if ("ask".equals(craft.q()) && !sig.busy())
            d.add("최근 꼬리 질문 비율 " + pct(craft.qRate()) + "로 낮음 → 첫 계획은 꼬리 질문(ask_type=followup).");
        if (craft.rhetStreak() >= 1) d.add("직전 답이 반문이었음 → 이번엔 반문 금지.");
        if (craft.deflects() >= 1) d.add("최근 회피·부정 반응 " + craft.deflects() + "회 → 이번엔 회피 금지.");
        if (!craft.due().isEmpty())
            d.add("회수할 떡밥: "
                    + craft.due().stream().map(l -> "\"" + l.text() + "\"").collect(Collectors.joining(", "))
                    + " → 사용자 말과 충돌하지 않으면 pay_loop.");
        if (!craft.callable().isEmpty())
            d.add("콜백 가능한 농담거리: "
                    + craft.callable().stream()
                            .map(b ->
                                    "\"" + b.text() + "\"(" + b.uses() + "회" + (b.uses() == 2 ? ", 다음엔 비틀기" : "") + ")")
                            .collect(Collectors.joining(", ")));
        if (!craft.lastMoves().isEmpty())
            d.add("최근 수: " + String.join(", ", craft.lastMoves()) + " → 직전과 같은 수를 첫 계획으로 쓰지 않기.");
        if (!beats.isEmpty() && !sig.busy()) {
            d.add("쓸 수 있는 비트: "
                    + beats.stream()
                            .map(b -> b.id() + "(" + b.name() + ": " + b.text() + ")")
                            .collect(Collectors.joining(" / ")));
            if (featured != null) d.add("특히 지금 '" + featured.name() + "' 비트를 권함.");
        } else if (!beats.isEmpty()) d.add("지금은 사용자 말에 집중할 때라 비트는 쓰지 않기.");
        int tgt = st.tensionTarget();
        d.add("긴장도 " + st.tension + " / 목표 " + tgt + " → "
                + (st.tension < tgt - 10 ? "조금 올리는 쪽" : st.tension > tgt + 10 ? "내리는 쪽" : "유지") + ". 거만함 " + st.status
                + (st.status > 85 ? " (높음: 무너뜨리는 수 고려)" : st.status < 40 ? " (낮음: 다시 세우기)" : "") + ".");
        return d;
    }

    public static String directorInput(
            DialogueState st, String userText, WorldEvent event, List<String> directives, Character c, long nowMs) {
        String sp = c.speaker();
        List<String> L = new ArrayList<>();
        L.add("<세계 상태>");
        L.add("모드: " + st.mode + " / 오늘 공부 시간: " + st.studyMinutes(nowMs) + "분 / 시각: 밤");
        L.add(sp + " 기분: " + st.mood + " / 체력: " + st.energy(nowMs) + "% / 에세이 진척: " + st.essayPct(nowMs) + "%");
        L.add(event != null ? "방금 일어난 사건: " + event.text() + " (어울리는 반응: " + event.hook() + ")" : "방금 일어난 사건: 없음");
        if (!st.pendingFixed.isEmpty()) L.add("앱이 재생한 " + sp + " 고정 대사: " + quoteJoin(st.pendingFixed));
        L.add("</세계 상태>");
        L.add("<세계 지식 카드 id> "
                + c.lore().stream().map(l -> l.id() + "(" + l.title() + ")").collect(Collectors.joining(", "))
                + " </세계 지식 카드 id>");
        L.add("<사용자에 대해 안 것> 이름: " + (st.userName.isEmpty() ? "모름" : st.userName)
                + (st.userFacts.isEmpty() ? "" : " / " + String.join(" / ", st.userFacts)) + " </사용자에 대해 안 것>");
        L.add("<최근 대화>\n" + transcript(st, c, 6) + "\n</최근 대화>");
        L.add("<코드 지시>\n- " + String.join("\n- ", directives) + "\n</코드 지시>");
        L.add(
                userText != null && !userText.isEmpty()
                        ? "사용자의 새 말: \"" + userText + "\""
                        : "사용자의 새 말: (없음. 사용자는 조용히 공부 중이다. 방금 일어난 일에 " + sp + "가 먼저 한마디 한다.)");
        return String.join("\n", L);
    }

    public static String sceneCard(
            DialogueState st,
            String userText,
            Plan plan,
            SubPlan sub,
            List<LoreCard> lore,
            WorldEvent event,
            Craft craft,
            Signals sig,
            Character c,
            long nowMs) {
        Move mv = c.move(sub.move());
        Beat beat = sub.beatId() == null || sub.beatId().isEmpty()
                ? null
                : c.beat(sub.beatId()).orElse(null);
        String sp = c.speaker();
        String tensionWord = st.tension >= 65 ? "높음(신경이 곤두서 있음)" : st.tension >= 35 ? "중간" : "낮음(느슨함)";
        String statusWord = st.status >= 80 ? "아주 거만함" : st.status >= 55 ? "거만함" : "자세가 무너져 있음";
        String exs = mv.examples().stream()
                .limit(2)
                .map(e -> "사용자: " + e.user() + "\n" + sp + ": " + e.reply())
                .collect(Collectors.joining("\n\n"));
        List<String> L = new ArrayList<>();
        L.add("<장면>");
        L.add("시각: 밤 / 모드: " + st.mode + " / 오늘 공부 시간: " + st.studyMinutes(nowMs) + "분");
        if (event != null) L.add("방금 일어난 일: " + event.text());
        if (!st.pendingFixed.isEmpty()) L.add("앱이 너 대신 방금 재생한 네 대사: " + quoteJoin(st.pendingFixed));
        L.add("</장면>");
        L.add("<" + sp + " 상태>");
        L.add("기분: " + plan.mood() + " / 체력: " + st.energy(nowMs) + "% / 에세이 진척: " + st.essayPct(nowMs) + "% / 긴장: "
                + tensionWord + " / 자세: " + statusWord);
        L.add("사용자 이름: " + (st.userName.isEmpty() ? "모름" : st.userName)
                + (st.userFacts.isEmpty() ? "" : " / 사용자에 대해 안 것: " + String.join(" / ", st.userFacts)));
        L.add("</" + sp + " 상태>");
        if (!lore.isEmpty())
            L.add("<세계 지식>\n" + lore.stream().map(l -> "- " + l.text()).collect(Collectors.joining("\n"))
                    + "\n</세계 지식>");
        L.add("<이번 턴 연출>");
        L.add("수: " + mv.name() + " — " + mv.how());
        if (!sub.scene().isEmpty()) L.add("장면: " + sub.scene());
        if (beat != null) L.add("비트: " + beat.text());
        L.add(
                switch (sub.askType()) {
                    case "followup" ->
                        "질문: "
                                + (plan.followupTarget().isEmpty()
                                        ? ""
                                        : "사용자 말 중 '" + plan.followupTarget() + "'을 붙잡아 ")
                                + "답을 기다리는 꼬리 질문으로 끝낸다.";
                    case "rhetorical" -> "질문: 반문은 하나까지만. 회피용 반문은 금지.";
                    default -> "질문: 이번엔 묻지 않는다. 물음표 없이 끝낸다.";
                });
        if (!st.prefs.isEmpty()) L.add("사용자 요청(지킬 것): " + String.join(" / ", st.prefs));
        if (sig != null && sig.lowEffort()) L.add("사용자가 짧게만 답하고 있다: 네가 화제를 던져 판을 깐다. 몰아붙이지 않는다.");
        if (st.returnedMinutes != null) L.add("사용자가 " + st.returnedMinutes + "분쯤 자리를 비웠다가 방금 돌아왔다.");
        if (!plan.callback().isEmpty() && sub.move().equals("callback")) L.add("콜백: \"" + plan.callback() + "\"");
        if (!plan.payLoop().isEmpty() && sub.move().equals("pay_loop"))
            L.add("떡밥 회수: \"" + plan.payLoop() + "\"을 이번에 풀어 준다.");
        if (!plan.openLoop().isEmpty() && sub == plan.plans().get(0))
            L.add("떡밥 던지기: \"" + plan.openLoop() + "\"을 살짝 예고만 하고 지금은 풀지 않는다.");
        if (!plan.safety().equals("none")) L.add("안전 주의: " + plan.safety() + " (절대 규칙 우선)");
        List<String> rep = new ArrayList<>();
        if (!craft.firsts().isEmpty()) rep.add("첫마디 " + quoteJoinComma(craft.firsts()) + " 피하기");
        if (craft.hmm() > 0) rep.add("\"흥\" 쓰지 않기");
        if (craft.nagged()) rep.add("공부 재촉 금지");
        if (craft.father()) rep.add("\"아버지\" 금지");
        L.add("반복 금지: " + (rep.isEmpty() ? "없음" : String.join(" / ", rep)));
        L.add("</이번 턴 연출>");
        L.add("<이 수의 예시 (톤 참고용, 베끼지 않기)>\n" + exs + "\n</이 수의 예시>");
        L.add(userText != null && !userText.isEmpty() ? "사용자: " + userText : "사용자: (말 없음. 방금 일어난 일에 네가 먼저 한마디 한다.)");
        return String.join("\n", L);
    }

    public static String judgeInput(DialogueState st, String userText, List<String> candidateLines, Character c) {
        return String.join(
                "\n",
                "<최근 대화>\n" + transcript(st, c, 3) + "\n</최근 대화>",
                "모드: " + st.mode,
                userText != null && !userText.isEmpty()
                        ? "사용자의 새 말: \"" + userText + "\""
                        : "사용자의 새 말: (없음. " + c.speaker() + "가 먼저 말을 거는 상황)",
                String.join("\n", candidateLines));
    }

    static String quoteJoin(List<String> xs) {
        return xs.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(" / "));
    }

    static String quoteJoinComma(List<String> xs) {
        return xs.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(", "));
    }
}
