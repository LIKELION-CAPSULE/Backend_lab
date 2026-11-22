package com.capsule.relation.api;

import com.capsule.relation.api.RelationTypes.AffinityChange;
import com.capsule.relation.api.RelationTypes.IntimacyNow;
import com.capsule.relation.api.RelationTypes.IntimacySettlement;
import com.capsule.relation.api.RelationTypes.RelationView;
import com.capsule.relation.api.RelationTypes.SessionEventRef;
import com.capsule.relation.api.RelationTypes.StudySettlement;
import java.util.Set;

public interface RelationQuery {

    RelationView view(long userId, long characterId);

    /** 지금 친밀도 단계 1~5. 안 만났으면 1 (api.md 16 relation) */
    int stage(long userId, long characterId);

    /** 끝난 연결의 친밀도 정산을 다시 읽는다 (연결 끊기 재호출 · 연결 기록, api.md 22 · 23). 정산이 없으면 지금 단계로 0초 */
    IntimacySettlement intimacyOf(long userId, long characterId, long connectionId);

    /**
     * 이미 반영된 이벤트의 호감도 변화를 다시 읽는다 (이벤트 반응 받기, api.md 19).
     * 같은 이벤트로 몇 번을 불러도 호감도는 다시 바뀌지 않는다.
     */
    AffinityChange changeOf(SessionEventRef event);

    /** 끝난 세션의 정산 결과를 기록에서 다시 읽는다 (공부 종료 재호출·리포트 조회, api.md 22·23) */
    StudySettlement settlementOf(long userId, long characterId, long sessionId);

    /** 지금 친밀도: 단계 · 누적 인정 시간 · 다음 단계까지 남은 시간 (내 메이트, api.md 37). 안 만났으면 1단계 · 0초 */
    IntimacyNow intimacyNow(long userId, long characterId);

    /** 이 사용자에게 열린 해금 콘텐츠 id 전부 (user_unlocked_contents, 내 메이트 api.md 37 unlocks[].unlocked) */
    Set<Long> unlockedContentIds(long userId);
}
