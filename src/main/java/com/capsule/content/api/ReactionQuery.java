package com.capsule.content.api;

import com.capsule.content.api.ContentTypes.Reaction;

public interface ReactionQuery {

    /**
     * 캐릭터 모델의 live2d_reaction_maps에서 (trigger, intensity)에 맞는 행 중 하나를 같은 확률로 고른다.
     * intensity가 null이면 intensity가 NULL인 행을 본다. 맞는 행이 없으면 {@link Reaction#NONE}.
     *
     * @param trigger GREETING · AWAY · RETURN · BREAK · BREAK_END (서버 enum, api.md 19)
     */
    Reaction pick(long characterId, String trigger, Integer intensity);
}
