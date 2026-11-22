package com.capsule.dialogue.service;

import com.capsule.dialogue.repository.SafetyStrikeRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 안전 규칙 (api.md 20 · 28 '안전', 2026-11-23): 판정(SafetyChecker) · 위반 기록 · 대화 정지.
 * window 안에 위반이 strike-limit번이면 그 마지막 위반부터 suspension 동안 20 · 28이 403 CHAT_SUSPENDED
 */
// TODO(judgment): J-17 ② 횟수 · 기간(지금 24시간 안 3번 → 24시간), 반복 시 늘릴지, ③ 거절 대사 문구 (확정 필요)
@Component
@RequiredArgsConstructor
public class SafetyGuard {

    private final SafetyChecker checker;
    private final SafetyStrikeRepository strikes;
    private final Clock clock;

    @Value("${capsule.dialogue.safety.strike-limit:3}")
    private final int strikeLimit;

    @Value("${capsule.dialogue.safety.window:PT24H}")
    private final Duration window;

    @Value("${capsule.dialogue.safety.suspension:PT24H}")
    private final Duration suspension;

    @Value("${capsule.dialogue.safety.refusal-line:그런 얘기는 하지 않을래. 우리 다른 얘기 하자.}")
    private final String refusalLine;

    /** 위반이면 종류, 아니면 빈 값 */
    public Optional<String> violation(String userMessage) {
        return checker.violation(userMessage);
    }

    /** 위반 턴의 정해진 거절 대사 */
    public String refusalLine() {
        return refusalLine;
    }

    /** 위반 기록 (답장 저장 트랜잭션 안에서). 같은 요청은 한 번만 */
    public void record(long userId, long connectionId, UUID requestId, String category) {
        strikes.record(userId, connectionId, requestId, category, Instant.now(clock));
    }

    /** 이 메시지들 중 위반 턴(사용자 말 · 거절 대사)이었던 요청 */
    public Set<UUID> struckAmong(long userId, Collection<UUID> requestIds) {
        return requestIds.isEmpty() ? Set.of() : strikes.struckAmong(userId, requestIds);
    }

    /** 이 요청이 위반 턴이었는지 (재전송 응답의 safety) */
    public boolean struck(long userId, UUID requestId) {
        return strikes.existsByUserIdAndRequestId(userId, requestId);
    }

    /**
     * 지금 대화 정지 중이면 풀리는 시각. 정지는 window 안 strike-limit번째 위반부터 suspension 동안.
     * 최근 위반부터 거꾸로 보며, 그 위반까지 window 안에 strike-limit번이 쌓였고 아직 안 풀렸으면 정지
     */
    public Optional<Instant> suspendedUntil(long userId) {
        Instant now = Instant.now(clock);
        List<Instant> times = strikes.timesSince(userId, now.minus(window).minus(suspension));
        for (int i = times.size() - 1; i >= 0; i--) {
            Instant last = times.get(i);
            Instant until = last.plus(suspension);
            if (!until.isAfter(now)) break; // 더 오래된 위반은 더 일찍 풀렸다
            Instant from = last.minus(window);
            long inWindow = times.subList(0, i + 1).stream()
                    .filter(t -> t.isAfter(from))
                    .count();
            if (inWindow >= strikeLimit) return Optional.of(until);
        }
        return Optional.empty();
    }
}
