package com.capsule.relation.api;

import com.capsule.relation.api.RelationTypes.AffinityChange;
import com.capsule.relation.api.RelationTypes.ChatScore;
import com.capsule.relation.api.RelationTypes.IntimacySettlement;
import com.capsule.relation.api.RelationTypes.SessionEventRef;
import com.capsule.relation.api.RelationTypes.StudySettlement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 호감도를 바꾸는 쪽. 얼마나 바꿀지(공식)는 relation이 정한다 (호감도 최종안, ADR 0022). */
public interface RelationCommands {

    /** 처음 만나면 관계 행을 만들고(50점), 이미 있으면 last_met_at만 갱신 (공부 시작, api.md 16) */
    void meet(long userId, long characterId);

    /**
     * 상태 이벤트 하나를 반영하고 이 이벤트로 바뀐 만큼을 돌려준다 (api.md 18). 같은 이벤트를 두 번 반영하지 않음.
     * 세션 첫 FOCUS → 연속 출석, 자리 비움을 끝낸 상태 이벤트 → 오래 비웠으면 감점. 나머지는 0
     */
    AffinityChange applySessionEvent(SessionEventRef event);

    /**
     * 대화 한 번의 점수를 반영한다 (api.md 20). 하루 오름 상한, 심한 말 뒤 그날 오름 멈춤, 0~100 자르기는 relation이 한다.
     * 0점이어도 부르면 관계 행을 잠근다: 메시지 저장 트랜잭션이 공부 종료와 같은 순서로 잠그기 위해서다. 같은 requestId는 한 번만
     */
    AffinityChange applyChatScore(long userId, long characterId, long sessionId, UUID requestId, ChatScore score);

    /**
     * 공부 종료 정산: 함께한 시간 누적 + 레벨 갱신 (api.md 22). 호감도는 바꾸지 않는다. 같은 세션은 한 번만
     *
     * @deprecated 연결 끊기는 {@link #settleIntimacy}를 쓴다 (2026-11-23 친밀도). 같은 정산 행을 쓰므로 먼저 부르면
     *     그 연결의 친밀도 정산이 건너뛰어진다 → 새로 부르지 않는다. 제거 예정 PR: 호감도 정리 때
     */
    @Deprecated(forRemoval = true)
    StudySettlement settleStudy(long userId, long characterId, long sessionId, int studySeconds, int presentSeconds);

    /**
     * 연결 끊기 친밀도 정산 (api.md 22 '친밀도 규칙'). 인정 = min(present, 그날 남은 2시간), 그날 대화가 없으면 절반.
     * 그날 = 연결이 끝난 시각의 오전 5시 기준 날짜. 대화 턴은 {@link #applyChatScore}가 relation_daily에 센다.
     * 누적 인정 시간으로 단계를 다시 매기고(내려가지 않음), 함께한 · 연결 시간 누적과 레벨도 같이 갱신한다. 같은 연결은 한 번만
     */
    IntimacySettlement settleIntimacy(
            long userId,
            long characterId,
            long connectionId,
            Instant endedAt,
            int presentSeconds,
            int connectedSeconds);

    /** 열린 해금 콘텐츠 기록 (user_unlocked_contents). 이미 있으면 그대로 */
    void recordUnlocks(long userId, List<Long> contentIds);
}
