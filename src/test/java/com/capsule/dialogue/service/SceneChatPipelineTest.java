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
        assertThat(card.lines().count()).isLessThan(40);
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
    void movesFollowTheSignalAndFactsFollowTheWords() {
        assertThat(SceneChatPipeline.pickMove(
                                MALFOY.moves(), com.capsule.relation.api.RelationTypes.SignalType.COMPLIMENT)
                        .code())
                .isEqualTo("flustered");
        List<String> facts = List.of("사용자는 토익 800 목표", "사용자는 밤에 공부가 잘 됨", "사용자는 수학이 불안함");
        // 모든 사실에 붙은 "사용자는"으로는 고르지 않는다
        assertThat(SceneChatPipeline.pickFacts(facts, "토익 점수 걱정돼")).containsExactly("사용자는 토익 800 목표");
        assertThat(SceneChatPipeline.pickFacts(facts, "오늘 날씨")).hasSize(2); // 겹치는 게 없으면 최근 2개
    }
}
