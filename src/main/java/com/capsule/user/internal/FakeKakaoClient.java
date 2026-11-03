package com.capsule.user.internal;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 카카오를 부르지 않고 코드 "fake-kakao:{회원번호}:{닉네임}"을 카카오 응답처럼 해석한다. 다른 형식은 실패. */
@Component
@ConditionalOnProperty(name = "capsule.fake-integrations", havingValue = "true")
public class FakeKakaoClient implements KakaoClient {

    private static final String PREFIX = "fake-kakao:";

    @Override
    public Optional<KakaoProfile> fetchProfile(String code, String redirectUri) {
        if (code == null || !code.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String[] parts = code.substring(PREFIX.length()).split(":", 2);
        if (parts.length < 2 || parts[0].isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new KakaoProfile(parts[0], parts[1]));
    }
}
