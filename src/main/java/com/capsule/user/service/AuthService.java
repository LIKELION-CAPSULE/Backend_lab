package com.capsule.user.service;

import com.capsule.common.error.ApiException;
import com.capsule.common.ratelimit.RateLimiter;
import com.capsule.user.client.EmailSender;
import com.capsule.user.client.KakaoClient;
import com.capsule.user.domain.User;
import com.capsule.user.dto.KakaoProfile;
import com.capsule.user.dto.KakaoRequest;
import com.capsule.user.dto.Login;
import com.capsule.user.dto.SignupRequest;
import com.capsule.user.dto.TermVersion;
import com.capsule.user.repository.TermsRepository;
import com.capsule.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 약관, 이메일 인증, 가입, 로그인, 카카오 (api.md 1~6). 토큰 발급은 {@link TokenService}. */
@Service
public class AuthService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration VERIFICATION_TTL = Duration.ofMinutes(10);
    private static final Duration LOGIN_LOCK = Duration.ofMinutes(5);
    private static final int MAX_FAILURES = 5;
    private static final int PASSWORD_MIN = 8;
    private static final int BCRYPT_MAX_BYTES = 72; // 넘으면 BCryptPasswordEncoder가 예외를 던진다
    private static final String KAKAO = "KAKAO";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final TermsRepository terms;
    private final StringRedisTemplate redis;
    private final RateLimiter rateLimiter;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final KakaoClient kakaoClient;
    private final TransactionTemplate tx;
    private final Clock clock;

    AuthService(
            UserRepository users,
            TermsRepository terms,
            StringRedisTemplate redis,
            RateLimiter rateLimiter,
            PasswordEncoder passwordEncoder,
            EmailSender emailSender,
            KakaoClient kakaoClient,
            TransactionTemplate tx,
            Clock clock) {
        this.users = users;
        this.terms = terms;
        this.redis = redis;
        this.rateLimiter = rateLimiter;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.kakaoClient = kakaoClient;
        this.tx = tx;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TermVersion> terms() {
        return terms.latestVersions(Instant.now(clock));
    }

    /** 6자리 번호를 5분 보관하고 메일로 보낸다. 돌려주는 값 = 유효 시간(초) */
    public int requestVerification(String rawEmail) {
        String email = normalize(rawEmail);
        if (!isEmail(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL");
        }
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_USED");
        }
        String rateKey = "rl:email-verification:" + email;
        if (!rateLimiter.tryAcquire(rateKey, 1, Duration.ofMinutes(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS");
        }
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        redis.opsForValue().set(codeKey(email), code, CODE_TTL);
        redis.delete(codeFailKey(email));
        try {
            emailSender.sendVerificationCode(email, code);
        } catch (RuntimeException e) {
            // 메일이 안 나갔는데 1분 동안 429가 나지 않게, 보내지 못한 번호와 제한을 지우고 500으로 넘긴다
            redis.delete(codeKey(email));
            rateLimiter.reset(rateKey);
            throw e;
        }
        return (int) CODE_TTL.toSeconds();
    }

    /** 맞으면 가입 토큰(10분, 값은 이메일). 5번째로 틀리면 번호를 버린다 */
    public String confirmVerification(String rawEmail, String code) {
        String email = normalize(rawEmail);
        String saved = redis.opsForValue().get(codeKey(email));
        if (saved == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CODE_EXPIRED");
        }
        if (!saved.equals(code)) {
            if (!rateLimiter.tryAcquire(codeFailKey(email), MAX_FAILURES - 1, CODE_TTL)) {
                redis.delete(List.of(codeKey(email), codeFailKey(email)));
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS");
            }
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CODE");
        }
        redis.delete(List.of(codeKey(email), codeFailKey(email)));
        String token = "vt_" + TokenService.randomToken();
        redis.opsForValue().set(verifiedKey(token), email, VERIFICATION_TTL);
        return token;
    }

    @Transactional
    public Login signup(SignupRequest req) {
        String verifiedKey = req.verificationToken() == null ? null : verifiedKey(req.verificationToken());
        String email = verifiedKey == null ? null : redis.opsForValue().get(verifiedKey);
        if (email == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VERIFICATION_EXPIRED");
        }
        String password = req.password();
        requireValidPassword(password);
        String nickname = User.validNickname(req.nickname());
        Instant now = Instant.now(clock);
        List<Long> agreed = agreedLatestTerms(req.agreedTermVersionIds(), now);
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_USED");
        }
        User user;
        try {
            // 가입 버튼 연타처럼 같은 이메일이 동시에 오면 둘 다 위 확인을 지나고, 늦은 쪽은 users.email UNIQUE에 막힌다.
            // flush로 여기서 바로 부딪히게 해 500 대신 409로 (api.md 4, #40)
            user = users.saveAndFlush(User.emailUser(email, passwordEncoder.encode(password), nickname, now));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_USED");
        }
        terms.recordAgreements(user.id(), agreed);
        redis.delete(verifiedKey);
        return new Login(user.id(), true, true);
    }

    /** 없는 이메일·틀린 비밀번호·탈퇴는 구분하지 않는다. 5번 틀리면 5분 잠금 */
    @Transactional
    public Login login(String rawEmail, String password) {
        String email = normalize(rawEmail);
        String lockKey = loginLockKeys(email).get(0);
        String failKey = loginLockKeys(email).get(1);
        if (Boolean.TRUE.equals(redis.hasKey(lockKey))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_LOCKED");
        }
        Optional<User> user = users.findByEmail(email)
                .filter(u -> u.isActive()
                        && u.passwordHash() != null
                        && password != null
                        && password.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES
                        && passwordEncoder.matches(password, u.passwordHash()));
        if (user.isEmpty()) {
            // ponytail: 실패 횟수 창 = 잠금 시간(5분). 명세에 창 길이가 없음
            if (!rateLimiter.tryAcquire(failKey, MAX_FAILURES - 1, LOGIN_LOCK)) {
                rateLimiter.reset(failKey);
                redis.opsForValue().set(lockKey, "1", LOGIN_LOCK);
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_LOCKED");
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }
        rateLimiter.reset(failKey);
        user.get().loggedIn(Instant.now(clock));
        return new Login(user.get().id(), user.get().onboardingRequired(), false);
    }

    /**
     * 처음이면 users + user_social_accounts + user_terms를 한 트랜잭션으로 (api.md 6 '서버가 하는 일').
     * 약관 체크는 받지 않는다. 카카오로 가입하면 가입 시점의 최신 필수 약관 버전에 동의한 것으로 기록한다.
     * 카카오 호출(네트워크 왕복 2번)은 트랜잭션 밖에서 한다. 카카오가 느려도 DB 커넥션을 붙잡지 않게
     */
    public Login kakao(KakaoRequest req) {
        KakaoProfile profile = kakaoClient
                .fetchProfile(req.code(), req.redirectUri())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "SOCIAL_AUTH_FAILED"));
        return tx.execute(status -> loginOrSignUp(profile));
    }

    private Login loginOrSignUp(KakaoProfile profile) {
        Instant now = Instant.now(clock);
        Optional<Long> existingId = users.findUserIdBySocialAccount(KAKAO, profile.providerUserId());
        if (existingId.isPresent()) {
            User user = users.findById(existingId.get())
                    .filter(User::isActive)
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "SOCIAL_AUTH_FAILED"));
            user.loggedIn(now);
            return new Login(user.id(), user.onboardingRequired(), false);
        }
        String nickname = User.fitNickname(profile.nickname());
        if (nickname.isEmpty()) {
            // 닉네임 동의는 필수 항목(api.md 6)이지만 비어 올 수 있어 NOT NULL을 지키려고 둔 기본값. 프로필 설정에서 바꿀 수 있다
            nickname = "카카오 사용자";
        }
        User user = users.save(User.socialUser(nickname, now));
        users.insertSocialAccount(KAKAO, profile.providerUserId(), user.id());
        terms.recordAgreements(
                user.id(),
                terms.latestVersions(now).stream()
                        .filter(TermVersion::required)
                        .map(TermVersion::termVersionId)
                        .toList());
        return new Login(user.id(), true, true);
    }

    /** 필수 약관마다 최신 시행 버전에 동의했는지 본다. 기록할 id = 보낸 것 중 지금 최신 버전인 것 */
    private List<Long> agreedLatestTerms(List<Long> agreedIds, Instant now) {
        Set<Long> agreed = agreedIds == null ? Set.of() : new HashSet<>(agreedIds);
        List<TermVersion> latest = terms.latestVersions(now);
        // 필수 약관이 하나도 없으면(약관 데이터를 아직 안 넣은 DB) 동의 없이 가입되지 않게 막는다
        if (latest.stream().noneMatch(TermVersion::required)
                || latest.stream().anyMatch(t -> t.required() && !agreed.contains(t.termVersionId()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TERMS_REQUIRED");
        }
        return latest.stream()
                .map(TermVersion::termVersionId)
                .filter(agreed::contains)
                .toList();
    }

    /** 가입 · 비밀번호 재설정 공통: 8자 이상, 72바이트 이하(bcrypt 한계) */
    static void requireValidPassword(String password) {
        if (password == null
                || password.length() < PASSWORD_MIN
                || password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD");
        }
    }

    static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    static boolean isEmail(String normalizedEmail) {
        return EMAIL.matcher(normalizedEmail).matches();
    }

    /** 로그인 5회 실패 잠금 키 (비밀번호를 바꾸면 푼다) */
    static List<String> loginLockKeys(String normalizedEmail) {
        return List.of("auth:login-lock:" + normalizedEmail, "auth:login-fail:" + normalizedEmail);
    }

    private static String codeKey(String email) {
        return "auth:email-code:" + email;
    }

    private static String codeFailKey(String email) {
        return "auth:email-code-fail:" + email;
    }

    private static String verifiedKey(String token) {
        return "auth:verified:" + token;
    }
}
