package com.capsule.user.internal;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 메일을 보내지 않고 마지막 번호만 들고 있다. E2E 테스트가 {@link #lastCode}로 번호를 읽으므로 public. */
@Component
@ConditionalOnProperty(name = "capsule.fake-integrations", havingValue = "true")
public class FakeEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(FakeEmailSender.class);

    private final Map<String, String> codes = new ConcurrentHashMap<>();

    @Override
    public void sendVerificationCode(String email, String code) {
        codes.put(email, code);
        log.info("Fake 메일: 인증번호 발송 {}", email); // 번호는 찍지 않는다
    }

    public Optional<String> lastCode(String email) {
        return Optional.ofNullable(codes.get(email.strip().toLowerCase(Locale.ROOT)));
    }
}
