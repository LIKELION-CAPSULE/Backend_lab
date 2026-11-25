package com.capsule.user.service;

import com.capsule.common.error.ApiException;
import com.capsule.common.error.CommonErrorCode;
import com.capsule.common.ratelimit.RateLimiter;
import com.capsule.user.client.EmailSender;
import com.capsule.user.client.KakaoClient;
import com.capsule.user.domain.Age;
import com.capsule.user.domain.User;
import com.capsule.user.domain.UserErrorCode;
import com.capsule.user.dto.KakaoProfile;
import com.capsule.user.dto.KakaoRequest;
import com.capsule.user.dto.KakaoSignupRequest;
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
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 약관, 이메일 인증, 가입, 로그인, 카카오 (api.md 1~6, 49). 토큰 발급은 {@link TokenService}. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration VERIFICATION_TTL = Duration.ofMinutes(10);
    private static final Duration KAKAO_SIGNUP_TTL = Duration.ofMinutes(10); // 카카오 가입 토큰 (api.md 6 · 49)
    private static final Duration LOGIN_LOCK = Duration.ofMinutes(10); // 2026-11-23 5 → 10분 (피그마 SCR-51 AU-1 초안)
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
    private final SignupPolicy signupPolicy;

    @Transactional(readOnly = true)
    public List<TermVersion> terms() {
        return terms.latestVersions(Instant.now(clock));
    }

    /** 6자리 번호를 5분 보관하고 메일로 보낸다. 돌려주는 값 = 유효 시간(초) */
    public int requestVerification(String rawEmail) {
        String email = normalize(rawEmail);
        if (!isEmail(email)) {
            throw new ApiException(UserErrorCode.INVALID_EMAIL);
        }
        if (users.existsByEmail(email)) {
            throw new ApiException(UserErrorCode.EMAIL_ALREADY_USED);
        }
        String rateKey = "rl:email-verification:" + email;
        if (!rateLimiter.tryAcquire(rateKey, 1, Duration.ofMinutes(1))) {
            throw new ApiException(CommonErrorCode.TOO_MANY_REQUESTS);
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
            throw new ApiException(UserErrorCode.CODE_EXPIRED);
        }
        if (!saved.equals(code)) {
            if (!rateLimiter.tryAcquire(codeFailKey(email), MAX_FAILURES - 1, CODE_TTL)) {
                redis.delete(List.of(codeKey(email), codeFailKey(email)));
                throw new ApiException(UserErrorCode.TOO_MANY_ATTEMPTS);
            }
            // 남은 시도 횟수 (api.md 3, SCR-51 '(2/5)'): 틀린 횟수는 방금 올린 값
            String failed = redis.opsForValue().get(codeFailKey(email));
            int remaining = MAX_FAILURES - (failed == null ? 1 : Integer.parseInt(failed));
            throw new ApiException(UserErrorCode.INVALID_CODE, Map.of("remainingAttempts", remaining));
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
            throw new ApiException(UserErrorCode.VERIFICATION_EXPIRED);
        }
        String password = req.password();
        requireValidPassword(password);
        String nickname = User.validNickname(req.nickname());
        LocalDate birthDate = Age.validBirthDate(req.birthDate(), Age.today(clock));
        String deviceHash = SignupPolicy.validDeviceHash(req.deviceHash());
        Instant now = Instant.now(clock);
        List<Long> agreed = agreedLatestTerms(req.agreedTermVersionIds(), now);
        if (users.existsByEmail(email)) {
            throw new ApiException(UserErrorCode.EMAIL_ALREADY_USED);
        }
        signupPolicy.requireSeat();
        User user;
        try {
            // 가입 버튼 연타처럼 같은 이메일이 동시에 오면 둘 다 위 확인을 지나고, 늦은 쪽은 users.email UNIQUE에 막힌다.
            // flush로 여기서 바로 부딪히게 해 500 대신 409로 (api.md 4, #40)
            user = users.saveAndFlush(User.emailUser(
                    email, passwordEncoder.encode(password), nickname, birthDate, signupPolicy.newReferralCode(), now));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(UserErrorCode.EMAIL_ALREADY_USED);
        }
        terms.recordAgreements(user.getId(), agreed);
        users.insertSignupDevice(deviceHash, user.getId());
        redis.delete(verifiedKey);
        return new Login(user.getId(), true, true);
    }

    /** 없는 이메일·틀린 비밀번호·탈퇴는 구분하지 않는다. 5번 틀리면 5분 잠금 */
    @Transactional
    public Login login(String rawEmail, String password) {
        String email = normalize(rawEmail);
        String lockKey = loginLockKeys(email).get(0);
        String failKey = loginLockKeys(email).get(1);
        if (Boolean.TRUE.equals(redis.hasKey(lockKey))) {
            throw new ApiException(UserErrorCode.LOGIN_LOCKED);
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
                throw new ApiException(UserErrorCode.LOGIN_LOCKED);
            }
            throw new ApiException(UserErrorCode.INVALID_CREDENTIALS);
        }
        rateLimiter.reset(failKey);
        user.get().loggedIn(Instant.now(clock));
        return new Login(user.get().getId(), user.get().onboardingRequired(), false);
    }

    /**
     * 이미 가입한 사람만 로그인시킨다. 새 사용자는 계정을 만들지 않고, 정원을 확인한 뒤 카카오 회원번호 · 닉네임을
     * 가입 토큰(10분)에 묶어 403 KAKAO_SIGNUP_REQUIRED로 돌려준다. 가입은 약관 동의와 함께 {@link #kakaoSignup} (api.md 6 · 49).
     * 카카오 호출(네트워크 왕복 2번)은 트랜잭션 밖에서 한다. 카카오가 느려도 DB 커넥션을 붙잡지 않게
     */
    public Login kakao(KakaoRequest req) {
        // 인가 코드는 한 번만 쓸 수 있어서, 요청 모양 검사를 카카오 호출보다 먼저 한다
        SignupPolicy.validDeviceHash(req.deviceHash());
        KakaoProfile profile = kakaoClient
                .fetchProfile(req.code(), req.redirectUri())
                .orElseThrow(() -> new ApiException(UserErrorCode.SOCIAL_AUTH_FAILED));
        Optional<Login> existing = tx.execute(status -> logInSocialUser(profile.providerUserId()));
        if (existing.isPresent()) {
            return existing.get();
        }
        // 정원이 찼으면 동의 화면을 보여 주기 전에 알린다. 49에서 한 번 더 본다
        signupPolicy.requireSeat();
        String token = "kt_" + TokenService.randomToken();
        redis.opsForValue().set(kakaoSignupKey(token), encode(profile), KAKAO_SIGNUP_TTL);
        throw new ApiException(
                UserErrorCode.KAKAO_SIGNUP_REQUIRED,
                Map.of("signupToken", token, "expiresInSeconds", KAKAO_SIGNUP_TTL.toSeconds()));
    }

    /**
     * 6번의 가입 토큰 + 약관 동의로 users + user_social_accounts + user_terms + signup_devices를 한 트랜잭션으로 (api.md 49).
     * 요청 검사(기기 값 · 약관)를 토큰을 쓰기 전에 해서, 400이면 같은 토큰으로 다시 보낼 수 있다
     */
    public Login kakaoSignup(KakaoSignupRequest req) {
        String deviceHash = SignupPolicy.validDeviceHash(req.deviceHash());
        List<Long> agreed = agreedLatestTerms(req.agreedTermVersionIds(), Instant.now(clock));
        // 꺼내면서 지운다(GETDEL): 연타 · 재사용은 여기서 하나만 지나간다
        String saved = req.signupToken() == null || req.signupToken().isBlank()
                ? null
                : redis.opsForValue().getAndDelete(kakaoSignupKey(req.signupToken()));
        KakaoProfile profile = decode(saved).orElseThrow(() -> new ApiException(UserErrorCode.SIGNUP_TOKEN_INVALID));
        return tx.execute(status -> logInSocialUser(profile.providerUserId())
                // 토큰을 두 번 받아 다른 쪽으로 이미 가입을 마쳤으면 그 사용자로 로그인
                .orElseGet(() -> signUpSocialUser(profile, deviceHash, agreed)));
    }

    private Optional<Login> logInSocialUser(String providerUserId) {
        Optional<Long> existingId = users.findUserIdBySocialAccount(KAKAO, providerUserId);
        if (existingId.isEmpty()) {
            return Optional.empty();
        }
        User user = users.findById(existingId.get())
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(UserErrorCode.SOCIAL_AUTH_FAILED));
        user.loggedIn(Instant.now(clock));
        return Optional.of(new Login(user.getId(), user.onboardingRequired(), false));
    }

    private Login signUpSocialUser(KakaoProfile profile, String deviceHash, List<Long> agreedTermVersionIds) {
        Instant now = Instant.now(clock);
        String nickname = User.fitNickname(profile.nickname());
        if (nickname.isEmpty()) {
            // 닉네임 동의는 필수 항목(api.md 6)이지만 비어 올 수 있어 NOT NULL을 지키려고 둔 기본값. 프로필 설정에서 바꿀 수 있다
            nickname = "카카오 사용자";
        }
        signupPolicy.requireSeat();
        User user = users.save(User.socialUser(nickname, signupPolicy.newReferralCode(), now));
        users.insertSocialAccount(KAKAO, profile.providerUserId(), user.getId());
        users.insertSignupDevice(deviceHash, user.getId());
        terms.recordAgreements(user.getId(), agreedTermVersionIds);
        return new Login(user.getId(), true, true);
    }

    /** 가입 토큰에 묶어 둘 값: "회원번호\n닉네임" (회원번호에는 줄바꿈이 없다) */
    private static String encode(KakaoProfile profile) {
        return profile.providerUserId() + "\n" + (profile.nickname() == null ? "" : profile.nickname());
    }

    private static Optional<KakaoProfile> decode(String saved) {
        if (saved == null) {
            return Optional.empty();
        }
        String[] parts = saved.split("\n", 2);
        return Optional.of(new KakaoProfile(parts[0], parts.length > 1 ? parts[1] : ""));
    }

    /** 필수 약관마다 최신 시행 버전에 동의했는지 본다. 기록할 id = 보낸 것 중 지금 최신 버전인 것 */
    private List<Long> agreedLatestTerms(List<Long> agreedIds, Instant now) {
        Set<Long> agreed = agreedIds == null ? Set.of() : new HashSet<>(agreedIds);
        List<TermVersion> latest = terms.latestVersions(now);
        // 필수 약관이 하나도 없으면(약관 데이터를 아직 안 넣은 DB) 동의 없이 가입되지 않게 막는다
        if (latest.stream().noneMatch(TermVersion::required)
                || latest.stream().anyMatch(t -> t.required() && !agreed.contains(t.termVersionId()))) {
            throw new ApiException(UserErrorCode.TERMS_REQUIRED);
        }
        return latest.stream()
                .map(TermVersion::termVersionId)
                .filter(agreed::contains)
                .toList();
    }

    /** 가입 · 비밀번호 재설정 공통: 8자 이상, 72바이트 이하(bcrypt 한계) */
    public static void requireValidPassword(String password) {
        if (password == null
                || password.length() < PASSWORD_MIN
                || password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new ApiException(UserErrorCode.INVALID_PASSWORD);
        }
    }

    public static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    public static boolean isEmail(String normalizedEmail) {
        return EMAIL.matcher(normalizedEmail).matches();
    }

    /** 로그인 5회 실패 잠금 키 (비밀번호를 바꾸면 푼다) */
    public static List<String> loginLockKeys(String normalizedEmail) {
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

    private static String kakaoSignupKey(String token) {
        return "auth:kakao-signup:" + token;
    }
}
