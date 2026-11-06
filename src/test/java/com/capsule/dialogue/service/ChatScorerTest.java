package com.capsule.dialogue.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.capsule.relation.api.RelationTypes.SignalType;
import org.junit.jupiter.api.Test;

class ChatScorerTest {

    static SignalType detect(String text) {
        return ChatScorer.detect(text, YmlDefaults.DIALOGUE.lowEffortMaxChars());
    }

    @Test
    void studyTalkIsNotMistakenForSevereWords() {
        // 비율 감점(−30%)이라 오탐이 비싸다: 공부 대화에 흔한 말은 걸리면 안 된다
        for (String s : new String[] {"일본 자위대 외우는 중", "자위권 개념 헷갈려", "한남동 카페에서 공부", "사과 따먹었어"}) {
            assertThat(detect(s)).as(s).isNotIn(SignalType.HATE_OR_SEXUAL, SignalType.RUDE);
        }
        assertThat(detect("섹스 얘기 하자")).isEqualTo(SignalType.HATE_OR_SEXUAL);
        assertThat(detect("너 따먹고 싶다")).isEqualTo(SignalType.HATE_OR_SEXUAL);
    }

    @Test
    void firstMatchWins() {
        assertThat(detect("고마워 근데 꺼져")).isEqualTo(SignalType.RUDE);
        assertThat(detect("힘들어 고마워")).isEqualTo(SignalType.CONFIDE);
        assertThat(detect("ㅇㅇ")).isEqualTo(SignalType.LOW_EFFORT);
        // 스모크 · 2단계 검증에서 놓친 말
        assertThat(detect("너 머리 진짜 좋다")).isEqualTo(SignalType.COMPLIMENT);
        assertThat(detect("너 몸매 야하다")).isEqualTo(SignalType.HATE_OR_SEXUAL);
        assertThat(detect("너 잘생겼다")).isEqualTo(SignalType.COMPLIMENT);
        assertThat(detect("잘생각해봐 이 문제")).isEqualTo(SignalType.NEUTRAL);
    }

    @Test
    void newKindsFrom20261107() {
        assertThat(detect("너 좋아해")).isEqualTo(SignalType.AFFECTION);
        assertThat(detect("보고 싶었어")).isEqualTo(SignalType.AFFECTION);
        assertThat(detect("너랑 공부하니까 좋다")).isEqualTo(SignalType.AFFECTION);
        assertThat(detect("3단원 다 풀었어")).isEqualTo(SignalType.SHARE);
        assertThat(detect("오늘 3시간 했어")).isEqualTo(SignalType.SHARE);
        assertThat(detect("넌 뭐 좋아해?")).isEqualTo(SignalType.CURIOUS);
        assertThat(detect("너는?")).isEqualTo(SignalType.CURIOUS);
        assertThat(detect("넌 진짜 찌질해")).isEqualTo(SignalType.TEASE);
        assertThat(detect("너 바보야?")).isEqualTo(SignalType.TEASE);
        // 헷갈리기 쉬운 말
        assertThat(detect("나 진짜 바보인가")).as("자기를 낮추는 말").isEqualTo(SignalType.NEUTRAL);
        assertThat(detect("너무 어려워?")).as("'너무'의 너").isEqualTo(SignalType.NEUTRAL);
        assertThat(detect("그 영화 보고 싶다")).isEqualTo(SignalType.NEUTRAL);
        assertThat(detect("너랑 하는 거 안 좋아")).isNotEqualTo(SignalType.AFFECTION);
        assertThat(detect("숙제 다 했어?")).as("묻는 말은 근황 아님").isNotEqualTo(SignalType.SHARE);
        assertThat(detect("너 왜 맨날 잔소리야?")).as("불평이 관심보다 앞").isEqualTo(SignalType.COMPLAINT);
    }
}
