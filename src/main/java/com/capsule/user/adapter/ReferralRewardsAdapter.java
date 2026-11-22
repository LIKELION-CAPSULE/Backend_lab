package com.capsule.user.adapter;

import com.capsule.user.api.ReferralRewards;
import com.capsule.user.api.UserTypes.ReferralReward;
import com.capsule.user.service.ReferralService;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** ReferralRewards 계약 구현 (UserQueryAdapter와 같은 이유로 얇게 따로 둔다) */
@Component
@RequiredArgsConstructor
public class ReferralRewardsAdapter implements ReferralRewards {

    private final ReferralService referrals;

    @Override
    public Optional<ReferralReward> qualify(long userId, long connectionId, Instant endedAt) {
        return referrals.qualify(userId, connectionId, endedAt);
    }

    @Override
    public Optional<ReferralReward> rewardOf(long userId, long connectionId) {
        return referrals.rewardOf(userId, connectionId);
    }
}
