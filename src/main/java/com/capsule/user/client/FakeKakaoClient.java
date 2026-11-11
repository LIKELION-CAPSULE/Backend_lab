package com.capsule.user.client;

import com.capsule.user.dto.KakaoProfile;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 카카오를 부르지 않고 코드 "fake-kakao:{회원번호}:{닉네임}"을 카카오 응답처럼 해석한다. 다른 형식은 실패.
 * 카카오 키(capsule.kakao.rest-api-key)가 있으면 실제 카카오(HttpKakaoClient)를 쓰고 이 빈은 뜨지 않는다
 */
@Component
@Profile({"local", "test"}) // 설정값을 잘못 켜도 운영에서는 뜨지 않게 (아무 코드로나 로그인됨)
@ConditionalOnExpression("${capsule.fake-integrations:false} and '${capsule.kakao.rest-api-key:}'.isBlank()")
public class FakeKakaoClient implements KakaoClient {

    private static final Logger log = LoggerFactory.getLogger(FakeKakaoClient.class);
    private static final String PREFIX = "fake-kakao:";

    public FakeKakaoClient() {
        log.info("카카오 로그인: Fake (fake-kakao:<번호>:<이름> 코드로 로그인. 실제로 하려면 CAPSULE_KAKAO_REST_API_KEY)");
    }

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
