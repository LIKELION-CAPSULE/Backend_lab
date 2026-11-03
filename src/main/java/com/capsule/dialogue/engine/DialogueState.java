package com.capsule.dialogue.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 한 대화 세션의 전체 상태. Jackson으로 그대로 JSON(B) 저장/복원한다(공개 필드 사용).
 * 엔진은 이 객체를 제자리에서 바꾼다. 서비스는 턴이 성공했을 때만 저장한다.
 */
public class DialogueState {
    public static final String STUDY = "공부 중";
    public static final String BREAK = "쉬는 시간";

    public String characterId = "malfoy";
    public String userName = "";
    public String mode = STUDY;
    public long studyMs = 0;
    public long studyStartMs = 0;
    public Long breakStartMs = null;
    /** 평가·테스트용: 값이 있으면 실제 시계 대신 이 분 값을 쓴다. */
    public Integer simMinutes = null;

    public String mood = "심드렁";
    public int energyBonus = 0;
    public int tension = 20;
    public int status = 75;
    public double engagement = 0.6;

    public List<Bit> bits = new ArrayList<>();
    public List<Loop> loops = new ArrayList<>();
    public List<String> userFacts = new ArrayList<>();
    public List<String> prefs = new ArrayList<>();
    public Map<String, Integer> motifs = new HashMap<>();
    public List<String> usedEvents = new ArrayList<>();
    public int lastEventTurn = 0;
    public Map<String, Integer> beatLast = new HashMap<>();
    public List<String> beatsOnce = new ArrayList<>();

    public Long awayMs = null;
    public Integer returnedMinutes = null;

    public List<String> pendingFixed = new ArrayList<>();
    public Map<String, Integer> fixedIdx = new HashMap<>(Map.of("break", 0, "resume", 0));

    /** 일반 난수(비트 추천, 떡밥 회수 시점)와 사건 난수를 분리해 고정 대본 평가를 재현 가능하게 한다. */
    public int rngState = (int) System.nanoTime();

    public int evRngState = (int) (System.nanoTime() >>> 7);

    public List<HistoryMessage> history = new ArrayList<>();
    public List<TurnSummary> turns = new ArrayList<>();

    public record Bit(String text, int uses, int at) {}

    public record Loop(String text, int opened, int due, boolean paid) {}

    public record HistoryMessage(String role, String text) {}
    /** 대화 기술 분석에 필요한 최소 정보. 비용·지연 같은 지표는 DB 테이블에 따로 저장한다. */
    public record TurnSummary(
            int n,
            String userText,
            String replyClean,
            String emotion,
            String move,
            String askType,
            String eventId,
            List<String> signals,
            List<String> penalties) {}

    public double rng() {
        int[] s = {rngState};
        double v = Rng.next(s);
        rngState = s[0];
        return v;
    }

    public double evRng() {
        int[] s = {evRngState};
        double v = Rng.next(s);
        evRngState = s[0];
        return v;
    }

    public static DialogueState start(String characterId, String userName, long nowMs) {
        DialogueState st = new DialogueState();
        st.characterId = characterId;
        st.userName = userName == null ? "" : userName.trim();
        st.studyStartMs = nowMs;
        return st;
    }

    public int studyMinutes(long nowMs) {
        if (simMinutes != null) return simMinutes;
        long live = STUDY.equals(mode) ? nowMs - studyStartMs : 0;
        return (int) Math.floor((studyMs + live) / 60000.0);
    }

    public int essayPct(long nowMs) {
        return Math.min(
                100, 40 + (int) Math.round(studyMinutes(nowMs) * 0.7) + (usedEvents.contains("progress") ? 8 : 0));
    }

    public int energy(long nowMs) {
        return Engine.clamp(90 - (int) Math.round(studyMinutes(nowMs) * 0.6) + energyBonus, 15, 100);
    }

    public int tensionTarget() {
        if (!STUDY.equals(mode)) return 30;
        return Engine.clamp(25 + turns.size() * 5, 25, 75);
    }
}
