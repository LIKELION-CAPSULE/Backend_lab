package com.capsule.user.client;

import com.capsule.user.dto.KakaoProfile;
import java.util.Optional;

/** 카카오 인가 코드 → 회원번호·닉네임. 운영은 HttpKakaoClient, local·test는 FakeKakaoClient (ADR 0011). */
public interface KakaoClient {

    /** 코드가 만료·오류면 빈 값 */
    Optional<KakaoProfile> fetchProfile(String code, String redirectUri);
}
