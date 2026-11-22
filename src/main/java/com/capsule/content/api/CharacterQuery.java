package com.capsule.content.api;

import com.capsule.content.api.ContentTypes.CharacterInfo;
import java.util.List;
import java.util.Optional;

public interface CharacterQuery {

    /** 비활성 캐릭터도 찾음 (active로 판단) */
    Optional<CharacterInfo> find(long characterId);

    /** 활성 캐릭터 (정렬 순서). 스터디룸이 없어진 뒤 '룸의 캐릭터'를 대신한다 (2026-11-23) */
    List<CharacterInfo> active();
}
