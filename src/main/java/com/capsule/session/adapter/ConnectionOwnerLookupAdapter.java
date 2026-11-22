package com.capsule.session.adapter;

import com.capsule.session.repository.StudySessionRepository;
import com.capsule.user.api.ConnectionOwnerLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** user.api.ConnectionOwnerLookup 구현 (api.md 42 베타 피드백의 connectionId 확인) */
@Component
@RequiredArgsConstructor
public class ConnectionOwnerLookupAdapter implements ConnectionOwnerLookup {

    private final StudySessionRepository sessions;

    @Override
    public boolean owns(long userId, long connectionId) {
        return sessions.findById(connectionId).map(s -> s.getUserId() == userId).orElse(false);
    }
}
