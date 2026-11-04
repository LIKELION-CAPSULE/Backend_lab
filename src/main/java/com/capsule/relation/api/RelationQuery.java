package com.capsule.relation.api;

import com.capsule.relation.api.RelationTypes.AffinityChange;
import com.capsule.relation.api.RelationTypes.ChatSignal;
import com.capsule.relation.api.RelationTypes.RelationView;
import com.capsule.relation.api.RelationTypes.SessionEventRef;
import com.capsule.relation.api.RelationTypes.StudySettlement;

public interface RelationQuery {

    RelationView view(long userId, long characterId);

    /**
     * 이미 반영된 이벤트의 호감도 변화를 다시 읽는다 (이벤트 반응 받기, api.md 19).
     * 같은 이벤트로 몇 번을 불러도 호감도는 다시 바뀌지 않는다.
     */
    AffinityChange changeOf(SessionEventRef event);

    /** 끝난 세션의 정산 결과를 기록(relation_logs)에서 다시 계산한다 (공부 종료 재호출·리포트 조회, api.md 22·23) */
    StudySettlement settlementOf(long userId, long characterId, long sessionId);

    /** 이 신호의 점수 (하루 상한 전). 반영하지 않는다: 실험실·평가가 "서비스였다면 몇 점"을 보여 줄 때 */
    int chatPoints(ChatSignal signal);
}
