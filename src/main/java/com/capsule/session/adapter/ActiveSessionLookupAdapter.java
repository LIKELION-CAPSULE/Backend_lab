package com.capsule.session.adapter;

import com.capsule.content.api.CharacterQuery;
import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.session.repository.StudySessionRepository;
import com.capsule.user.api.ActiveSessionLookup;
import com.capsule.user.api.UserTypes.ActiveSessionSummary;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** user의 GET /api/me가 부른다. UserQuery에 의존하면 user와 빈 순환이 생기므로 content만 쓴다 */
@Service
@RequiredArgsConstructor
public class ActiveSessionLookupAdapter implements ActiveSessionLookup {

    private final StudySessionRepository sessions;
    private final CharacterQuery characters;

    @Override
    @Transactional(readOnly = true)
    public Optional<ActiveSessionSummary> find(long userId) {
        return sessions.findByUserIdAndEndedAtIsNull(userId)
                .map(s -> new ActiveSessionSummary(
                        s.getId(),
                        s.getCharacterId(),
                        characters
                                .find(s.getCharacterId())
                                .map(CharacterInfo::name)
                                .orElse(null),
                        s.getStartedAt()));
    }
}
