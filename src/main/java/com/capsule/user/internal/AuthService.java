package com.capsule.user.internal;

import com.capsule.common.error.ApiException;
import com.capsule.common.ratelimit.RateLimiter;
import com.capsule.user.internal.TermsRepository.TermVersion;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 약관, 이메일 인증, 가입, 로그인, 카카오 (api.md 1~6). 토큰 발급은 {@link TokenService}. */
@Service
class AuthService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration VERIFICATION_TTL = Duration.ofMinutes(10);
    private static final Duration LOGIN_LOCK = Duration.ofMinutes(5);
    private static final int MAX_FAILURES = 5;
    private static final int PASSWORD_MIN = 8;
    private static final int BCRYPT_MAX_BYTES = 72; // 넘으면 BCryptPasswordEncoder가 예외를 던진다
    private static final String KAKAO = "KAKAO";
    private static final SecureRandom RANDOM = new SecureRandom();

    record SignupRequest(String verificationToken, String password, String nickname, List<Long> agreedTermVersionIds) {}

    record KakaoRequest(String code, String redirectUri, List<Long> agreedTermVersionIds) {}

    record Login(long userId, boolean onboardingRequired, boolean newUser) {}

    private final UserRepository users;
    private final TermsRepository terms;
    private final StringRedisTemplate redis;
    private final RateLimiter rateLimiter;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final KakaoClient kakaoClient;
    private final Clock clock;

    AuthService(
            UserRepository users,
            TermsRepository terms,
            StringRedisTemplate redis,
            RateLimiter rateLimiter,
            PasswordEncoder passwordEncoder,
            EmailSender emailSender,
            KakaoClient kakaoClient,
            Clock clock) {
        this.users = users;
        this.terms = terms;
        this.redis = redis;
        this.rateLimiter = rateLimiter;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.kakaoClient = kakaoClient;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    List<TermVersion> terms() {
        return terms.latestVersions(Instant.now(clock));
    }

    /** 6자리 번호를 5분 보관하고 메일로 보낸다. 돌려주는 값 = 유효 시간(초) */
    int requestVerification(String rawEmail) {
        String email = normalize(rawEmail);
        if (!EMAIL.matcher(email).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL");
        }
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_USED");
        }
        if (!rateLimiter.tryAcquire("rl:email-verification:" + email, 1, Duration.ofMinutes(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS");
        }
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        redis.opsForValue().set(codeKey(email), code, CODE_TTL);
        redis.delete(codeFailKey(email));
        emailSender.sendVerificationCode(email, code);
        return (int) CODE_TTL.toSeconds();
    }

    /** 맞으면 가입 토큰(10분, 값은 이메일). 5번째로 틀리면 번호를 버린다 */
    String confirmVerification(String rawEmail, String code) {
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
    Login signup(SignupRequest req) {
        String verifiedKey = req.verificationToken() == null ? null : verifiedKey(req.verificationToken());
        String email = verifiedKey == null ? null : redis.opsForValue().get(verifiedKey);
        if (email == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VERIFICATION_EXPIRED");
        }
        String password = req.password();
        if (password == null
                || password.length() < PASSWORD_MIN
                || password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD");
        }
        String nickname = User.validNickname(req.nickname());
        Instant now = Instant.now(clock);
        List<Long> agreed = agreedLatestTerms(req.agreedTermVersionIds(), now);
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_USED");
        }
        User user = users.save(User.emailUser(email, passwordEncoder.encode(password), nickname, now));
        terms.recordAgreements(user.id(), agreed);
        redis.delete(verifiedKey);
        return new Login(user.id(), true, true);
    }

    /** 없는 이메일·틀린 비밀번호·탈퇴는 구분하지 않는다. 5번 틀리면 5분 잠금 */
    @Transactional
    Login login(String rawEmail, String password) {
        String email = normalize(rawEmail);
        String lockKey = "auth:login-lock:" + email;
        String failKey = "auth:login-fail:" + email;
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

    /** 처음이면 users + user_social_accounts + user_terms를 한 트랜잭션으로 (api.md 6 '서버가 하는 일') */
    @Transactional
    Login kakao(KakaoRequest req) {
        KakaoClient.KakaoProfile profile = kakaoClient
                .fetchProfile(req.code(), req.redirectUri())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "SOCIAL_AUTH_FAILED"));
        Instant now = Instant.now(clock);
        Optional<Long> existingId = users.findUserIdBySocialAccount(KAKAO, profile.providerUserId());
        if (existingId.isPresent()) {
            User user = users.findById(existingId.get())
                    .filter(User::isActive)
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "SOCIAL_AUTH_FAILED"));
            user.loggedIn(now);
            return new Login(user.id(), user.onboardingRequired(), false);
        }
        List<Long> agreed = agreedLatestTerms(req.agreedTermVersionIds(), now);
        String nickname = User.fitNickname(profile.nickname());
        if (nickname.isEmpty()) {
            // 닉네임 동의는 필수 항목(api.md 6)이지만 비어 올 수 있어 NOT NULL을 지키려고 둔 기본값. 프로필 설정에서 바꿀 수 있다
            nickname = "카카오 사용자";
        }
        User user = users.save(User.socialUser(nickname, now));
        users.insertSocialAccount(KAKAO, profile.providerUserId(), user.id());
        terms.recordAgreements(user.id(), agreed);
        return new Login(user.id(), true, true);
    }

    /** 필수 약관마다 최신 시행 버전에 동의했는지 본다. 기록할 id = 보낸 것 중 지금 최신 버전인 것 */
    private List<Long> agreedLatestTerms(List<Long> agreedIds, Instant now) {
        Set<Long> agreed = agreedIds == null ? Set.of() : new HashSet<>(agreedIds);
        List<TermVersion> latest = terms.latestVersions(now);
        if (latest.stream().anyMatch(t -> t.required() && !agreed.contains(t.termVersionId()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TERMS_REQUIRED");
        }
        return latest.stream()
                .map(TermVersion::termVersionId)
                .filter(agreed::contains)
                .toList();
    }

    private static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
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
