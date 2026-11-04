package com.capsule.dialogue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.dialogue.client.LlmJson;
import com.capsule.dialogue.config.CharacterContentSeeder;
import com.capsule.dialogue.domain.ChatMessage;
import com.capsule.dialogue.dto.CharacterContent;
import com.capsule.dialogue.dto.ChatInput;
import com.capsule.dialogue.dto.ChatLine;
import com.capsule.dialogue.dto.EngineOptions;
import com.capsule.dialogue.engine.Llm;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** 고정 블록에 지금 관계 단계의 것만, 상황 카드에 이번 턴의 사실만 들어가는지 (DB 없이, 말포이 처음 값으로) */
class SceneChatPipelineTest {

    static final CharacterContent MALFOY = CharacterContentSeeder.load("malfoy", 1);
    static final Function<String, String> SITUATION = s -> "상황 " + s;

    static ChatInput input(String message, List<ChatLine> history, List<String> facts) {
        return input(message, history, facts, false, false);
    }

    static ChatInput input(
            String message, List<ChatLine> history, List<String> facts, boolean onBreak, boolean chatty) {
        CharacterInfo info = new CharacterInfo(1, 1, "말포이", null, List.of(), "", true, null);
        return new ChatInput(
                info,
                "민수",
                2,
                "11/03 55분, 3단원 하다 일찍 끝냄",
                facts,
                List.of("시험: 중간고사 (2026-11-20)"),
                history,
                message,
                null,
                Map.of(),
                EngineOptions.DEFAULT,
                onBreak,
                chatty,
                52);
    }

    static String card(ChatInput in, String fact) {
        return SceneChatPipeline.card(MALFOY, in, fact, "2026-11-05 (목) 21:00", "엄마가 과자 상자를 보냈다");
    }

    @Test
    void fixedBlockCarriesOnlyThisStagesVoice() {
        Map<String, String> one = SceneChatPipeline.systemVars(MALFOY, 1, "민수", SITUATION);
        Map<String, String> four = SceneChatPipeline.systemVars(MALFOY, 4, "민수", SITUATION);
        assertThat(one.get("relation")).contains("단계 1").contains("이름은 부르지 않는다");
        assertThat(four.get("relation")).contains("단계 4 · 특별한 관계").contains("질투도 숨기지 않는다");
        // 말투 샘플은 그 단계 묶음만, {name}은 닉네임으로
        assertThat(one.get("samples")).contains("…또 왔냐. 조용히 앉아.").doesNotContain("기다렸어");
        assertThat(four.get("samples")).contains("민수, 왔구나. 기다렸어.").doesNotContain("조용히 앉아");
        // 상태 변화 예시: 단계 1은 PM 대사, 단계 4는 단계 4 대사. 순간 동작은 넣지 않는다
        assertThat(one.get("events"))
                .contains("(방금 일어난 일: 상황 AWAY) → 야 어디가?!")
                .contains("(방금 일어난 일: 상황 AWAY) → …말도 없이 가네."); // 상황마다 예시 두 줄
        assertThat(four.get("events")).contains("(방금 일어난 일: 상황 AWAY) → 어디 가? 빨리 와, 너 없으면 재미없단 말이야.");
        assertThat(four.get("events")).doesNotContain("DROWSY").doesNotContain("졸려");
    }

    @Test
    void cardCarriesOnlyThisTurnsFacts() {
        String card = card(input("오늘 뭐 해", List.of(), List.of("사용자는 토익 800 목표", "사용자는 밤에 공부가 잘 됨")), "사용자는 토익 800 목표");
        // 아는 사실은 결정 모델이 고른 하나만, 고르지 않은 사실은 안 들어감. 세션 카드 · 첫 대화 일정은 들어감
        assertThat(card).contains("<꺼낼 수 있는 기억> 사용자는 토익 800 목표").doesNotContain("밤에 공부가 잘 됨");
        assertThat(card).contains("11/03 55분, 3단원 하다 일찍 끝냄").contains("시험: 중간고사");
        assertThat(card).contains("\"흥\"로 말을 시작하지 않기"); // 말버릇은 데이터
        assertThat(card).contains("공부한 지: 52분").contains("말포이 오늘: 엄마가 과자 상자를 보냈다");
        // 매 턴 기술 · 세계 지식 카드 · 무작위 사건은 없다 (결정 19)
        assertThat(card).doesNotContain("대화 기술").doesNotContain("세계 지식").doesNotContain("방금 일어난 일");
        // 길이 · 구성(지금) 줄이 맨 끝
        assertThat(card.lines().toList())
                .element(card.lines().toList().size() - 2)
                .asString()
                .startsWith("지금: 지금은 공부 중이다. 1~2문장");
    }

    @Test
    void breakAndChattyChangeTheLastLines() {
        String rest = card(input("오늘 뭐 해", List.of(), List.of(), true, true), null);
        String chatty = card(input("ㅋㅋ 또 뭐 해", List.of(), List.of(), false, true), null);
        assertThat(rest).contains("지금: 지금은 쉬는 시간이다. 2~4문장").doesNotContain(SceneChatPipeline.CHATTY); // 쉬는 시간엔 수다가 정상
        assertThat(chatty).contains(SceneChatPipeline.CHATTY);
    }

    @Test
    void scheduleOnlyOnFirstTurnAndOncePerSessionHabit() {
        List<ChatLine> history =
                List.of(new ChatLine(ChatMessage.USER, "안녕"), new ChatLine(ChatMessage.CHARACTER, "아버지가 그러셨지. 공부나 해."));
        String card = card(input("오늘 뭐 해", history, List.of()), null);
        assertThat(card).doesNotContain("중간고사").doesNotContain("꺼낼 수 있는 기억");
        assertThat(card).doesNotContain("말포이 오늘"); // 근황은 첫 대화와 쉬는 시간에만
        assertThat(card(input("오늘 뭐 해", history, List.of(), true, false), null)).contains("말포이 오늘");
        assertThat(card).contains("\"아버지\" 쓰지 않기 (이번 세션에 이미 씀)");
    }

    @Test
    void reactionCardIsFactsAndFormatOnly() {
        String card = ReactionWriter.card(MALFOY, "민수", null, List.of(), null);
        assertThat(card).contains("사용자: 민수").contains("한 문장");
        assertThat(card).doesNotContain("<지난 세션 흐름>").doesNotContain("오늘:"); // 없는 재료는 칸도 없음
    }

    @Test
    void offListEmotionMapsToTheNearestExpression() {
        // 최종 검토에서 모델이 쓴 '걱정' · '질투'는 표정이 비었다 → 가까운 표정으로, 모르는 말은 null
        assertThat(MALFOY.emotion("삐짐")).isEqualTo("삐짐");
        assertThat(MALFOY.emotion(" 걱정 ")).isEqualTo("집중");
        assertThat(MALFOY.emotion("질투")).isEqualTo("삐짐");
        assertThat(MALFOY.emotion("SAD")).isNull();
        assertThat(MALFOY.emotion(null)).isNull();
        // 대체 표의 목록 안 키(에셋 없는 표정의 대체)는 감정 이름을 바꾸지 않는다: 목록 안 이름이 먼저
        CharacterContent c = MALFOY;
        CharacterContent assetFallback = new CharacterContent(
                c.characterId(),
                c.code(),
                c.speaker(),
                c.bible(),
                c.rules(),
                c.safety(),
                c.expressions(),
                Map.of("화남", "경멸", "분노", "화남"),
                c.stageTones(),
                c.studyStyle(),
                c.breakStyle(),
                c.world(),
                c.moves(),
                c.lines(),
                c.samples(),
                c.scores(),
                c.habits());
        assertThat(assetFallback.emotion("화남")).isEqualTo("화남");
        assertThat(assetFallback.emotion("분노")).isEqualTo("화남");
    }

    @Test
    void brokenReplyJsonGoesToTheFallbackLine() {
        // 맨 글은 그대로 답장, 온전한 JSON은 꺼낸다
        assertThat(SceneChatPipeline.replyJson(result("흥, 그 정도는 알아.", "end_turn")))
                .isNull();
        assertThat(LlmJson.str(SceneChatPipeline.replyJson(result("{\"reply\":\"흥.\",\"emotion\":\"경멸\"}", "end_turn"))
                        .path("reply")))
                .isEqualTo("흥.");
        // 앞이 잘린 JSON · 토큰 한도로 잘린 답은 깨진 것 → 부르는 쪽이 5초 대체 대사로
        assertThatThrownBy(() -> SceneChatPipeline.replyJson(result("…공부나 해.\",\"emotion\":\"경멸\"}", "end_turn")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SceneChatPipeline.replyJson(result("{\"reply\":\"흥, 그 정도는", "max_tokens")))
                .isInstanceOf(IllegalStateException.class);
    }

    static Llm.Result result(String text, String stopReason) {
        return new Llm.Result(text, null, null, 0, stopReason, false);
    }

    @Test
    void todayNotePicksTheWeekday() {
        // 2026-11-05는 목요일 → TODAY 네 번째 줄
        java.time.Clock thursday =
                java.time.Clock.fixed(java.time.Instant.parse("2026-11-05T03:00:00Z"), java.time.ZoneOffset.UTC);
        assertThat(SceneChatPipeline.today(MALFOY, thursday)).startsWith("엄마가 부엉이 편으로 과자 상자를 보냈다");
    }

    @Test
    void givingUpIsEndingBeforeHalfTheTarget() {
        assertThat(DialogueService.gaveUp(closing("NORMAL", 20 * 60, 50))).isTrue();
        assertThat(DialogueService.gaveUp(closing("NORMAL", 30 * 60, 50))).isFalse();
        assertThat(DialogueService.gaveUp(closing("AUTO_CLOSED", 60, 50))).isFalse(); // 자동 종료는 포기가 아님
        assertThat(DialogueService.gaveUp(closing("NORMAL", 60, null))).isFalse();
    }

    static com.capsule.session.api.SessionTypes.SessionClosing closing(String endType, int seconds, Integer target) {
        return new com.capsule.session.api.SessionTypes.SessionClosing(1, 1, 1, "말포이", endType, seconds, target);
    }
}
