package com.capsule.session.internal.adapter;

import com.capsule.content.api.CharacterQuery;
import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.content.api.ContentTypes.RoomInfo;
import com.capsule.content.api.RoomQuery;
import com.capsule.session.internal.repository.StudySessionRepository;
import com.capsule.user.api.ActiveSessionLookup;
import com.capsule.user.api.UserTypes.ActiveSessionSummary;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** user의 GET /api/me가 부른다. UserQuery에 의존하면 user와 빈 순환이 생기므로 content만 쓴다 */
@Service
class ActiveSessionLookupAdapter implements ActiveSessionLookup {

    private final StudySessionRepository sessions;
    private final RoomQuery rooms;
    private final CharacterQuery characters;

    ActiveSessionLookupAdapter(StudySessionRepository sessions, RoomQuery rooms, CharacterQuery characters) {
        this.sessions = sessions;
        this.rooms = rooms;
        this.characters = characters;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ActiveSessionSummary> find(long userId) {
        return sessions.findByUserIdAndEndedAtIsNull(userId)
                .map(s -> new ActiveSessionSummary(
                        s.id(),
                        s.roomId(),
                        rooms.find(s.roomId()).map(RoomInfo::name).orElse(null),
                        characters
                                .find(s.characterId())
                                .map(CharacterInfo::name)
                                .orElse(null),
                        s.startedAt()));
    }
}
