package com.capsule.user.service;

import com.capsule.common.error.ApiException;
import com.capsule.common.error.CommonErrorCode;
import com.capsule.user.domain.UserErrorCode;
import com.capsule.user.repository.UserRepository;
import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 새 가입 공통 규칙 (api.md 4 · 6): 베타 정원, 기기 값, 내 초대 코드 */
@Component
public class SignupPolicy {

    private static final int DEVICE_HASH_MAX = 64;
    // 헷갈리는 글자(0 · O · 1 · I)를 뺀 32자. 4자리 = 약 100만 개
    private static final String CODE_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 4;
    private static final int CODE_TRIES = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final long betaMaxUsers;

    public SignupPolicy(UserRepository users, @Value("${capsule.beta.max-users:100}") long betaMaxUsers) {
        this.users = users;
        this.betaMaxUsers = betaMaxUsers;
    }

    /**
     * 정원이 찼으면 403 BETA_FULL. 동시에 가입하면 정원을 조금 넘을 수 있다:
     * 베타 100명 규모에서 막으려고 표 잠금을 걸 만큼은 아니라 둔다
     */
    public void requireSeat() {
        if (users.countByWithdrawnAtIsNull() >= betaMaxUsers) {
            throw new ApiException(UserErrorCode.BETA_FULL);
        }
    }

    /** 1~64자. 없으면 400 INVALID_REQUEST (명세 4 · 6 '필수') */
    public static String validDeviceHash(String raw) {
        String hash = raw == null ? "" : raw.strip();
        if (hash.isEmpty() || hash.length() > DEVICE_HASH_MAX) {
            throw new ApiException(CommonErrorCode.INVALID_REQUEST);
        }
        return hash;
    }

    /** CAP-XXXX. 이미 있으면 다시 뽑는다 */
    public String newReferralCode() {
        for (int i = 0; i < CODE_TRIES; i++) {
            StringBuilder code = new StringBuilder("CAP-");
            for (int j = 0; j < CODE_LENGTH; j++) {
                code.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            if (!users.existsByReferralCode(code.toString())) {
                return code.toString();
            }
        }
        throw new IllegalStateException("초대 코드를 " + CODE_TRIES + "번 뽑아도 겹침");
    }
}
