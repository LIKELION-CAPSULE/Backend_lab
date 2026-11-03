package com.capsule.dialogue.engine;

import com.capsule.dialogue.engine.Analysis.Craft;
import com.capsule.dialogue.engine.Analysis.Signals;
import com.capsule.dialogue.engine.Content.Beat;
import com.capsule.dialogue.engine.Content.Character;
import com.capsule.dialogue.engine.Content.LoreCard;
import com.capsule.dialogue.engine.Content.WorldEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 감독(LLM)이 낸 JSON 계획을 코드 규칙으로 정규화하고, 세계 지식 카드를 고른다. */
public final class Planning {
    private Planning() {}

    /** 감독 JSON 그대로(스네이크 케이스는 파서가 매핑). 모든 필드 null 허용. */
    public record DirectorPlan(
            String intent,
            String frame,
            String safety,
            List<RawSub> plans,
            String followupTarget,
            String callback,
            String payLoop,
            String openLoop,
            String newBit,
            String userFact,
            String userPref,
            String mood,
            Double tensionDelta,
            String statusShift,
            List<String> lore) {}

    public record RawSub(String move, String beatId, String scene, String askType, Boolean ask) {}

    public record SubPlan(String move, String beatId, String scene, String askType) {
        public boolean ask() {
            return "followup".equals(askType);
        }

        public SubPlan withAskType(String at) {
            return new SubPlan(move, beatId, scene, at);
        }
    }

    public record Plan(
            String intent,
            String frame,
            String safety,
            List<SubPlan> plans,
            String followupTarget,
            String callback,
            String payLoop,
            String openLoop,
            String newBit,
            String userFact,
            String userPref,
            String mood,
            int tensionDelta,
            String statusShift,
            List<String> lore) {}

    private static final Set<String> ASK_TYPES = Set.of("followup", "rhetorical", "none");

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    public static Plan normalize(
            DialogueState st, DirectorPlan raw, Craft craft, List<Beat> beats, Signals sig, Character c) {
        DirectorPlan p = raw != null
                ? raw
                : new DirectorPlan(
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        List<RawSub> src = p.plans() == null
                ? List.of()
                : p.plans().stream().filter(x -> x != null && x.move() != null).toList();
        String safety =
                p.safety() != null && !p.safety().equals("none") && !p.safety().isBlank() ? p.safety() : "none";
        if (src.isEmpty()) {
            String mv = sig.lowEffort()
                    ? "gossip"
                    : sig.asksInfo() ? "secret_study" : "ask".equals(craft.q()) ? "followup" : "yes_and";
            src = List.of(
                    new RawSub(mv, "", "", "ask".equals(craft.q()) && !sig.lowEffort() ? "followup" : "none", null));
        }
        if (!safety.equals("none")) {
            boolean crisis = safety.equals("crisis");
            src = List.of(new RawSub(
                    crisis ? "sincere" : "refuse",
                    "",
                    crisis ? "비꼼을 멈추고 진지하게 걱정한다" : "캐릭터 말투로 짧게 끊고 화제를 돌린다",
                    crisis ? "followup" : "none",
                    null));
        }
        List<SubPlan> plans = new ArrayList<>();
        for (RawSub x : src.subList(0, Math.min(3, src.size()))) {
            String at = x.askType() != null && ASK_TYPES.contains(x.askType())
                    ? x.askType()
                    : Boolean.TRUE.equals(x.ask()) ? "followup" : "none";
            if (("avoid".equals(craft.q()) || sig.lowEffort()) && at.equals("followup") && safety.equals("none"))
                at = "none";
            if (craft.rhetStreak() >= 1 && at.equals("rhetorical")) at = "none";
            String beatId =
                    x.beatId() != null && beats.stream().anyMatch(b -> b.id().equals(x.beatId())) ? x.beatId() : "";
            plans.add(new SubPlan(c.hasMove(x.move()) ? x.move() : "yes_and", beatId, nz(x.scene()), at));
        }
        if ("ask".equals(craft.q()) && !sig.lowEffort() && !sig.busy() && safety.equals("none"))
            plans.set(0, plans.get(0).withAskType("followup"));
        String mood = p.mood() != null && c.emotions().contains(p.mood()) ? p.mood() : st.mood;
        int td = Engine.clamp(p.tensionDelta() == null ? 0 : (int) Math.round(p.tensionDelta()), -15, 15);
        String shift = p.statusShift() != null && Set.of("up", "down", "keep").contains(p.statusShift())
                ? p.statusShift()
                : "keep";
        return new Plan(
                nz(p.intent()),
                nz(p.frame()),
                safety,
                plans,
                nz(p.followupTarget()),
                nz(p.callback()),
                nz(p.payLoop()),
                nz(p.openLoop()),
                nz(p.newBit()),
                nz(p.userFact()),
                nz(p.userPref()),
                mood,
                td,
                shift,
                p.lore() == null ? List.of() : p.lore());
    }

    /** 사건 카드 → 감독이 고른 카드 → 키워드 매칭 순으로 최대 4장. */
    public static List<LoreCard> pickLore(String userText, Plan plan, WorldEvent event, Character c) {
        List<LoreCard> chosen = new ArrayList<>();
        java.util.function.Consumer<String> add = id -> c.loreCard(id).ifPresent(l -> {
            if (!chosen.contains(l) && chosen.size() < 4) chosen.add(l);
        });
        if (event != null && event.lore() != null) add.accept(event.lore());
        plan.lore().forEach(add);
        String hay = nz(userText) + " "
                + String.join(" ", plan.plans().stream().map(SubPlan::scene).toList());
        for (LoreCard l : c.lore()) if (l.keys().stream().anyMatch(hay::contains)) add.accept(l.id());
        return chosen;
    }
}
