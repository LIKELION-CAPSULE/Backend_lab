package com.capsule.content.api;

import com.capsule.content.api.ContentTypes.Reaction;

public interface ReactionQuery {

    /**
     * 캐릭터 모델의 live2d_reaction_maps에서 (trigger, intensity)에 맞는 행 중 하나를 같은 확률로 고른다.
     * intensity가 null이면 intensity가 NULL인 행을 본다. 맞는 행이 없으면 {@link Reaction#NONE}.
     *
     * @param trigger 18 응답의 '알아채는' 동작(GREETING · AWAY · RETURN · BREAK · BREAK_END · DROWSY · YAWN · WAVE · HEAD_TURN)
     *     또는 19 · 20의 감정 이름(예: 웃음, 수줍음). 감정 목록은 서버 enum이 아니라 반응 표의 데이터 (schema.sql)
     */
    Reaction pick(long characterId, String trigger, Integer intensity);
}
