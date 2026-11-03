package com.capsule.user.service;

import com.capsule.common.error.ApiException;
import com.capsule.content.api.AvatarQuery;
import com.capsule.content.api.ContentTypes.AvatarInfo;
import com.capsule.user.api.ActiveSessionLookup;
import com.capsule.user.api.UserTypes.UserProfile;
import com.capsule.user.domain.User;
import com.capsule.user.dto.MeResponse;
import com.capsule.user.dto.UpdateMeRequest;
import com.capsule.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 정보 조회·프로필 설정 (api.md 8, 10). UserQuery 계약은
 * {@link com.capsule.user.adapter.UserQueryAdapter}가 이 클래스로 위임
 */
@Service
public class ProfileService {

    private final UserRepository users;
    private final AvatarQuery avatars;
    private final ActiveSessionLookup activeSessions;
    private final Clock clock;

    ProfileService(UserRepository users, AvatarQuery avatars, ActiveSessionLookup activeSessions, Clock clock) {
        this.users = users;
        this.avatars = avatars;
        this.activeSessions = activeSessions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MeResponse me(long userId) {
        User user = activeUser(userId);
        return toMe(user, thumbnail(user));
    }

    /** 아바타를 안 보내면 기본 아바타 (H02 '나중에 바꿀게요') */
    @Transactional
    public MeResponse update(long userId, UpdateMeRequest req) {
        User user = activeUser(userId);
        String nickname = User.validNickname(req.nickname());
        AvatarInfo avatar = (req.avatarId() == null ? avatars.defaultAvatar() : avatars.find(req.avatarId()))
                .filter(AvatarInfo::active)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "AVATAR_NOT_FOUND"));
        user.updateProfile(nickname, avatar.id(), Instant.now(clock));
        return toMe(user, avatar.thumbnailUrl());
    }

    @Transactional(readOnly = true)
    public Optional<UserProfile> find(long userId) {
        return users.findById(userId)
                .filter(User::isActive)
                .map(u -> new UserProfile(
                        u.id(), u.nickname(), u.email(), u.avatarId(), thumbnail(u), u.onboardingRequired()));
    }

    private MeResponse toMe(User user, String profileImage) {
        return new MeResponse(
                user.id(),
                user.nickname(),
                user.email(),
                user.onboardingRequired(),
                activeSessions.find(user.id()).orElse(null),
                profileImage);
    }

    private String thumbnail(User user) {
        return user.avatarId() == null
                ? null
                : avatars.find(user.avatarId()).map(AvatarInfo::thumbnailUrl).orElse(null);
    }

    /** 토큰은 맞는데 탈퇴했거나 없는 사용자 */
    private User activeUser(long userId) {
        return users.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED"));
    }
}
