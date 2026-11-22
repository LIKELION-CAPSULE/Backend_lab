package com.capsule.user.dto;

import java.time.Instant;
import java.util.List;

/** api.md 35 초대 현황. codeInputDeadline = 가입 + 7일, 입력했거나 지났으면 null */
public record ReferralStatus(
        String code,
        String link,
        int rewardedCount,
        int maxRewards,
        int rewardedCredits,
        List<ReferralFriend> friends,
        boolean canEnterCode,
        Instant codeInputDeadline) {}
