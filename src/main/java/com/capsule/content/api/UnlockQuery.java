package com.capsule.content.api;

import com.capsule.content.api.ContentTypes.UnlockInfo;
import java.util.List;

/** 친밀도 해금 콘텐츠 (api.md 22 relation.unlocked, 2026-11-23 5단계) */
public interface UnlockQuery {

    /** 단계 afterStage를 넘어 upToStage까지 오르며 열리는 콘텐츠 (afterStage < unlock_stage ≤ upToStage). 없으면 빈 목록 */
    List<UnlockInfo> between(long characterId, int afterStage, int upToStage);
}
