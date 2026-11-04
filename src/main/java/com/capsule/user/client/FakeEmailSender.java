package com.capsule.user.client;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 메일을 보내지 않고 마지막 번호만 들고 있다. E2E 테스트가 {@link #lastCode}로 번호를 읽으므로 public.
 * Brevo 키(CAPSULE_BREVO_KEY)가 있으면 실제 메일(BrevoEmailSender)을 쓰고 이 빈은 뜨지 않는다. 개발 서버는 카카오만 Fake로 둘 수 있다
 */
@Component
@Profile({"local", "test"}) // 설정값을 잘못 켜도 운영에서는 뜨지 않게
@ConditionalOnExpression("${capsule.fake-integrations:false} and '${capsule.brevo.key:}'.isBlank()")
public class FakeEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(FakeEmailSender.class);

    private final Map<String, String> codes = new ConcurrentHashMap<>();

    FakeEmailSender() {
        log.info("인증 메일: Fake (메일을 보내지 않음. 실제로 보내려면 CAPSULE_BREVO_KEY · CAPSULE_MAIL_FROM)");
    }

    @Override
    public void sendVerificationCode(String email, String code) {
        codes.put(email, code);
        log.info("Fake 메일: 인증번호 발송 {}", email); // 번호는 찍지 않는다
    }

    public Optional<String> lastCode(String email) {
        return Optional.ofNullable(codes.get(email.strip().toLowerCase(Locale.ROOT)));
    }
}
