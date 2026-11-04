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

    static final CharacterContent MALFOY = CharacterContentSeeder.load("malfoy");

    static ChatInput input(String message, List<ChatLine> history, List<String> facts) {
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
                EngineOptions.DEFAULT);
    }

    @Test
    void sceneCardCarriesOnlyWhatThisTurnNeeds() {
        String card = SceneChatPipeline.scene(
                        MALFOY, input("사서 핀스 부인 지나갔어", List.of(), List.of("사용자는 토익 800 목표", "사용자는 밤에 공부가 잘 됨")), false)
                .card();
        assertThat(card).contains("핀스 부인: 깐깐한 도서관 사서"); // 키워드가 맞는 지식 카드만
        assertThat(card).doesNotContain("퀴디치"); // 안 맞는 카드는 안 들어감
        assertThat(card).contains("관계: 라이벌이자 공부 짝"); // 관계 단계 2의 말투
        assertThat(card).contains("시험: 중간고사"); // 세션 첫 대화에만 일정
        assertThat(card).contains("\"흥\"로 말을 시작하지 않기"); // 말버릇은 데이터
        assertThat(card).contains("이번 대화 기술: ");
        // 기억 세 겹은 전부 (아는 사실 · 세션 카드). 원문 20줄은 메시지로 따로 간다
        assertThat(card).contains("사용자는 토익 800 목표", "사용자는 밤에 공부가 잘 됨");
        assertThat(card).contains("11/03 55분, 3단원 하다 일찍 끝냄");
    }

    @Test
    void allTwentyFactsAndFiveCardsFit() {
        List<String> facts = java.util.stream.IntStream.rangeClosed(1, 20)
                .mapToObj(i -> "사용자에 대한 사실 " + i + "번은 사십 자 정도 되는 꽤 긴 한 줄짜리 기억이다")
                .toList();
        ChatInput base = input("오늘 뭐 해", List.of(), facts);
        String cards = String.join(
                "\n",
                java.util.stream.IntStream.rangeClosed(1, 5)
                        .mapToObj(i -> "11/0" + i + " 55분, 지난번엔 3단원 하다 지쳐서 일찍 끝냈고 놀렸더니 삐짐")
                        .toList());
        ChatInput in = new ChatInput(
                base.character(),
                base.nickname(),
                base.relationLevel(),
                cards,
                facts,
                base.memories(),
                base.history(),
                base.userMessage(),
                null,
                Map.of(),
                EngineOptions.DEFAULT);
        String card = SceneChatPipeline.scene(MALFOY, in, false).card();
        assertThat(card).contains("사실 1번", "사실 20번", "11/01", "11/05");
        assertThat(card.length()).isLessThan(3_000); // 다 넣어도 장면 카드는 작다
    }

    @Test
    void scheduleOnlyOnFirstTurnAndOncePerSessionHabit() {
        List<ChatLine> history =
                List.of(new ChatLine(ChatMessage.USER, "안녕"), new ChatLine(ChatMessage.CHARACTER, "아버지가 그러셨지. 공부나 해."));
        String card = SceneChatPipeline.scene(MALFOY, input("오늘 뭐 해", history, List.of()), false)
                .card();
        assertThat(card).doesNotContain("중간고사");
        assertThat(card).contains("\"아버지\" 쓰지 않기 (이번 세션에 이미 씀)");
    }

    @Test
    void movesFollowTheSignal() {
        assertThat(SceneChatPipeline.pickMove(
                                MALFOY.moves(), com.capsule.relation.api.RelationTypes.SignalType.COMPLIMENT)
                        .code())
                .isEqualTo("flustered");
    }
}
