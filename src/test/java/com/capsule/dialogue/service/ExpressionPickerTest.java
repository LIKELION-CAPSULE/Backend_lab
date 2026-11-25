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
    void lockedUnlockExpressionsAreLeftOutOfTheChoices() {
        // 해금 표정 (api.md 19 emotion, 2026-11-26): 수줍음은 3단계 해금(아직 안 열림), 행복은 2단계 해금(열림)
        com.capsule.content.api.UnlockQuery unlocks = mock(com.capsule.content.api.UnlockQuery.class);
        com.capsule.relation.api.RelationQuery relations = mock(com.capsule.relation.api.RelationQuery.class);
        long id = SceneChatPipelineTest.MALFOY.characterId();
        when(unlocks.between(id, 0, Integer.MAX_VALUE))
                .thenReturn(List.of(
                        new com.capsule.content.api.ContentTypes.UnlockInfo(9, "EXPRESSION", "수줍은 웃음", 3, "수줍음", null),
                        new com.capsule.content.api.ContentTypes.UnlockInfo(8, "EXPRESSION", "함박웃음", 2, "행복", null),
                        new com.capsule.content.api.ContentTypes.UnlockInfo(7, "VOICE_LINE", "보이스", 2, null, "a.mp3")));
        when(relations.unlockedContentIds(1)).thenReturn(java.util.Set.of(8L));
        ExpressionPicker limited = new ExpressionPicker(
                decider, RuntimeSettings.fixed(YmlDefaults.DIALOGUE), new LockedExpressions(unlocks, relations));
        when(decider.decide(anyString(), any(), any()))
                .thenReturn(Map.of(ExpressionPicker.QUESTION, new Decision("행복", 0.8)));

        assertThat(limited.pick(SceneChatPipelineTest.MALFOY, "고마워", "별거 아니야.", ctx))
                .isEqualTo("행복");
        verify(decider)
                .decide(
                        anyString(),
                        argThat(q -> {
                            Map<String, String> criteria =
                                    q.get(ExpressionPicker.QUESTION).criteria();
                            return !criteria.containsKey("수줍음")
                                    && criteria.containsKey("행복")
                                    && criteria.containsKey("삐짐");
                        }),
                        any());

        // 실험실 · 평가(사용자 없음)는 제한하지 않는다
        LlmContext lab = new LlmContext(LlmContext.LAB, null, null, null, List.of(), null, null, null, null, null);
        org.mockito.Mockito.clearInvocations(decider);
        limited.pick(SceneChatPipelineTest.MALFOY, "고마워", "별거 아니야.", lab);
        verify(decider)
                .decide(
                        anyString(),
                        argThat(q -> q.get(ExpressionPicker.QUESTION).criteria().containsKey("수줍음")),
                        any());
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
