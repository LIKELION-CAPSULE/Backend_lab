package com.capsule.dialogue.service;

import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.dialogue.client.Decider;
import com.capsule.dialogue.client.Decider.Decision;
import com.capsule.dialogue.client.Llm;
import com.capsule.dialogue.client.LlmGateway;
import com.capsule.dialogue.config.DialogueProperties;
import com.capsule.dialogue.domain.CharacterMemory;
import com.capsule.dialogue.dto.ChatLine;
import com.capsule.dialogue.dto.LlmContext;
import com.capsule.dialogue.dto.LlmPurpose;
import com.capsule.dialogue.repository.CharacterMemoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 캐릭터가 사용자에 대해 아는 사실 (기억 ①, ADR 0022). Mem0처럼 매 턴 결정 모델이 무시 · 추가 · 고치기 · 지우기 중 하나를 고르고,
 * 추가 · 고치기면 LLM이 한 줄로 쓴다. 최대 개수를 넘으면 결정 모델이 가장 덜 중요한 하나를 골라 버린다.
 * 답장을 늦추지 않게 답장을 저장한 뒤에 돈다. 실패하면 이번 턴은 기억하지 않을 뿐이다.
 */
@Component
public class MemoryKeeper {

    private static final Logger log = LoggerFactory.getLogger(MemoryKeeper.class);
    private static final ZoneId DAY_ZONE = ZoneId.of("Asia/Seoul");
    static final String KEEP = "memory";
    static final String DROP = "drop";
    static final String NOOP = "NOOP";
    static final String ADD = "ADD";
    static final int MAX_LENGTH = 80; // DB CHECK. 프롬프트는 40자 이내로 쓰게 한다

    /** 결정 모델 판단과 상관없이 저장하지 않는다: 전화번호 · 주민번호 · 계좌 · 카드처럼 긴 숫자 */
    private static final Pattern PRIVATE = Pattern.compile("(\\d{2,4}-\\d{3,4}-\\d{4}|\\d{6}-?\\d{7}|\\d{10,})");

    static final String WRITE_SYSTEM = """
            너는 캐릭터 {name}가 사용자에 대해 기억할 사실을 한 줄로 적는다. 오늘은 {today}다.
            - <사용자의 말>에 나온 사실만 쓴다. 추측하지 않는다.
            - 40자 이내 한 줄. 주어는 '사용자'. 날짜가 나오면 날짜를 넣는다 ("다음 주"는 날짜로 바꾼다).
            - <고칠 기억>이 있으면 그 내용에 새 내용을 합쳐 다시 쓴다.
            - 한 줄만 출력한다.""";

    private final Decider decider;
    private final LlmGateway llm;
    private final CharacterMemoryRepository memories;
    private final DialogueProperties props;
    private final Clock clock;

    MemoryKeeper(
            Decider decider,
            LlmGateway llm,
            CharacterMemoryRepository memories,
            DialogueProperties props,
            Clock clock) {
        this.decider = decider;
        this.llm = llm;
        this.memories = memories;
        this.props = props;
        this.clock = clock;
    }

    /** 답장 프롬프트에 넣을 사실 (오래된 것부터) */
    public List<String> facts(long userId, long characterId) {
        return memories.findByUserIdAndCharacterIdOrderById(userId, characterId).stream()
                .map(CharacterMemory::content)
                .toList();
    }

    /** 대화 한 턴 뒤 (답장 저장 후) */
    public void afterTurn(
            CharacterInfo character,
            long userId,
            UUID requestId,
            String userMessage,
            List<ChatLine> recent,
            LlmContext ctx) {
        try {
            keep(character, userId, requestId, userMessage, recent, ctx);
        } catch (RuntimeException e) {
            log.warn("기억 정리 실패, 이번 턴은 기억하지 않음: {}", e.toString());
        }
    }

    private void keep(
            CharacterInfo character,
            long userId,
            UUID requestId,
            String userMessage,
            List<ChatLine> recent,
            LlmContext ctx) {
        if (userMessage == null
                || userMessage.isBlank()
                || PRIVATE.matcher(userMessage).find()) return;
        List<CharacterMemory> known = memories.findByUserIdAndCharacterIdOrderById(userId, character.id());
        Decision d = decide(
                character,
                known.stream().map(m -> new Known(m.id(), m.content())).toList(),
                recent,
                userMessage,
                ctx);
        if (d == null || NOOP.equals(d.choice())) return;
        String op = d.choice();
        Optional<CharacterMemory> target =
                known.stream().filter(m -> op.endsWith("_" + m.id())).findFirst();
        if (op.startsWith("DELETE_")) {
            target.ifPresent(memories::delete);
            return;
        }
        String line = write(character, target.map(CharacterMemory::content).orElse(null), recent, userMessage, ctx);
        Instant now = Instant.now(clock);
        if (target.isPresent()) {
            target.get().rewrite(line, requestId, now);
            memories.save(target.get());
            return;
        }
        memories.save(new CharacterMemory(userId, character.id(), line, requestId, now));
        if (known.size() + 1 > props.memoryMax()) drop(character, userId, ctx);
    }

    /** 넘치면 하나 버린다. 결정 모델이 못 고르면 가장 오래된 것 */
    private void drop(CharacterInfo character, long userId, LlmContext ctx) {
        List<CharacterMemory> all = memories.findByUserIdAndCharacterIdOrderById(userId, character.id());
        Map<String, String> criteria = new LinkedHashMap<>();
        all.forEach(m -> criteria.put("m" + m.id(), m.content()));
        Decision d = null;
        try {
            d = decider.decide(
                            "캐릭터: " + character.name() + "\n오늘: " + today(),
                            Map.of(
                                    DROP,
                                    new Decider.Question(
                                            "오늘 기준으로 캐릭터가 계속 기억할 가치가 가장 낮은 사실 하나를 고른다 (지난 일정, 덜 중요한 취향 등)", criteria)),
                            ctx)
                    .get(DROP);
        } catch (RuntimeException e) {
            log.warn("버릴 기억 고르기 실패, 가장 오래된 것을 버림: {}", e.toString());
        }
        String choice = d == null ? null : d.choice();
        CharacterMemory victim = all.stream()
                .filter(m -> ("m" + m.id()).equals(choice))
                .findFirst()
                .orElse(all.getFirst());
        memories.delete(victim);
    }

    private String write(
            CharacterInfo character, String current, List<ChatLine> recent, String userMessage, LlmContext ctx) {
        String system = WRITE_SYSTEM.replace("{name}", character.name()).replace("{today}", today().toString());
        String input = (current == null ? "" : "<고칠 기억>\n" + current + "\n</고칠 기억>\n\n")
                + "<앞 대화>\n" + PromptVars.orNone(PromptVars.transcript(recent, character.name()), "없음")
                + "\n</앞 대화>\n\n<사용자의 말>\n" + userMessage + "\n</사용자의 말>";
        Llm.Result r = llm.complete(
                LlmPurpose.MEMORY,
                List.of(new Llm.SystemBlock(system, true)),
                List.of(new Llm.ChatTurn("user", input)),
                ctx,
                null);
        String line = r.text().strip().replaceAll("^[\"“-]\\s*|[\"”]$", "").strip();
        if (line.isEmpty()) throw new IllegalStateException("빈 기억");
        return line.length() > MAX_LENGTH ? line.substring(0, MAX_LENGTH) : line;
    }

    /** 이미 아는 사실 (id는 결정 보기 이름에 쓰임) */
    record Known(long id, String content) {}

    /**
     * 실험실 미리보기: 저장하지 않고 결정과 쓸 한 줄만 보여 준다 (서비스와 같은 질문 · 같은 프롬프트).
     *
     * @param facts 이미 아는 사실 (실험 조건). id는 1부터 순서대로
     */
    public Map<String, Object> preview(
            CharacterInfo character, List<String> facts, List<ChatLine> recent, String userMessage, LlmContext ctx) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (userMessage == null
                || userMessage.isBlank()
                || PRIVATE.matcher(userMessage).find()) {
            out.put("choice", "SKIPPED (개인정보 숫자 · 빈 말)");
            return out;
        }
        List<Known> known = new java.util.ArrayList<>();
        for (int i = 0; i < facts.size(); i++) known.add(new Known(i + 1, facts.get(i)));
        Decision d = decide(character, known, recent, userMessage, ctx);
        out.put("choice", d == null ? null : d.choice());
        out.put("confidence", d == null ? null : d.confidence());
        if (d == null || NOOP.equals(d.choice()) || d.choice().startsWith("DELETE_")) return out;
        String current = known.stream()
                .filter(k -> d.choice().endsWith("_" + k.id()))
                .map(Known::content)
                .findFirst()
                .orElse(null);
        out.put("line", write(character, current, recent, userMessage, ctx));
        out.put("overflow", current == null && known.size() + 1 > props.memoryMax());
        return out;
    }

    private Decision decide(
            CharacterInfo character, List<Known> known, List<ChatLine> recent, String userMessage, LlmContext ctx) {
        return decider.decide(state(character, known, recent, userMessage), Map.of(KEEP, keepQuestion(known)), ctx)
                .get(KEEP);
    }

    static String state(CharacterInfo character, List<Known> known, List<ChatLine> recent, String userMessage) {
        String facts = known.isEmpty()
                ? "없음"
                : known.stream().map(m -> "#" + m.id() + " " + m.content()).collect(Collectors.joining("\n"));
        return "캐릭터: " + character.name() + "\n"
                + "캐릭터가 이미 아는 사실:\n" + facts + "\n"
                + "앞 대화 (맥락으로만 본다):\n" + PromptVars.orNone(PromptVars.transcript(recent, character.name()), "없음")
                + "\n사용자의 마지막 말: " + userMessage;
    }

    // TODO(judgment): J-13 기억할 가치 기준 (임시: 아래 보기 설명). 실험실 평가로 다듬는다
    static Decider.Question keepQuestion(List<Known> known) {
        Map<String, String> criteria = new LinkedHashMap<>();
        criteria.put(NOOP, "기억할 것이 없다: 인사, 잡담, 맞장구, 공부 문제 내용 자체, 그때뿐인 상태(배고파), 이미 아는 사실 그대로, 사용자 일정에 따로 있는 시험 · 약속 날짜");
        criteria.put(ADD, "새로 기억할 사실이 있다: 목표 · 계획, 이어지는 고민 · 감정, 취향 · 습관, 캐릭터와 한 약속, 자기 이야기(학년, 전공, 불러 달라는 이름)");
        for (Known m : known) {
            criteria.put("UPDATE_" + m.id(), "이미 아는 사실 #" + m.id() + "(" + m.content() + ")에 새 내용을 더하거나 바뀐 내용으로 고친다");
            criteria.put(
                    "DELETE_" + m.id(), "이미 아는 사실 #" + m.id() + "(" + m.content() + ")가 이제 틀렸다 (그만뒀다, 취소했다, 아니라고 했다)");
        }
        return new Decider.Question("사용자의 마지막 말에서 캐릭터가 사용자에 대해 기억할 것을 어떻게 할지 하나 고른다", criteria);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(DAY_ZONE));
    }
}
