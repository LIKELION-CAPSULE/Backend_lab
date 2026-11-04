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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class RoomQueryAdapter implements RoomQuery {

    private final StudyRoomRepository rooms;
    private final RoomBackgroundRepository backgrounds;
    private final CharacterRepository characters;
    private final ContentMapper mapper;

    RoomQueryAdapter(
            StudyRoomRepository rooms,
            RoomBackgroundRepository backgrounds,
            CharacterRepository characters,
            ContentMapper mapper) {
        this.rooms = rooms;
        this.backgrounds = backgrounds;
        this.characters = characters;
        this.mapper = mapper;
    }

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
        var roomCharacters = characters.findByRoomIdAndIsActiveTrueOrderBySortOrderAscIdAsc(r.id()).stream()
                .map(mapper::character)
                .toList();
        return new RoomInfo(
                r.id(),
                r.name(),
                r.description(),
                mapper.asset(r.thumbnailUrl()),
                r.isActive(),
                roomCharacters,
                mapper.asset(r.bannerUrl()));
    }

    private BackgroundInfo info(RoomBackground b) {
        return new BackgroundInfo(b.id(), b.roomId(), b.name(), mapper.asset(b.imageUrl()));
    }
}
