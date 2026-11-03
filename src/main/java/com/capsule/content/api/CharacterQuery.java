package com.capsule.content.api;

import com.capsule.content.api.ContentTypes.CharacterInfo;
import java.util.Optional;

public interface CharacterQuery {

    /** 비활성 캐릭터도 찾음. 그 룸 캐릭터인지는 CharacterInfo.roomId로 판단 */
    Optional<CharacterInfo> find(long characterId);
}
