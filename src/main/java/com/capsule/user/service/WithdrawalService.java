package com.capsule.user.service;

import com.capsule.common.error.ApiException;
import com.capsule.user.api.ActiveSessionLookup;
import com.capsule.user.api.UserWithdrawnEvent;
import com.capsule.user.domain.User;
import com.capsule.user.repository.UserMemoryRepository;
import com.capsule.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 회원 탈퇴 (api.md 27). DB는 한 트랜잭션, 리프레시 토큰 폐기는 커밋 뒤 */
@Service
public class WithdrawalService {

    private final UserRepository users;
    private final UserMemoryRepository memories;
    private final ActiveSessionLookup activeSessions;
    private final ApplicationEventPublisher events;
    private final TokenService tokens;
    private final TransactionTemplate tx;
    private final Clock clock;

    WithdrawalService(
            UserRepository users,
            UserMemoryRepository memories,
            ActiveSessionLookup activeSessions,
            ApplicationEventPublisher events,
            TokenService tokens,
            TransactionTemplate tx,
            Clock clock) {
        this.users = users;
        this.memories = memories;
        this.activeSessions = activeSessions;
        this.events = events;
        this.tokens = tokens;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * 이미 탈퇴한 사용자면 DB는 그대로 두고 토큰 폐기만 다시 한다 (명세: 그래도 204).
     * 커밋 뒤 Redis 폐기가 실패해 500이 났어도 다시 부르면 폐기된다
     */
    public void withdraw(long userId) {
        tx.executeWithoutResult(status -> {
            User user = users.findById(userId).filter(User::isActive).orElse(null);
            if (user == null) {
                return;
            }
            // 세션이 남으면 자동 마감이 탈퇴 뒤에 정산 · 대화 카드를 다시 만든다
            if (activeSessions.find(userId).isPresent()) {
                throw new ApiException(HttpStatus.CONFLICT, "SESSION_IN_PROGRESS");
            }
            user.withdraw(Instant.now(clock));
            users.deleteSocialAccounts(userId);
            memories.deleteByUserId(userId);
            events.publishEvent(new UserWithdrawnEvent(userId)); // dialogue가 대화 기억을 같은 트랜잭션에서 지움
        });
        tokens.revokeAll(userId);
    }
}
