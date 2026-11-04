package com.capsule.user.client;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 인증번호 메일을 Brevo 발송 API(HTTPS)로 보낸다 (api.md 2, ADR 0020).
 * Railway Trial · Hobby는 SMTP가 막혀 있어서 SMTP 대신 HTTPS API를 쓴다.
 * CAPSULE_BREVO_KEY가 있을 때만 뜬다. 없으면 local · test는 FakeEmailSender, 운영은 메일 빈이 없어 시작에 실패한다.
 * 발송 실패는 예외 그대로 둬서 500으로 보인다(인증번호는 Redis에 이미 저장됨, 1분 뒤 다시 요청 가능).
 */
@Component
@ConditionalOnExpression("!'${capsule.brevo.key:}'.isBlank()")
class BrevoEmailSender implements EmailSender {

    static final String SEND_URL = "https://api.brevo.com/v3/smtp/email";
    private static final String SENDER_NAME = "캡슐";

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailSender.class);

    private final RestClient rest;
    private final String apiKey;
    private final String from;

    @Autowired
    BrevoEmailSender(@Value("${capsule.brevo.key}") String apiKey, @Value("${capsule.mail.from}") String from) {
        this(RestClient.builder().requestFactory(timeouts()), apiKey, from);
    }

    /** 테스트가 MockRestServiceServer를 붙인 builder를 넘긴다 */
    BrevoEmailSender(RestClient.Builder builder, String apiKey, String from) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("capsule.brevo.key가 비어 있다");
        }
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("capsule.mail.from(보내는 사람 주소, Brevo에서 인증한 이메일)이 비어 있다");
        }
        this.rest = builder.build();
        this.apiKey = apiKey;
        this.from = from;
        log.info("인증 메일: Brevo로 실제 발송"); // 개발 서버에서 키를 빠뜨려 Fake로 떨어졌는지 시작 로그로 구분
    }

    @Override
    public void sendVerificationCode(String email, String code) {
        send(email, "[캡슐] 이메일 인증번호 " + code, text(code));
        log.info("인증 메일 발송 요청 완료"); // 주소 · 번호는 남기지 않는다
    }

    @Override
    public void sendPasswordResetLink(String email, String link) {
        send(email, "[캡슐] 비밀번호 재설정", resetText(link));
        log.info("비밀번호 재설정 메일 발송 요청 완료"); // 주소 · 링크(토큰)는 남기지 않는다
    }

    private void send(String email, String subject, String textContent) {
        Map<String, Object> body = Map.of(
                "sender",
                Map.of("name", SENDER_NAME, "email", from),
                "to",
                List.of(Map.of("email", email)),
                "subject",
                subject,
                "textContent",
                textContent);
        rest.post()
                .uri(SEND_URL)
                .header("api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    static String resetText(String link) {
        return """
                캡슐 비밀번호 재설정을 요청하셨어요.
                아래 링크에서 30분 안에 새 비밀번호를 설정해 주세요.

                %s

                본인이 요청하지 않았다면 이 메일은 무시해 주세요. 비밀번호는 바뀌지 않아요.
                """.formatted(link);
    }

    static String text(String code) {
        return """
                캡슐 가입 인증번호는 %s 입니다.
                5분 안에 입력해 주세요.

                본인이 요청하지 않았다면 이 메일은 무시해 주세요.
                """.formatted(code);
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }
}
