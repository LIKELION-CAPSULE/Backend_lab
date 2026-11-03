package com.capsule.user.internal;

import com.capsule.user.api.UserQuery;
import com.capsule.user.api.UserTypes.UserProfile;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * UserQuery 계약 구현. 계약마다 얇은 클래스로 따로 둔다:
 * 다른 모듈 테스트가 이 계약을 @MockitoBean으로 바꿔도 컨트롤러가 쓰는 ProfileService는 그대로 남게.
 */
@Component
class UserQueryAdapter implements UserQuery {

    private final ProfileService profiles;

    UserQueryAdapter(ProfileService profiles) {
        this.profiles = profiles;
    }

    @Override
    public Optional<UserProfile> find(long userId) {
        return profiles.find(userId);
    }
}
