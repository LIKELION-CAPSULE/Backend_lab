package com.capsule.dialogue.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.dialogue.config.CharacterContentSeeder;
import com.capsule.dialogue.domain.ChatMessage;
import com.capsule.dialogue.dto.CharacterContent;
import com.capsule.dialogue.dto.ChatInput;
import com.capsule.dialogue.dto.ChatLine;
import com.capsule.dialogue.dto.EngineOptions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 장면 카드에 이번 턴에 쓸 것만 들어가는지 (DB 없이, 말포이 처음 값으로) */
class SceneChatPipelineTest {

    static final CharacterContent MALFOY = CharacterContentSeeder.load("malfoy", 1);

    static ChatInput input(String message, List<ChatLine> history, List<String> facts) {
        return input(message, history, facts, false);
    }

    static ChatInput input(String message, List<ChatLine> history, List<String> facts, boolean onBreak) {
        return input(message, history, facts, onBreak, false);
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
                chatty);
    }

    @Test
    void breakChangesLengthAndMovePool() {
        String study = SceneChatPipeline.scene(MALFOY, input("오늘 뭐 해", List.of(), List.of()), null, false)
                .card();
        String rest = SceneChatPipeline.scene(MALFOY, input("오늘 뭐 해", List.of(), List.of(), true), null, false)
                .card();
        assertThat(study).contains("지금: 지금은 공부 중이다. 1~2문장");
        assertThat(rest).contains("지금: 지금은 쉬는 시간이다. 2~4문장");
        // 쉬는 시간에는 공부용 기술(내기 · 비교로 올라서기 등)을 고르지 않는다
        for (int i = 0; i < 30; i++) {
            var move = SceneChatPipeline.pickMove(
                    MALFOY.moves(), com.capsule.relation.api.RelationTypes.SignalType.NEUTRAL, CharacterContent.BREAK);
            assertThat(move.usableIn(CharacterContent.BREAK)).isTrue();
            assertThat(move.code()).isNotIn("compare_up", "challenge", "secret_study", "hint_muggle");
        }
    }

    @Test
    void reactionCardUsesStageToneAndScriptedLinesAsExamples() {
        String card = ReactionWriter.card(MALFOY, "사용자가 자리를 비웠다", "AWAY", 2, 2, "민수", null, List.of());
        assertThat(card).contains("방금 일어난 일: 사용자가 자리를 비웠다");
        assertThat(card).contains("관계: 라이벌이자 공부 짝");
        assertThat(card).contains("…진짜 간 거야? 금방 와. (삐짐)"); // 강도 칸(AWAY_2)의 미리 쓴 대사가 예시로
        assertThat(card).doesNotContain("<지난 세션 흐름>"); // 없는 재료는 칸도 없음
    }

    @Test
    void chattyStudyTurnSendsBackToStudyButNotOnBreak() {
        String chatty = SceneChatPipeline.scene(
                        MALFOY, input("ㅋㅋ 또 뭐 해", List.of(), List.of(), false, true), null, false)
                .card();
        String breakChatty = SceneChatPipeline.scene(
                        MALFOY, input("ㅋㅋ 또 뭐 해", List.of(), List.of(), true, true), null, false)
                .card();
        assertThat(chatty).contains(SceneChatPipeline.CHATTY);
        assertThat(breakChatty).doesNotContain(SceneChatPipeline.CHATTY); // 쉬는 시간엔 수다가 정상
    }

    @Test
    void sceneCardCarriesOnlyWhatThisTurnNeeds() {
        String card = SceneChatPipeline.scene(
                        MALFOY,
                        input("사서 핀스 부인 지나갔어", List.of(), List.of("사용자는 토익 800 목표", "사용자는 밤에 공부가 잘 됨")),
                        "사용자는 토익 800 목표",
                        false)
                .card();
        assertThat(card).contains("핀스 부인: 깐깐한 도서관 사서"); // 키워드가 맞는 지식 카드만
        assertThat(card).doesNotContain("퀴디치"); // 안 맞는 카드는 안 들어감
        assertThat(card).contains("관계: 라이벌이자 공부 짝"); // 관계 단계 2의 말투
        assertThat(card).contains("시험: 중간고사"); // 세션 첫 대화에만 일정
        assertThat(card).contains("\"흥\"로 말을 시작하지 않기"); // 말버릇은 데이터
        assertThat(card).contains("이번 대화 기술: ");
        // 아는 사실은 결정 모델이 고른 하나만, 고르지 않은 사실은 안 들어감. 세션 카드는 들어감
        assertThat(card).contains("<꺼낼 수 있는 기억> 사용자는 토익 800 목표");
        assertThat(card).doesNotContain("밤에 공부가 잘 됨");
        assertThat(card).contains("11/03 55분, 3단원 하다 일찍 끝냄");
    }

    @Test
    void noPickedFactMeansNoMemoryBlock() {
        String card = SceneChatPipeline.scene(
                        MALFOY, input("오늘 뭐 해", List.of(), List.of("사용자는 토익 800 목표")), null, false)
                .card();
        // "토익"은 무작위로 고른 대화 기술의 예시에도 나올 수 있어서 기억 문장에만 있는 말로 본다
        assertThat(card).doesNotContain("꺼낼 수 있는 기억").doesNotContain("800 목표");
    }

    @Test
    void scheduleOnlyOnFirstTurnAndOncePerSessionHabit() {
        List<ChatLine> history =
                List.of(new ChatLine(ChatMessage.USER, "안녕"), new ChatLine(ChatMessage.CHARACTER, "아버지가 그러셨지. 공부나 해."));
        String card = SceneChatPipeline.scene(MALFOY, input("오늘 뭐 해", history, List.of()), null, false)
                .card();
        assertThat(card).doesNotContain("중간고사");
        assertThat(card).contains("\"아버지\" 쓰지 않기 (이번 세션에 이미 씀)");
    }

    @Test
    void movesFollowTheSignal() {
        assertThat(SceneChatPipeline.pickMove(
                                MALFOY.moves(),
                                com.capsule.relation.api.RelationTypes.SignalType.COMPLIMENT,
                                CharacterContent.STUDY)
                        .code())
                .isEqualTo("flustered");
    }
}
