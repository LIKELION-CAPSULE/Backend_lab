package com.capsule.content.adapter;

import com.capsule.content.api.AvatarQuery;
import com.capsule.content.api.ContentTypes.AvatarInfo;
import com.capsule.content.repository.AvatarRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 계약마다 빈을 따로 둔다: 한 빈이 여러 계약을 구현하면 다른 모듈 테스트의 @MockitoBean이 나머지 계약까지 지움
@Service
@Transactional(readOnly = true)
class AvatarQueryAdapter implements AvatarQuery {

    private final AvatarRepository avatars;
    private final ContentMapper mapper;

    AvatarQueryAdapter(AvatarRepository avatars, ContentMapper mapper) {
        this.avatars = avatars;
        this.mapper = mapper;
    }

    @Override
    public List<AvatarInfo> activeAvatars() {
        return avatars.findByIsActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(mapper::avatar)
                .toList();
    }

    @Override
    public Optional<AvatarInfo> find(long avatarId) {
        return avatars.findById(avatarId).map(mapper::avatar);
    }

    @Override
    public Optional<AvatarInfo> defaultAvatar() {
        return activeAvatars().stream().findFirst();
    }
}
