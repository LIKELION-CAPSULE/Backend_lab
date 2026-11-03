package com.capsule.relation.api;

import com.capsule.relation.api.RelationTypes.AffinityChange;
import com.capsule.relation.api.RelationTypes.SessionEventRef;
import com.capsule.relation.api.RelationTypes.StudySettlement;
import java.util.UUID;

/** 호감도를 바꾸는 쪽. 얼마나 바꿀지(공식)는 relation이 정한다 (판단 백로그 J-01·J-02·J-03). */
public interface RelationCommands {

    /** 처음 만나면 관계 행을 만들고, 이미 있으면 last_met_at만 갱신 (공부 시작, api.md 16) */
    void meet(long userId, long characterId);

    /** 상태 이벤트 하나를 반영하고 이 이벤트로 바뀐 만큼을 돌려준다 (api.md 18). 같은 이벤트를 두 번 반영하지 않음 */
    AffinityChange applySessionEvent(SessionEventRef event);

    /** 대화 점수 반영 (api.md 20). delta는 −5~5로 자르고, 0이면 기록하지 않음. 같은 requestId는 한 번만 */
    AffinityChange applyChat(long userId, long characterId, long sessionId, UUID requestId, int delta);

    /** 공부 종료 정산: 공부 시간 점수 + 함께한 시간 누적 + 레벨 갱신 (api.md 22) */
    StudySettlement settleStudyTime(long userId, long characterId, long sessionId, int studySeconds);
}
