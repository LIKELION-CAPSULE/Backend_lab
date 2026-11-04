package com.capsule.user.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.capsule.user.dto.KakaoProfile;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

/** 실제 카카오를 부르지 않고 MockRestServiceServer로 카카오 응답을 흉내 낸다 */
class HttpKakaoClientTest {

    private static final String REDIRECT = "https://capsule.app/oauth/kakao";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer kakao =
            MockRestServiceServer.bindTo(builder).build();

    @Test
    void codeToProfile() {
        HttpKakaoClient client = new HttpKakaoClient(builder, "rest-key", "");
        kakao.expect(requestTo(HttpKakaoClient.TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formData(form("rest-key", "code-1", null)))
                .andRespond(withSuccess("""
                        {"token_type":"bearer","access_token":"kakao-at","expires_in":21599}""", MediaType.APPLICATION_JSON));
        kakao.expect(requestTo(HttpKakaoClient.ME_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer kakao-at"))
                .andRespond(withSuccess("""
                        {"id":4242,"connected_at":"2026-11-04T00:00:00Z",
                         "properties":{"nickname":"옛닉네임"},
                         "kakao_account":{"profile_nickname_needs_agreement":false,"profile":{"nickname":"채린팬"}}}""", MediaType.APPLICATION_JSON));

        Optional<KakaoProfile> profile = client.fetchProfile("code-1", REDIRECT);

        assertThat(profile).contains(new KakaoProfile("4242", "채린팬"));
        kakao.verify();
    }

    @Test
    void clientSecretSentWhenSet() {
        HttpKakaoClient client = new HttpKakaoClient(builder, "rest-key", "secret-1");
        kakao.expect(requestTo(HttpKakaoClient.TOKEN_URL))
                .andExpect(content().formData(form("rest-key", "code-1", "secret-1")))
                .andRespond(withSuccess("{\"access_token\":\"kakao-at\"}", MediaType.APPLICATION_JSON));
        kakao.expect(requestTo(HttpKakaoClient.ME_URL))
                .andRespond(withSuccess("{\"id\":1}", MediaType.APPLICATION_JSON));

        assertThat(client.fetchProfile("code-1", REDIRECT)).contains(new KakaoProfile("1", null));
        kakao.verify();
    }

    @Test
    void nicknameFallsBackToProperties() {
        HttpKakaoClient client = new HttpKakaoClient(builder, "rest-key", "");
        kakao.expect(requestTo(HttpKakaoClient.TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"kakao-at\"}", MediaType.APPLICATION_JSON));
        kakao.expect(requestTo(HttpKakaoClient.ME_URL))
                .andRespond(
                        withSuccess("{\"id\":7,\"properties\":{\"nickname\":\"옛닉네임\"}}", MediaType.APPLICATION_JSON));

        assertThat(client.fetchProfile("code-1", REDIRECT)).contains(new KakaoProfile("7", "옛닉네임"));
    }

    @Test
    void expiredCodeIsEmpty() {
        HttpKakaoClient client = new HttpKakaoClient(builder, "rest-key", "");
        kakao.expect(requestTo(HttpKakaoClient.TOKEN_URL))
                .andRespond(
                        withBadRequest().contentType(MediaType.APPLICATION_JSON).body("""
                                {"error":"invalid_grant","error_description":"authorization code not found","error_code":"KOE320"}"""));

        assertThat(client.fetchProfile("used-code", REDIRECT)).isEmpty();
        kakao.verify();
    }

    @Test
    void wrongAppKeyIsNotLoginFailure() {
        HttpKakaoClient client = new HttpKakaoClient(builder, "wrong-key", "");
        kakao.expect(requestTo(HttpKakaoClient.TOKEN_URL))
                .andRespond(withUnauthorizedRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error":"invalid_client","error_description":"Bad client credentials","error_code":"KOE010"}"""));

        assertThatThrownBy(() -> client.fetchProfile("code-1", REDIRECT)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blankCodeSkipsKakao() {
        HttpKakaoClient client = new HttpKakaoClient(builder, "rest-key", "");

        assertThat(client.fetchProfile(" ", REDIRECT)).isEmpty();
        assertThat(client.fetchProfile("code-1", null)).isEmpty();
        kakao.verify(); // 아무 요청도 없어야 한다
    }

    @Test
    void kakaoOutageIsNotLoginFailure() {
        HttpKakaoClient client = new HttpKakaoClient(builder, "rest-key", "");
        kakao.expect(requestTo(HttpKakaoClient.TOKEN_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchProfile("code-1", REDIRECT)).isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void missingKeyFailsAtStartup() {
        assertThatThrownBy(() -> new HttpKakaoClient(builder, " ", "")).isInstanceOf(IllegalStateException.class);
    }

    private static MultiValueMap<String, String> form(String key, String code, String secret) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", key);
        form.add("redirect_uri", REDIRECT);
        form.add("code", code);
        if (secret != null) {
            form.add("client_secret", secret);
        }
        return form;
    }
}
