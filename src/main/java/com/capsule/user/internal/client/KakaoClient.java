package com.capsule.user.internal.client;

import com.capsule.user.internal.dto.KakaoProfile;
import java.util.Optional;

/** 카카오 인가 코드 → 회원번호·닉네임. 지금은 Fake만 있다. 실제 어댑터는 키를 받은 뒤 RestClient로 (ADR 0011). */
public interface KakaoClient {

    /** 코드가 만료·오류면 빈 값 */
    Optional<KakaoProfile> fetchProfile(String code, String redirectUri);
}
