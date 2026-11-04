package com.capsule.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.capsule.TestcontainersConfiguration;
import com.capsule.user.client.EmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Brevo 키가 있으면 Fake 대신 실제 메일 어댑터 하나만 뜬다 (개발 서버: 메일은 실제, 카카오는 Fake) */
@SpringBootTest(properties = {"capsule.brevo.key=test-key", "capsule.mail.from=capsule.team@example.com"})
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class EmailSenderSelectionTest {

    @Autowired
    ApplicationContext context;

    @Test
    void brevoReplacesFakeWhenKeyIsSet() {
        assertThat(context.getBeansOfType(EmailSender.class).values())
                .singleElement()
                .extracting(bean -> bean.getClass().getSimpleName())
                .isEqualTo("BrevoEmailSender");
    }
}
