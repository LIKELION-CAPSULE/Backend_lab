package com.capsule.user.api;

import com.capsule.user.api.UserTypes.ReferralReward;
import java.time.Instant;
import java.util.Optional;

/** 친구 초대 보상 (api.md 22 '서버가 하는 일' 7 · 23 referralReward · 36 '초대 규칙'). session이 연결을 끝낼 때 부른다 */
public interface ReferralRewards {

    /**
     * 연결 끊기 트랜잭션 안에서: 이 사용자가 초대 코드를 입력했고 아직 보상 전이면 이 연결로 조건 충족 →
     * 받은 사람 1,000 · 초대한 사람 1,000(10명까지)을 크레딧 초대분으로 준다.
     *
     * @return 받은 사람 몫을 이번에 줬으면 그 금액, 아니면 빈 값
     */
    Optional<ReferralReward> qualify(long userId, long connectionId, Instant endedAt);

    /** 연결 기록 다시 읽기 (23): 이 연결로 받은 사람 몫을 받았으면 그 금액 */
    Optional<ReferralReward> rewardOf(long userId, long connectionId);
}
