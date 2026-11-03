package com.capsule.content.api;

import com.capsule.content.api.ContentTypes.AvatarInfo;
import java.util.List;
import java.util.Optional;

public interface AvatarQuery {

    /** 고를 수 있는 아바타 (활성, 정렬 순서) */
    List<AvatarInfo> activeAvatars();

    /** 비활성 아바타도 찾음 (이미 고른 사용자의 프로필 표시용) */
    Optional<AvatarInfo> find(long avatarId);

    /** 아바타를 안 보냈을 때 쓸 기본 아바타 = 활성 중 정렬 첫 번째 */
    Optional<AvatarInfo> defaultAvatar();
}
