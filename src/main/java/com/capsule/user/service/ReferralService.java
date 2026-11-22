package com.capsule.user.service;

import com.capsule.common.error.ApiException;
import com.capsule.common.error.CommonErrorCode;
import com.capsule.credit.api.CreditCommands;
import com.capsule.user.api.UserTypes.ReferralReward;
import com.capsule.user.domain.Referral;
import com.capsule.user.domain.User;
import com.capsule.user.domain.UserErrorCode;
import com.capsule.user.dto.ReferralEntered;
import com.capsule.user.dto.ReferralFriend;
import com.capsule.user.dto.ReferralStatus;
import com.capsule.user.repository.ReferralRepository;
import com.capsule.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 친구 초대 (api.md 35 · 36 '초대 규칙', 22 '서버가 하는 일' 7) */
@Service
public class ReferralService {

    public static final int REWARD = 1000;
    public static final int MAX_INVITER_REWARDS = 10;
    static final Duration INPUT_WINDOW = Duration.ofDays(7);
    private static final int CODE_MAX = 20;
    private static final String CREDIT_TITLE = "친구 초대"; // api.md 34 title (문구 확정 필요)

    /** 초대한 사람 몫: "친구 초대 · 민지님 첫 연결" (민지 = 코드를 입력하고 첫 연결을 마친 친구, 피그마 SCR-30) */
    static String inviterTitle(String friendNickname) {
        return CREDIT_TITLE + " · " + friendNickname + "님 첫 연결";
    }

    /** 받은 사람 몫: "친구 초대 · 성욱님 초대" (성욱 = 코드를 준 사람, 확정 필요) */
    static String inviteeTitle(String inviterNickname) {
        return CREDIT_TITLE + " · " + inviterNickname + "님 초대";
    }

    private final ReferralRepository referrals;
    private final UserRepository users;
    private final CreditCommands credits;
    private final Clock clock;
    private final String linkBase;

    public ReferralService(
            ReferralRepository referrals,
            UserRepository users,
            CreditCommands credits,
            Clock clock,
            @Value("${capsule.referral.link-base:https://capsule.ai.kr/i/}") String linkBase) {
        this.referrals = referrals;
        this.users = users;
        this.credits = credits;
        this.clock = clock;
        this.linkBase = linkBase;
    }

    // ---- 35 초대 현황

    @Transactional(readOnly = true)
    public ReferralStatus status(long userId) {
        User me = activeUser(userId);
        List<Referral> mine = referrals.findByInviterIdOrderByIdDesc(userId);
        Map<Long, User> friends = users
                .findAllById(mine.stream().map(Referral::getInviteeId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<ReferralFriend> list =
                mine.stream().map(r -> friend(r, friends.get(r.getInviteeId()))).toList();
        int rewarded = (int) mine.stream().filter(Referral::isInviterRewarded).count();
        Instant deadline = deadline(me);
        boolean canEnter =
                !referrals.existsByInviteeId(userId) && Instant.now(clock).isBefore(deadline);
        return new ReferralStatus(
                me.getReferralCode(),
                linkBase + me.getReferralCode(),
                rewarded,
                MAX_INVITER_REWARDS,
                rewarded * REWARD,
                list,
                canEnter,
                canEnter ? deadline : null);
    }

    private static ReferralFriend friend(Referral r, User u) {
        boolean active = u != null && u.isActive();
        return new ReferralFriend(
                active ? u.getNickname() : null,
                r.isQualified() ? ReferralFriend.QUALIFIED : ReferralFriend.JOINED,
                r.isInviterRewarded(),
                u == null ? null : u.getCreatedAt());
    }

    // ---- 36 초대 코드 입력

    /** 검사 순서: 값 → 이미 입력함(409) → 7일 → 코드(404) → 자기 코드 → 같은 기기 */
    @Transactional
    public ReferralEntered enter(long userId, String rawCode, String rawDeviceHash) {
        String code = validCode(rawCode);
        String deviceHash = SignupPolicy.validDeviceHash(rawDeviceHash);
        User me = activeUser(userId);
        if (referrals.existsByInviteeId(userId)) {
            throw new ApiException(UserErrorCode.REFERRAL_ALREADY_ENTERED);
        }
        Instant now = Instant.now(clock);
        if (!now.isBefore(deadline(me))) {
            throw new ApiException(UserErrorCode.REFERRAL_EXPIRED);
        }
        User inviter = users.findByReferralCodeAndWithdrawnAtIsNull(code)
                .orElseThrow(() -> new ApiException(UserErrorCode.REFERRAL_CODE_NOT_FOUND));
        if (inviter.getId() == userId) {
            throw new ApiException(UserErrorCode.REFERRAL_SELF);
        }
        if (referrals.sharesDevice(inviter.getId(), userId, deviceHash)) {
            throw new ApiException(UserErrorCode.REFERRAL_SAME_DEVICE);
        }
        // 같은 사람이 동시에 두 번 보내면 한쪽만 들어간다
        if (referrals.insertIfAbsent(inviter.getId(), userId, deviceHash, now) == 0) {
            throw new ApiException(UserErrorCode.REFERRAL_ALREADY_ENTERED);
        }
        return new ReferralEntered(inviter.getNickname(), REWARD);
    }

    /** 대소문자를 가리지 않음 (코드는 대문자로 만든다) */
    private static String validCode(String raw) {
        String code = raw == null ? "" : raw.strip();
        if (code.isEmpty() || code.length() > CODE_MAX) {
            throw new ApiException(CommonErrorCode.INVALID_REQUEST);
        }
        return code.toUpperCase(Locale.ROOT);
    }

    // ---- 22 · 23 연결 끊기 때 보상

    /**
     * 연결 끊기 트랜잭션 안. 받은 사람 행을 잠가 같은 보상을 두 번 주지 않고, 초대한 사람 행을 잠가 10명 한도를 지킨다.
     * 크레딧은 user id가 작은 쪽부터 준다: 서로 코드를 넣은 두 사람이 동시에 끝내도 크레딧 잠금이 엇갈리지 않게
     */
    @Transactional
    public Optional<ReferralReward> qualify(long userId, long connectionId) {
        Optional<Referral> found = referrals.findForUpdateByInviteeId(userId).filter(r -> !r.isQualified());
        if (found.isEmpty()) return Optional.empty();
        Referral r = found.get();
        long inviterId = r.getInviterId();
        referrals.lockUser(inviterId);
        Optional<User> inviter = users.findById(inviterId).filter(User::isActive);
        // 확정 필요(36): 11번째부터 받은 사람에게도 안 줄지. 지금은 받은 사람 몫은 준다. 탈퇴한 초대한 사람에게는 주지 않는다
        boolean inviterReward = inviter.isPresent()
                && referrals.countByInviterIdAndInviterRewardedTrue(inviterId) < MAX_INVITER_REWARDS;
        r.qualify(connectionId, Instant.now(clock), inviterReward);
        long id = r.getId();
        // 34 title: 상대 닉네임을 지급할 때의 값으로 남긴다. 탈퇴한 초대한 사람이면 이름 없이
        String inviteeTitle = inviter.map(u -> inviteeTitle(u.getNickname())).orElse(CREDIT_TITLE);
        String inviterTitle =
                users.findById(userId).map(u -> inviterTitle(u.getNickname())).orElse(CREDIT_TITLE);
        if (inviterReward && inviterId < userId) {
            credits.grantInvite(inviterId, REWARD, id, true, inviterTitle);
            credits.grantInvite(userId, REWARD, id, false, inviteeTitle);
        } else {
            credits.grantInvite(userId, REWARD, id, false, inviteeTitle);
            if (inviterReward) credits.grantInvite(inviterId, REWARD, id, true, inviterTitle);
        }
        return Optional.of(new ReferralReward(REWARD));
    }

    @Transactional(readOnly = true)
    public Optional<ReferralReward> rewardOf(long userId, long connectionId) {
        return referrals
                .findByInviteeId(userId)
                .filter(r -> r.isInviteeRewarded() && Long.valueOf(connectionId).equals(r.getQualifiedConnectionId()))
                .map(r -> new ReferralReward(REWARD));
    }

    // ---- 도우미

    private User activeUser(long userId) {
        return users.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(CommonErrorCode.UNAUTHORIZED));
    }

    private static Instant deadline(User u) {
        return u.getCreatedAt().plus(INPUT_WINDOW);
    }
}
