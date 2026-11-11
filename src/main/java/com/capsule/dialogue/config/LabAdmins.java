package com.capsule.dialogue.config;

import com.capsule.common.security.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 실험실 관리자 로그인 (ADR 0027). 서버에 세션을 두지 않고, 로그인하면 서명한 토큰을 준다: 아이디.만료.서명
 * - 화면은 이 토큰을 X-Lab-Token 헤더로 보낸다 (고정 실험실 토큰과 같은 자리, LabSecurityConfig)
 * - 서명 키는 JWT 비밀값(capsule.auth.jwt-secret)이고, 서명 안에 그 계정의 저장된 비밀번호 값을 넣는다:
 *   비밀번호를 바꾸면 그 전에 받은 토큰은 바로 못 쓴다
 * - 사용자용 JWT(/api)와는 모양이 달라 서로 쓸 수 없다
 * ponytail: 로그아웃은 화면에서 토큰을 지우는 것뿐(서버 목록 없음). 바로 끊으려면 비밀번호를 바꾼다
 */
@Component
@ConditionalOnProperty(name = "capsule.llm.lab.enabled", havingValue = "true")
public class LabAdmins {

    /** 인증된 관리자 아이디를 요청에 남기는 속성 이름 (운영 설정 저장 로그에 누가 바꿨는지) */
    public static final String REQUEST_ATTRIBUTE = "capsule.labAdmin";

    public record Session(String username, String token, Instant expiresAt) {}

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final Map<String, String> users;
    private final AdminProperties props;
    private final byte[] key;
    private final Clock clock;
    /** 없는 아이디도 비밀번호 비교에 같은 시간을 쓰게 (아이디가 있는지 응답 시간으로 알 수 없게) */
    private final String dummy;

    public LabAdmins(AdminProperties props, AuthProperties auth, Clock clock) {
        props.users().forEach((user, stored) -> {
            try {
                // 형식(앞의 {id})이 틀리면 여기서 던진다: 첫 로그인이 아니라 시작 때 알게. 빈 값은 비교 없이 false라 "-"
                encoder.matches("-", stored);
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new IllegalStateException(
                        "관리자 " + user + "의 비밀번호는 {bcrypt}… 또는 {noop}… 형식이어야 함 (CAPSULE_ADMIN_USERS_" + user + ")");
            }
        });
        this.users = props.users();
        this.props = props;
        this.key = auth.jwtSecret().getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
        this.dummy = users.isEmpty() ? null : encoder.encode("-");
    }

    public boolean enabled() {
        return !users.isEmpty();
    }

    /** 맞으면 새 토큰, 틀리면 비어 있음 (아이디가 틀렸는지 비밀번호가 틀렸는지 구분하지 않음) */
    public Optional<Session> login(String username, String password) {
        if (!enabled() || username == null || password == null) return Optional.empty();
        String stored = users.get(username);
        boolean ok = encoder.matches(password, stored != null ? stored : dummy) && stored != null;
        if (!ok) return Optional.empty();
        long exp = clock.instant().plus(props.sessionTtl()).getEpochSecond();
        String token = B64.encodeToString(username.getBytes(StandardCharsets.UTF_8)) + "." + exp + "."
                + sign(username, exp, stored);
        return Optional.of(new Session(username, token, Instant.ofEpochSecond(exp)));
    }

    /** 토큰이 맞고 안 지났으면 아이디 */
    public Optional<String> verify(String token) {
        if (!enabled() || token == null) return Optional.empty();
        String[] parts = token.split("\\.");
        if (parts.length != 3) return Optional.empty();
        try {
            String username = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            long exp = Long.parseLong(parts[1]);
            String stored = users.get(username);
            if (stored == null || clock.instant().getEpochSecond() >= exp) return Optional.empty();
            boolean same = MessageDigest.isEqual(
                    parts[2].getBytes(StandardCharsets.UTF_8),
                    sign(username, exp, stored).getBytes(StandardCharsets.UTF_8));
            return same ? Optional.of(username) : Optional.empty();
        } catch (IllegalArgumentException e) { // 잘못된 base64 · 숫자
            return Optional.empty();
        }
    }

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private String sign(String username, long exp, String stored) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] sig =
                    mac.doFinal(("lab-admin|" + username + "|" + exp + "|" + stored).getBytes(StandardCharsets.UTF_8));
            return B64.encodeToString(sig);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
