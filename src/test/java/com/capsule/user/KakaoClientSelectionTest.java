package com.capsule.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.capsule.TestcontainersConfiguration;
import com.capsule.user.client.KakaoClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** 카카오 키가 있으면 Fake(fake-integrations=true)여도 실제 카카오 어댑터 하나만 뜬다 (개발 서버) */
@SpringBootTest(properties = "capsule.kakao.rest-api-key=test-key")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class KakaoClientSelectionTest {

    @Autowired
    ApplicationContext context;

    @Test
    void realKakaoReplacesFakeWhenKeyIsSet() {
        assertThat(context.getBeansOfType(KakaoClient.class).values())
                .singleElement()
                .extracting(bean -> bean.getClass().getSimpleName())
                .isEqualTo("HttpKakaoClient");
    }
}
