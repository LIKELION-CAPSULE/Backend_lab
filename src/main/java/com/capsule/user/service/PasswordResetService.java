package com.capsule.user.service;

import com.capsule.common.error.ApiException;
import com.capsule.common.ratelimit.RateLimiter;
import com.capsule.user.client.EmailSender;
import com.capsule.user.domain.User;
import com.capsule.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 비밀번호 찾기: 메일 링크 방식 (api.md 25 · 26) */
@Service
public class PasswordResetService {

    /** 링크 유효시간. api.md 25 '예시 · 확정 필요' */
    private static final Duration RESET_TTL = Duration.ofMinutes(30);

    private static final String RESET_PATH = "/password/reset?token="; // 프론트 페이지 경로, api.md 25 '확정 필요'

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository users;
    private final StringRedisTemplate redis;
    private final RateLimiter rateLimiter;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final TokenService tokens;
    private final Clock clock;
    private final String frontendUrl;
    private final Executor mailExecutor;

    public PasswordResetService(
            UserRepository users,
            StringRedisTemplate redis,
            RateLimiter rateLimiter,
            PasswordEncoder passwordEncoder,
            EmailSender emailSender,
            TokenService tokens,
            Clock clock,
            @Value("${capsule.frontend.url:http://localhost:3000}") String frontendUrl) {
        this.users = users;
        this.redis = redis;
        this.rateLimiter = rateLimiter;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.tokens = tokens;
        this.clock = clock;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.mailExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 비밀번호가 있는 활성 계정이면 링크를 보낸다. 없는 이메일 · 카카오 전용 · 탈퇴 계정도 같은 결과(아무 일 없음)라
     * 응답으로 가입 여부를 알 수 없다 (api.md 25)
     */
    public void requestReset(String rawEmail) {
        String email = AuthService.normalize(rawEmail);
        if (!AuthService.isEmail(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL");
        }
        String rateKey = "rl:password-reset:" + email;
        if (!rateLimiter.tryAcquire(rateKey, 1, Duration.ofMinutes(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS");
        }
        Optional<User> user = users.findByEmail(email).filter(u -> u.isActive() && u.passwordHash() != null);
        if (user.isEmpty()) {
            return;
        }
        long userId = user.get().getId();
        String previous = redis.opsForValue().get(userTokenKey(userId));
        if (previous != null) {
            redis.delete(tokenKey(previous)); // 마지막 링크만 유효
        }
        String token = "pr_" + TokenService.randomToken();
        redis.opsForValue().set(tokenKey(token), Long.toString(userId), RESET_TTL);
        redis.opsForValue().set(userTokenKey(userId), token, RESET_TTL);
        String link = frontendUrl + RESET_PATH + token;
        // 메일은 응답과 따로 보낸다. 같이 보내면 응답 시간 · 발송 실패(500)로 가입 여부가 드러난다 (api.md 25 '서버가 하는 일 5')
        mailExecutor.execute(() -> {
            try {
                emailSender.sendPasswordResetLink(email, link);
            } catch (RuntimeException e) {
                redis.delete(List.of(tokenKey(token), userTokenKey(userId))); // 보내지 못한 링크는 무효로
                log.warn("비밀번호 재설정 메일 발송 실패 ({})", e.getClass().getSimpleName()); // 주소 · 링크는 남기지 않는다
            }
        });
    }

    /**
     * 토큰은 한 번만 쓴다(GETDEL). 바꾸면 로그인 잠금을 풀고 모든 기기에서 로그아웃 (api.md 26).
     * 잠금 해제 · 로그아웃은 DB 커밋이 된 뒤에만 한다. 커밋이 실패하면 비밀번호는 그대로이므로 링크도 되살린다
     */
    @Transactional
    public void reset(String token, String newPassword) {
        AuthService.requireValidPassword(newPassword); // 비밀번호가 틀리면 토큰을 쓰지 않은 채로 400
        String userId = token == null ? null : redis.opsForValue().getAndDelete(tokenKey(token));
        if (userId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RESET_TOKEN_INVALID");
        }
        User user = users.findById(Long.parseLong(userId))
                .filter(u -> u.isActive() && u.passwordHash() != null)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "RESET_TOKEN_INVALID"));
        user.changePassword(passwordEncoder.encode(newPassword), Instant.now(clock));
        long id = user.getId();
        String email = user.getEmail();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redis.delete(userTokenKey(id));
                redis.delete(AuthService.loginLockKeys(email));
                tokens.revokeAll(id);
            }

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    redis.opsForValue().set(tokenKey(token), userId, RESET_TTL);
                }
            }
        });
    }

    private static String tokenKey(String token) {
        return "auth:password-reset:" + token;
    }

    private static String userTokenKey(long userId) {
        return "auth:password-reset-user:" + userId;
    }
}
