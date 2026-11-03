package com.capsule.user.api;

import com.capsule.user.api.UserTypes.UserProfile;
import java.util.Optional;

public interface UserQuery {

    /** 탈퇴한 사용자는 빈 값 */
    Optional<UserProfile> find(long userId);
}
