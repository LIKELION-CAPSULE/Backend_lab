package com.capsule.content.adapter;

import com.capsule.content.api.ContentTypes.BackgroundInfo;
import com.capsule.content.api.ContentTypes.RoomInfo;
import com.capsule.content.api.RoomQuery;
import com.capsule.content.domain.RoomBackground;
import com.capsule.content.domain.StudyRoom;
import com.capsule.content.repository.CharacterRepository;
import com.capsule.content.repository.RoomBackgroundRepository;
import com.capsule.content.repository.StudyRoomRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RoomQueryAdapter implements RoomQuery {

    private final StudyRoomRepository rooms;
    private final RoomBackgroundRepository backgrounds;
    private final CharacterRepository characters;
    private final ContentMapper mapper;

    @Override
    public List<RoomInfo> activeRooms() {
        return rooms.findByIsActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(this::info)
                .toList();
    }

    @Override
    public Optional<RoomInfo> find(long roomId) {
        return rooms.findById(roomId).map(this::info);
    }

    @Override
    public Optional<BackgroundInfo> defaultBackground(long roomId) {
        return backgrounds.findByRoomIdAndIsDefaultTrue(roomId).map(this::info);
    }

    @Override
    public Optional<BackgroundInfo> background(long backgroundId) {
        return backgrounds.findById(backgroundId).map(this::info);
    }

    private RoomInfo info(StudyRoom r) {
        var roomCharacters = characters.findByRoomIdAndIsActiveTrueOrderBySortOrderAscIdAsc(r.getId()).stream()
                .map(mapper::character)
                .toList();
        return new RoomInfo(
                r.getId(),
                r.getName(),
                r.getDescription(),
                mapper.asset(r.getThumbnailUrl()),
                r.isActive(),
                roomCharacters,
                mapper.asset(r.getBannerUrl()));
    }

    private BackgroundInfo info(RoomBackground b) {
        return new BackgroundInfo(b.getId(), b.getRoomId(), b.getName(), mapper.asset(b.getImageUrl()));
    }
}
