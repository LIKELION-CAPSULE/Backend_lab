package com.capsule.session.adapter;

import com.capsule.session.api.ConnectionBackgrounds;
import com.capsule.session.repository.StudySessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ConnectionBackgroundsAdapter implements ConnectionBackgrounds {

    private final StudySessionRepository sessions;

    @Override
    @Transactional(readOnly = true)
    public String current(long connectionId) {
        return sessions.findBackgroundCode(connectionId).orElse(null);
    }

    @Override
    public boolean change(long connectionId, String code) {
        return sessions.updateBackground(connectionId, code) == 1;
    }
}
