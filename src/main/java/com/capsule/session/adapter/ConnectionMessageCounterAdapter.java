package com.capsule.session.adapter;

import com.capsule.session.api.ConnectionMessageCounter;
import com.capsule.session.repository.StudySessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 부른 쪽(답장 저장) 트랜잭션에 같이 들어간다: 답장이 롤백되면 대화 수도 그대로 */
@Service
@Transactional
@RequiredArgsConstructor
public class ConnectionMessageCounterAdapter implements ConnectionMessageCounter {

    private final StudySessionRepository sessions;

    @Override
    public boolean countReply(long connectionId) {
        return sessions.incrementMessageCount(connectionId) == 1;
    }
}
