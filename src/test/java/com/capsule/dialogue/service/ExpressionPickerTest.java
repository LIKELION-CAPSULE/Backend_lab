package com.capsule.dialogue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.capsule.common.settings.RuntimeSettings;
import com.capsule.dialogue.client.Decider;
import com.capsule.dialogue.client.Decider.Decision;
import com.capsule.dialogue.dto.LlmContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 표정은 대사를 다 쓴 뒤 결정 모델이 캐릭터 표정 목록에서 고른다 (ADR 0028) */
class ExpressionPickerTest {

    final Decider decider = mock(Decider.class);
    final ExpressionPicker picker = new ExpressionPicker(decider, RuntimeSettings.fixed(YmlDefaults.DIALOGUE));
    final LlmContext ctx = LlmContext.app(1, 1, java.time.Instant.EPOCH); // 답장 마감이 이미 지나도 따로 고른다

    @Test
    void choicesAreTheCharactersExpressionsWithTheirGuide() {
        when(decider.decide(anyString(), any(), any()))
                .thenReturn(Map.of(ExpressionPicker.QUESTION, new Decision("수줍음", 0.8)));

        assertThat(picker.pick(SceneChatPipelineTest.MALFOY, "너 공부 잘한다", "갑자기 그런 말 하지 마.", ctx))
                .isEqualTo("수줍음");
        // 보기 = 표정 8개 그대로(순서 · 설명 포함), 상태 = 사용자 말 + 대사, 마감 없이
        verify(decider)
                .decide(
                        contains("말포이: 갑자기 그런 말 하지 마."),
                        argThat(q -> {
                            Map<String, String> criteria =
                                    q.get(ExpressionPicker.QUESTION).criteria();
                            return List.copyOf(criteria.keySet()).equals(SceneChatPipelineTest.MALFOY.expressions())
                                    && criteria.get("수줍음").contains("칭찬");
                        }),
                        argThat(c -> c.deadline() == null));
    }

    @Test
    void failureOrNoReplyMeansNoExpression() {
        assertThat(picker.pick(SceneChatPipelineTest.MALFOY, "안녕", " ", ctx)).isNull();
        verify(decider, never()).decide(anyString(), any(), any());

        when(decider.decide(anyString(), any(), any())).thenThrow(new IllegalStateException("Jev 실패"));
        assertThat(picker.pick(SceneChatPipelineTest.MALFOY, "안녕", "왔어?", ctx)).isNull();

        // Decider는 목록 밖 보기를 버린다 → 답이 없으면 null
        org.mockito.Mockito.doReturn(Map.of()).when(decider).decide(anyString(), any(), any());
        assertThat(picker.pick(SceneChatPipelineTest.MALFOY, "안녕", "왔어?", ctx)).isNull();
    }
}
