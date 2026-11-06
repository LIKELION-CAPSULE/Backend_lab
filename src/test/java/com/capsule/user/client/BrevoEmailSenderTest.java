package com.capsule.user.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

/** 실제 Brevo를 부르지 않고 MockRestServiceServer로 응답을 흉내 낸다 */
class BrevoEmailSenderTest {

    static final String CDN = "https://cdn.capsule.invalid/";
    static final String ICON = "https://cdn.capsule.invalid/brand/capsule-icon-v1.png";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer brevo =
            MockRestServiceServer.bindTo(builder).build();

    @Test
    void sendsCodeMail() {
        BrevoEmailSender sender = new BrevoEmailSender(builder, "brevo-key", "capsule.team@example.com", CDN);
        brevo.expect(requestTo(BrevoEmailSender.SEND_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "brevo-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sender.email").value("capsule.team@example.com"))
                .andExpect(jsonPath("$.sender.name").value("캡슐"))
                .andExpect(jsonPath("$.to[0].email").value("user@example.com"))
                .andExpect(jsonPath("$.subject").value("캡슐 이메일 인증번호 안내"))
                .andExpect(jsonPath("$.textContent").value(org.hamcrest.Matchers.containsString("123456")))
                .andExpect(jsonPath("$.htmlContent").value(org.hamcrest.Matchers.containsString("123456")))
                .andExpect(
                        jsonPath("$.htmlContent").value(org.hamcrest.Matchers.containsString("src=\"" + ICON + "\"")))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"messageId\":\"<abc@smtp-relay.mailin.fr>\"}"));

        sender.sendVerificationCode("user@example.com", "123456");

        brevo.verify();
    }

    @Test
    void sendsPasswordResetLink() {
        BrevoEmailSender sender = new BrevoEmailSender(builder, "brevo-key", "capsule.team@example.com", CDN);
        String link = "https://front.example.com/password/reset?token=pr_abc";
        brevo.expect(requestTo(BrevoEmailSender.SEND_URL))
                .andExpect(jsonPath("$.to[0].email").value("user@example.com"))
                .andExpect(jsonPath("$.subject").value("캡슐 비밀번호 재설정 안내"))
                .andExpect(jsonPath("$.textContent").value(org.hamcrest.Matchers.containsString(link)))
                .andExpect(
                        jsonPath("$.htmlContent").value(org.hamcrest.Matchers.containsString("href=\"" + link + "\"")))
                .andRespond(withStatus(HttpStatus.CREATED));

        sender.sendPasswordResetLink("user@example.com", link);

        brevo.verify();
    }

    @Test
    void wrongKeyIsServerError() {
        BrevoEmailSender sender = new BrevoEmailSender(builder, "wrong-key", "capsule.team@example.com", CDN);
        brevo.expect(requestTo(BrevoEmailSender.SEND_URL))
                .andRespond(withUnauthorizedRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"unauthorized\",\"message\":\"Key not found\"}"));

        // 우리 설정 문제라 사용자에게는 500으로 보인다(GlobalExceptionHandler)
        assertThatThrownBy(() -> sender.sendVerificationCode("user@example.com", "123456"))
                .isInstanceOf(HttpClientErrorException.class);
    }

    @Test
    void brevoOutageIsServerError() {
        BrevoEmailSender sender = new BrevoEmailSender(builder, "brevo-key", "capsule.team@example.com", CDN);
        brevo.expect(requestTo(BrevoEmailSender.SEND_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> sender.sendVerificationCode("user@example.com", "123456"))
                .isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void missingSettingsFailAtStartup() {
        assertThatThrownBy(() -> new BrevoEmailSender(builder, " ", "capsule.team@example.com", CDN))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new BrevoEmailSender(builder, "brevo-key", "", CDN))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void mailTextHasCodeAndExpiry() {
        MailContent mail = MailContent.verificationCode("654321", ICON);
        assertThat(mail.text()).contains("654321").contains("5분");
        assertThat(mail.html())
                .contains("654321")
                .contains("5분")
                .doesNotContain("%s")
                .doesNotContain("%%");
    }

    @Test
    void resetLinkIsEscapedInHtml() {
        // 링크의 &가 HTML에서 깨지지 않게 이스케이프된다. 텍스트 본문은 그대로
        String link = "https://front.example.com/password/reset?token=pr_abc&x=1";
        MailContent mail = MailContent.passwordReset(link, ICON);
        assertThat(mail.html()).contains("href=\"https://front.example.com/password/reset?token=pr_abc&amp;x=1\"");
        assertThat(mail.text()).contains(link).contains("30분");
    }
}
