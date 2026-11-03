package com.capsule.dialogue.engine;

import com.capsule.dialogue.engine.Content.Beat;
import com.capsule.dialogue.engine.Content.Character;
import com.capsule.dialogue.engine.Content.WorldEvent;
import com.capsule.dialogue.engine.DialogueState.Bit;
import com.capsule.dialogue.engine.DialogueState.Loop;
import com.capsule.dialogue.engine.Planning.Plan;
import com.capsule.dialogue.engine.Planning.SubPlan;

/** 한 턴이 끝난 뒤 상태 갱신: 긴장도, 거만함, 기분, 비트 기록, 농담거리, 떡밥, 사용자 사실·요청. */
public final class Effects {
    private Effects() {}

    public static void apply(DialogueState st, Plan plan, SubPlan sub, WorldEvent event, String replyTag, Character c) {
        int n = st.turns.size();
        Beat beat = sub.beatId().isEmpty() ? null : c.beat(sub.beatId()).orElse(null);
        st.tension = Engine.clamp(
                st.tension + (event != null ? event.dt() : 0) + (beat != null ? beat.dt() : 0) + plan.tensionDelta(),
                0,
                100);
        int shift = switch (plan.statusShift()) {
            case "up" -> 8;
            case "down" -> -15;
            default -> 2;
        };
        boolean crack = sub.move().equals("status_crack") || sub.move().equals("flustered");
        st.status = Engine.clamp(st.status + shift + (crack ? -10 : 0), 20, 95);
        st.mood = replyTag != null && c.emotions().contains(replyTag) ? replyTag : plan.mood();
        if (beat != null) {
            st.beatLast.put(beat.id(), n);
            if (beat.once()) st.beatsOnce.add(beat.id());
        }

        String nb = plan.newBit().trim();
        if (!nb.isEmpty() && st.bits.stream().noneMatch(b -> b.text().equals(nb))) {
            st.bits.add(new Bit(nb, 1, n));
            if (st.bits.size() > 6) st.bits.remove(0);
        }
        if (sub.move().equals("callback") && !plan.callback().isEmpty()) {
            for (int i = 0; i < st.bits.size(); i++) {
                Bit b = st.bits.get(i);
                if (plan.callback().contains(b.text()) || b.text().contains(plan.callback())) {
                    st.bits.set(i, new Bit(b.text(), b.uses() + 1, n));
                    break;
                }
            }
        }
        if (sub.move().equals("pay_loop") && !plan.payLoop().isEmpty()) {
            for (int i = 0; i < st.loops.size(); i++) {
                Loop l = st.loops.get(i);
                if (!l.paid() && (plan.payLoop().contains(l.text()) || l.text().contains(plan.payLoop()))) {
                    st.loops.set(i, new Loop(l.text(), l.opened(), l.due(), true));
                    break;
                }
            }
        }
        String ol = plan.openLoop().trim();
        if (!ol.isEmpty()
                && sub == plan.plans().get(0)
                && st.loops.stream().noneMatch(l -> l.text().equals(ol)))
            st.loops.add(new Loop(ol, n, n + 2 + (int) Math.floor(st.rng() * 3), false));
        String uf = plan.userFact().trim();
        if (!uf.isEmpty() && !st.userFacts.contains(uf)) {
            st.userFacts.add(uf);
            if (st.userFacts.size() > 8) st.userFacts.remove(0);
        }
        String up = plan.userPref().trim();
        if (!up.isEmpty()) Analysis.addPref(st, up.length() > 40 ? up.substring(0, 40) : up);
        st.returnedMinutes = null;
    }
}
