package com.capsule.session.dto;

/**
 * 메이트 반응 (api.md 16 greeting · 18 reaction). 프론트 플레이어 react(trigger, intensity)
 *
 * @param trigger GREETING · AWAY · RETURN · IDLE
 * @param intensity AWAY만 1~3, 그 외 null
 * @param line 대사. 대사 생성은 아직 19번이라 지금은 null
 * @param expression 반응표의 표정 이름. 없으면 null
 * @param audio 음성 단계 전이라 null
 */
public record ReactionView(String trigger, Integer intensity, String line, String expression, Object audio) {

    public static ReactionView of(String trigger, Integer intensity, String expression) {
        return new ReactionView(trigger, intensity, null, expression, null);
    }
}
