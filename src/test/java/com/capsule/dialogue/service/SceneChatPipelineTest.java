package com.capsule.dialogue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.dialogue.client.Llm;
import com.capsule.dialogue.client.LlmJson;
import com.capsule.dialogue.config.CharacterContentSeeder;
import com.capsule.dialogue.domain.ChatMessage;
import com.capsule.dialogue.dto.CharacterContent;
import com.capsule.dialogue.dto.ChatInput;
import com.capsule.dialogue.dto.ChatLine;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 고정 블록에 지금 관계 단계의 것만, 상황 카드에 이번 턴의 사실만 들어가는지 (DB 없이, 말포이 처음 값으로) */
class SceneChatPipelineTest {

    static final CharacterContent MALFOY = CharacterContentSeeder.load("malfoy", 1);

    static ChatInput input(String message, List<ChatLine> history, List<String> facts) {
        return input(message, history, facts, false, false);
    }

    static ChatInput input(
            String message, List<ChatLine> history, List<String> facts, boolean onBreak, boolean chatty) {
        CharacterInfo info = new CharacterInfo(1, 1, "말포이", null, List.of(), "", true, null, null);
        return new ChatInput(
                info,
                "민수",
                2,
                "11/03 55분, 3단원 하다 일찍 끝냄",
                facts,
                List.of("시험: 중간고사 (2026-11-20)"),
                history,
                message,
                Map.of(),
                onBreak,
                chatty,
                52);
    }

    static String card(ChatInput in, String fact) {
        return SceneChatPipeline.card(MALFOY, in, fact, "2026-11-05 (목) 21:00", "엄마가 과자 상자를 보냈다");
    }

    @Test
    void fixedBlockCarriesOnlyThisStagesVoice() {
        Map<String, String> one = SceneChatPipeline.systemVars(MALFOY, 1, "민수");
        Map<String, String> four = SceneChatPipeline.systemVars(MALFOY, 4, "민수");
        assertThat(one.get("relation")).contains("서먹한 사이").contains("이름은 부르지 않는다");
        assertThat(four.get("relation")).contains("공부 짝").contains("속마음");
        // 말투 샘플은 그 단계 묶음만, {name}은 닉네임으로
        assertThat(one.get("samples")).contains("보면 몰라? 앉든가.").doesNotContain("입꼬리");
        assertThat(four.get("samples")).contains("입꼬리가 실수한 거야").doesNotContain("앉든가");
        assertThat(SceneChatPipeline.systemVars(MALFOY, 3, "민수").get("samples")).contains("웃기지 마, 민수.");
        // 상태 변화 예시는 답장 블록에 없다 (상태 반응 카드에 그 상황 것만)
        assertThat(one).doesNotContainKey("events");
    }

    @Test
    void reactionExamplesAreOnlyThisSituationsTwoLines() {
        // 단계 1은 PM 대사부터 두 줄, 단계 4는 단계 4 대사. 강도 칸이 있으면 그 칸
        assertThat(ReactionWriter.examples(MALFOY, "AWAY", null, 1, "민수"))
                .containsExactly("야 어디가?!", "말도 없이 가네, 예의는 집에 두고 다니는구나");
        assertThat(ReactionWriter.examples(MALFOY, "AWAY", 3, 1, "민수"))
                .first()
                .asString()
                .contains("도망간 줄");
        assertThat(ReactionWriter.examples(MALFOY, "AWAY", null, 4, "민수"))
                .first()
                .isEqualTo("어디 가? 빨리 와 혼자 이기면 하나도 재미없단 말이야");
        // {name}은 닉네임으로
        assertThat(ReactionWriter.examples(MALFOY, "AWAY", null, 3, "민수"))
                .first()
                .asString()
                .startsWith("어디 가 민수,");
        String card = ReactionWriter.card(MALFOY, "민수", null, List.of(), null, MALFOY.toneFor(4), List.of("야 어디가?!"));
        assertThat(card).contains("- 야 어디가?!").contains("지금 이 사람과의 사이: 가장 믿는");
    }

    @Test
    void blankNicknameIsCalledYouLikeTheFixedLines() {
        // 상황 대사(CharacterContents)와 같이 닉네임이 비면 "너"로. 전에는 ", 왔구나."처럼 이름 자리가 비었다
        for (String blank : new String[] {"", " ", null}) {
            assertThat(SceneChatPipeline.systemVars(MALFOY, 3, blank).get("samples"))
                    .contains("웃기지 마, 너.")
                    .doesNotContain("{name}");
            assertThat(ReactionWriter.examples(MALFOY, "AWAY", null, 3, blank))
                    .first()
                    .asString()
                    .startsWith("어디 가 너,")
                    .doesNotContain("{name}");
        }
    }

    @Test
    void loreCardsOnlyWhenTheUserBringsThemUp() {
        // 띄어쓰기 없이 써도 걸리고, 걸린 카드만 들어간다
        String card = card(input("해리포터 알아?", List.of(), List.of()), null);
        assertThat(card).contains("<사용자가 꺼낸 원작 얘기").contains("- 해리 포터: 그리핀도르 수색꾼이자 말포이가 가장 이기고 싶은 앙숙");
        assertThat(card).doesNotContain("헤르미온느").doesNotContain("덤블도어");
        // 한 말에 여럿을 꺼내도 최대 2장 (파일 순서)
        assertThat(MALFOY.loreFor("해리랑 헤르미온느랑 위즐리 봤어", SceneChatPipeline.LORE_CARDS))
                .extracting(CharacterContent.Lore::title)
                .containsExactly("해리 포터", "헤르미온느 그레인저");
        assertThat(MALFOY.loreFor("초콜릿개구리 먹을래", 2))
                .extracting(CharacterContent.Lore::title)
                .containsExactly("과자 소포");
        assertThat(MALFOY.loreFor("해리가 또 1등이래", 2))
                .extracting(CharacterContent.Lore::title)
                .containsExactly("해리 포터");
        // 평범한 공부 말에는 안 걸린다 ('론' · '포터' · '해리' 하나만 · '빗자루' · '소포'는 키워드가 아님)
        assertThat(MALFOY.loreFor("이론 정리하고 결론 쓰는 중", 2)).isEmpty();
        assertThat(MALFOY.loreFor("오늘 수학 시험 망했어", 2)).isEmpty();
        assertThat(MALFOY.loreFor("리포터 시험이랑 해리성 장애 단원 공부 중", 2)).isEmpty();
        assertThat(MALFOY.loreFor("택배 소포 왔어, 빗자루로 청소 좀 하고 올게", 2)).isEmpty();
        // 실험실에서 keys 없이 저장한 카드도 대화를 깨지 않는다
        assertThat(new CharacterContent.Lore("빈 카드", null, "x").keys()).isEmpty();
    }

    @Test
    void cardCarriesOnlyThisTurnsFacts() {
        String card = card(input("오늘 뭐 해", List.of(), List.of("사용자는 토익 800 목표", "사용자는 밤에 공부가 잘 됨")), "사용자는 토익 800 목표");
        // 아는 사실은 결정 모델이 고른 하나만, 고르지 않은 사실은 안 들어감. 세션 카드 · 첫 대화 일정은 들어감
        assertThat(card).contains("<꺼낼 수 있는 기억> 사용자는 토익 800 목표").doesNotContain("밤에 공부가 잘 됨");
        assertThat(card).contains("11/03 55분, 3단원 하다 일찍 끝냄").contains("시험: 중간고사");
        assertThat(card).contains("첫마디에 \"흥\" 쓰지 않기"); // 말버릇은 데이터
        assertThat(card).contains("(시각 · 공부 시간은 물어보거나 꼭 필요할 때만 꺼낸다)");
        assertThat(card).contains("공부한 지: 52분").contains("말포이 오늘: 엄마가 과자 상자를 보냈다");
        // 매 턴 기술 · 세계 지식 카드 · 무작위 사건은 없다 (결정 19). 원작 얘기를 안 꺼냈으니 로어북 카드도 없다
        assertThat(card).doesNotContain("대화 기술").doesNotContain("세계 지식").doesNotContain("방금 일어난 일");
        assertThat(card).doesNotContain("원작 얘기");
        // 길이 · 구성(지금) 줄이 맨 끝
        assertThat(card.lines().toList())
                .element(card.lines().toList().size() - 2)
                .asString()
                .startsWith("지금: 지금은 공부 중이다. 3~6문장");
    }

    @Test
    void breakAndChattyChangeTheLastLines() {
        String rest = card(input("오늘 뭐 해", List.of(), List.of(), true, true), null);
        String chatty = card(input("ㅋㅋ 또 뭐 해", List.of(), List.of(), false, true), null);
        assertThat(rest).contains("지금: 지금은 쉬는 시간이다. 3~6문장").doesNotContain("수다가 길어지고"); // 쉬는 시간엔 수다가 정상
        assertThat(chatty).contains("수다가 길어지고 있다").contains("잔소리는 하지 않는다");
        // 수다가 이어지면 턴마다 돌려보내는 방법이 바뀐다
        assertThat(SceneChatPipeline.chattyDirective(0)).isNotEqualTo(SceneChatPipeline.chattyDirective(1));
        assertThat(SceneChatPipeline.chattyDirective(4)).isEqualTo(SceneChatPipeline.chattyDirective(0));
    }

    @Test
    void scheduleOnlyOnFirstTurnAndOncePerSessionHabit() {
        List<ChatLine> history =
                List.of(new ChatLine(ChatMessage.USER, "안녕"), new ChatLine(ChatMessage.CHARACTER, "아버지가 그러셨지. 공부나 해."));
        String card = card(input("오늘 뭐 해", history, List.of()), null);
        assertThat(card).doesNotContain("중간고사").doesNotContain("꺼낼 수 있는 기억");
        assertThat(card).doesNotContain("말포이 오늘"); // 근황은 첫 대화에만
        assertThat(card(input("오늘 뭐 해", history, List.of(), true, false), null))
                .doesNotContain("말포이 오늘"); // 쉬는 시간에도 안 넣음
        // 말버릇 규칙은 데이터 (말포이 v5는 비어 있어 규칙이 있는 사본으로)
        CharacterContent habits = new CharacterContent(
                MALFOY.characterId(),
                MALFOY.code(),
                MALFOY.version(),
                MALFOY.speaker(),
                MALFOY.bible(),
                MALFOY.rules(),
                MALFOY.safety(),
                MALFOY.expressions(),
                MALFOY.expressionGuide(),
                MALFOY.stageTones(),
                MALFOY.studyStyle(),
                MALFOY.breakStyle(),
                MALFOY.lines(),
                MALFOY.samples(),
                MALFOY.scores(),
                Map.of("흥", CharacterContent.NO_OPENING, "아버지", CharacterContent.ONCE_PER_SESSION),
                MALFOY.lore());
        assertThat(SceneChatPipeline.avoid(habits, history))
                .containsExactlyInAnyOrder("첫마디에 \"흥\" 쓰지 않기", "\"아버지\" 쓰지 않기 (이번 세션에 이미 씀)");
        assertThat(SceneChatPipeline.avoid(habits, List.of())).containsExactly("첫마디에 \"흥\" 쓰지 않기");
    }

    @Test
    void reactionDropsAForbiddenOpening() {
        // 첫마디 금지 말은 뒤 쉼표 · 공백까지 뗀다. 낱말 경계만, 떼고 남는 게 없으면 원문
        assertThat(SceneChatPipeline.dropOpening(MALFOY, "흥, 시작은 했네 민수")).isEqualTo("시작은 했네 민수");
        assertThat(SceneChatPipeline.dropOpening(MALFOY, "흥 5분이나 자리 비우고")).isEqualTo("5분이나 자리 비우고");
        assertThat(SceneChatPipeline.dropOpening(MALFOY, "흥… 됐어")).isEqualTo("됐어");
        assertThat(SceneChatPipeline.dropOpening(MALFOY, "흥미롭네, 그 풀이")).isEqualTo("흥미롭네, 그 풀이");
        assertThat(SceneChatPipeline.dropOpening(MALFOY, "쉰다고? 흥, 나도")).isEqualTo("쉰다고? 흥, 나도"); // 중간은 그대로
        assertThat(SceneChatPipeline.dropOpening(MALFOY, "흥.")).isEqualTo("흥.");
    }

    @Test
    void reactionCardIsFactsAndFormatOnly() {
        String card = ReactionWriter.card(MALFOY, "민수", null, List.of(), null, null, List.of());
        assertThat(card).contains("사용자: 민수").contains("한두 문장");
        assertThat(card).doesNotContain("<지난 세션 흐름>").doesNotContain("오늘:").doesNotContain("말투 예시"); // 없는 재료는 칸도 없음
    }

    @Test
    void expressionChoicesFollowTheListWithTheGuide() {
        // 결정 모델 보기: 표정 목록 순서 그대로, 설명이 없으면 이름
        assertThat(MALFOY.expressionChoices().keySet()).containsExactlyElementsOf(MALFOY.expressions());
        assertThat(MALFOY.expressionChoices().get("하품")).contains("졸리");
        assertThat(MALFOY.withVersion(1).expressionGuide()).isEqualTo(MALFOY.expressionGuide());
        // 대사에 표정을 쓰게 하지 않는다
        assertThat(SceneChatPipeline.systemVars(MALFOY, 2, "민수")).doesNotContainKey("expressions");
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
        // 맨 글(기본 형식) 속 낱말 "emotion"이나 { 로 시작하는 수식은 깨진 JSON이 아니다: 그대로 대사
        assertThat(SceneChatPipeline.spoken(result("\"emotion\"은 감정이야. 외워.", "end_turn")))
                .isEqualTo("\"emotion\"은 감정이야. 외워.");
        assertThat(SceneChatPipeline.spoken(result("{1, 2}부터 봐. 그게 집합이야.", "end_turn")))
                .isEqualTo("{1, 2}부터 봐. 그게 집합이야.");
        // 옛 형식 · 가짜 AI의 JSON이면 그 reply
        assertThat(SceneChatPipeline.spoken(result("{\"reply\":\"흥.\",\"emotion\":\"경멸\"}", "end_turn")))
                .isEqualTo("흥.");
        assertThatThrownBy(() -> SceneChatPipeline.spoken(result("  ", "end_turn")))
                .isInstanceOf(IllegalStateException.class);
    }

    static Llm.Result result(String text, String stopReason) {
        return new Llm.Result(text, null, null, 0, stopReason, false);
    }

    @Test
    void replyWrittenAsTextBeforeTheEmotionJsonIsKept() {
        String text = "그걸 이제 풀었어? …뭐, 풀긴 했네.{\"emotion\":\"웃음\"}";
        assertThat(SceneChatPipeline.replyText(text, LlmJson.objectIn(text))).isEqualTo("그걸 이제 풀었어? …뭐, 풀긴 했네.");
        String ok = "{\"reply\":\"왔냐.\",\"emotion\":\"경멸\"}";
        assertThat(SceneChatPipeline.replyText(ok, LlmJson.objectIn(ok))).isEqualTo("왔냐.");
    }

    @Test
    void reactionAcceptsPlainTextAndContentKey() {
        // 스모크: BREAK_END 반응이 JSON 없이 맨 글로 와서 형식 오류 → 고정 대사로 갔다
        assertThat(ReactionWriter.parse(result("좋아, 다시 하자.", "stop"), e -> e))
                .isEqualTo(new com.capsule.dialogue.dto.ReactionLine("좋아, 다시 하자.", null));
        assertThat(ReactionWriter.parse(result("{\"content\":\"왔네.\",\"emotion\":\"웃음\"}", "stop"), e -> e))
                .isEqualTo(new com.capsule.dialogue.dto.ReactionLine("왔네.", "웃음"));
        assertThatThrownBy(() -> ReactionWriter.parse(result("{\"reply\":\"좋아, 다시", "length"), e -> e))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void todayNotePicksTheWeekday() {
        // 2026-11-05는 목요일 → TODAY 네 번째 줄
        java.time.Clock thursday =
                java.time.Clock.fixed(java.time.Instant.parse("2026-11-05T03:00:00Z"), java.time.ZoneOffset.UTC);
        assertThat(SceneChatPipeline.today(MALFOY, thursday)).startsWith("엄마가 부엉이 편으로 초콜릿 개구리를");
    }

    @Test
    void givingUpIsEndingBeforeHalfTheTarget() {
        double ratio = YmlDefaults.DIALOGUE.giveUpRatio(); // 0.5
        assertThat(DialogueService.gaveUp(closing("NORMAL", 20 * 60, 50), ratio))
                .isTrue();
        assertThat(DialogueService.gaveUp(closing("NORMAL", 30 * 60, 50), ratio))
                .isFalse();
        assertThat(DialogueService.gaveUp(closing("AUTO_CLOSED", 60, 50), ratio))
                .isFalse(); // 자동 종료는 포기가 아님
        assertThat(DialogueService.gaveUp(closing("NORMAL", 60, null), ratio)).isFalse();
    }

    static com.capsule.session.api.SessionTypes.SessionClosing closing(String endType, int seconds, Integer target) {
        return new com.capsule.session.api.SessionTypes.SessionClosing(1, 1, 1, "말포이", endType, seconds, target);
    }
}
