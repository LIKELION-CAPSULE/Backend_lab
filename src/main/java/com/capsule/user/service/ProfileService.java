package com.capsule.user.service;

import com.capsule.common.error.ApiException;
import com.capsule.common.error.CommonErrorCode;
import com.capsule.content.api.AvatarQuery;
import com.capsule.content.api.ContentTypes.AvatarInfo;
import com.capsule.credit.api.CreditQuery;
import com.capsule.user.api.ActiveSessionLookup;
import com.capsule.user.api.UserTypes.UserProfile;
import com.capsule.user.domain.Age;
import com.capsule.user.domain.User;
import com.capsule.user.domain.UserErrorCode;
import com.capsule.user.dto.MeResponse;
import com.capsule.user.dto.UpdateMeRequest;
import com.capsule.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 정보 조회·프로필 설정 (api.md 8, 10). UserQuery 계약은
 * {@link com.capsule.user.adapter.UserQueryAdapter}가 이 클래스로 위임
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository users;
    private final AvatarQuery avatars;
    private final ActiveSessionLookup activeSessions;
    private final CreditQuery credits;
    private final Clock clock;

    /** 쓰기 트랜잭션: 크레딧이 그날 처음이면 출석분을 지급한다 (api.md 8 · 33) */
    @Transactional
    public MeResponse me(long userId) {
        User user = activeUser(userId);
        return toMe(user, thumbnail(user));
    }

    /**
     * 보낸 값만 바꾼다 (api.md 10). H02는 아바타만, H14는 바꾼 것만 보낸다.
     * 아바타를 안 보냈는데 아직 아바타가 없으면 기본 아바타 (H02 '나중에 바꿀게요')
     */
    @Transactional
    public MeResponse update(long userId, UpdateMeRequest req) {
        User user = activeUser(userId);
        String nickname = req.nickname() == null ? user.getNickname() : User.validNickname(req.nickname());
        // 칸 검사를 먼저 하고 바꾼다. 아래 아바타 검사가 실패해도 트랜잭션이 롤백돼 아무것도 저장되지 않는다
        LocalDate birthDate = req.birthDate() == null || user.getBirthDate() != null
                ? null
                : Age.validBirthDate(req.birthDate(), Age.today(clock));
        String honorific = req.honorific() == null ? null : User.validHonorific(req.honorific());
        String aboutMe = req.aboutMe() == null ? null : User.validAboutMe(req.aboutMe());
        Instant now = Instant.now(clock);
        if (birthDate != null) user.fillBirthDate(birthDate, now);
        if (req.honorific() != null) user.updateHonorific(honorific, now);
        if (req.aboutMe() != null) user.updateAboutMe(aboutMe, now);
        if (req.avatarId() == null && user.getAvatarId() != null) {
            user.updateProfile(nickname, user.getAvatarId(), Instant.now(clock));
            return toMe(user, thumbnail(user));
        }
        AvatarInfo avatar = (req.avatarId() == null ? avatars.defaultAvatar() : avatars.find(req.avatarId()))
                .filter(AvatarInfo::active)
                .orElseThrow(() -> new ApiException(UserErrorCode.AVATAR_NOT_FOUND));
        user.updateProfile(nickname, avatar.id(), Instant.now(clock));
        return toMe(user, avatar.thumbnailUrl());
    }

    @Transactional(readOnly = true)
    public Optional<UserProfile> find(long userId) {
        return users.findById(userId)
                .filter(User::isActive)
                .map(u -> new UserProfile(
                        u.getId(),
                        u.getNickname(),
                        u.getEmail(),
                        u.getAvatarId(),
                        thumbnail(u),
                        u.onboardingRequired(),
                        u.getHonorific(),
                        u.getAboutMe(),
                        u.isMinor(Age.today(clock))));
    }

    private MeResponse toMe(User user, String profileImage) {
        return new MeResponse(
                user.getId(),
                user.getNickname(),
                user.getEmail(),
                user.onboardingRequired(),
                activeSessions.find(user.getId()).orElse(null),
                profileImage,
                user.getBirthDate(),
                user.isMinor(Age.today(clock)),
                user.getHonorific(),
                user.getAboutMe(),
                user.getReferralCode(),
                credits.current(user.getId()),
                // 비밀번호가 없으면 카카오 가입자 (소셜은 카카오만, api.md 6)
                user.passwordHash() == null ? MeResponse.KAKAO : MeResponse.EMAIL);
    }

    private String thumbnail(User user) {
        return user.getAvatarId() == null
                ? null
                : avatars.find(user.getAvatarId()).map(AvatarInfo::thumbnailUrl).orElse(null);
    }

    /** 토큰은 맞는데 탈퇴했거나 없는 사용자 */
    private User activeUser(long userId) {
        return users.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(CommonErrorCode.UNAUTHORIZED));
    }
}
