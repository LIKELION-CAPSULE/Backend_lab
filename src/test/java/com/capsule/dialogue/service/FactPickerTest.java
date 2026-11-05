package com.capsule.dialogue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.capsule.common.settings.RuntimeSettings;
import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.dialogue.client.Decider;
import com.capsule.dialogue.client.Decider.Decision;
import com.capsule.dialogue.config.DialogueProperties;
import com.capsule.dialogue.dto.ChatInput;
import com.capsule.dialogue.dto.LlmContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 아는 사실은 결정 모델이 [사실 + 선택 안 함] 중 하나를 고르고, 고른 것만 쓴다 (ADR 0022 결정 16) */
class FactPickerTest {

    static final List<String> FACTS = List.of("사용자는 토익 800 목표", "사용자는 밤에 공부가 잘 됨");
    final Decider decider = mock(Decider.class);
    final DialogueProperties props = YmlDefaults.DIALOGUE;
    final FactPicker picker = new FactPicker(decider, RuntimeSettings.fixed(props));
    final LlmContext ctx = LlmContext.app(1, 1, null);

    static ChatInput input(List<String> facts) {
        return new ChatInput(
                new CharacterInfo(1, 1, "말포이", null, List.of(), "", true, null),
                "민수",
                2,
                null,
                facts,
                List.of(),
                List.of(),
                "토익 점수 걱정돼",
                Map.of(),
                false,
                false,
                0);
    }

    void answer(String choice, double confidence) { // 확신 기준은 signal-min-confidence 0.6
        when(decider.decide(anyString(), any(), any()))
                .thenReturn(Map.of(FactPicker.QUESTION, new Decision(choice, confidence)));
    }

    @Test
    void picksOneFactOrNone() {
        answer("f1", 0.9);
        assertThat(picker.pick(input(FACTS), ctx)).contains("사용자는 토익 800 목표");

        answer(FactPicker.NONE, 0.9);
        assertThat(picker.pick(input(FACTS), ctx)).isEmpty();

        answer("f2", 0.4); // 확신이 낮으면 안 꺼냄
        assertThat(picker.pick(input(FACTS), ctx)).isEmpty();
    }

    @Test
    void noFactsMeansNoCallAndFailureMeansNone() {
        assertThat(picker.pick(input(List.of()), ctx)).isEmpty();
        verify(decider, never()).decide(anyString(), any(), any());

        when(decider.decide(anyString(), any(), any())).thenThrow(new IllegalStateException("Jev 실패"));
        assertThat(picker.pick(input(FACTS), ctx)).isEmpty();
    }
}
