package com.capsule.user.client;

import com.capsule.user.dto.KakaoProfile;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * 실제 카카오 로그인: 인가 코드 → 카카오 액세스 토큰 → 회원번호·닉네임 (api.md 6, ADR 0011).
 * 운영(Fake 끔)과, 카카오 키가 있는 개발 서버에서 뜬다. 키가 없는 local · test는 FakeKakaoClient.
 * 코드 만료·오류처럼 카카오가 4xx로 거절하면 빈 값(→ 401 SOCIAL_AUTH_FAILED). 카카오 장애(5xx·연결 실패)는 예외 그대로 둬서 500으로 보인다.
 */
@Component
@ConditionalOnExpression("!${capsule.fake-integrations:false} or !'${capsule.kakao.rest-api-key:}'.isBlank()")
class HttpKakaoClient implements KakaoClient {

    static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    static final String ME_URL = "https://kapi.kakao.com/v2/user/me";

    private static final Logger log = LoggerFactory.getLogger(HttpKakaoClient.class);

    private final RestClient rest;
    private final String restApiKey;
    private final String clientSecret;

    @Autowired
    HttpKakaoClient(
            @Value("${capsule.kakao.rest-api-key}") String restApiKey,
            @Value("${capsule.kakao.client-secret:}") String clientSecret) {
        this(RestClient.builder().requestFactory(timeouts()), restApiKey, clientSecret);
    }

    /** 테스트가 MockRestServiceServer를 붙인 builder를 넘긴다 */
    HttpKakaoClient(RestClient.Builder builder, String restApiKey, String clientSecret) {
        if (restApiKey == null || restApiKey.isBlank()) {
            throw new IllegalStateException("capsule.kakao.rest-api-key가 비어 있다");
        }
        this.rest = builder.build();
        this.restApiKey = restApiKey;
        this.clientSecret = clientSecret == null ? "" : clientSecret;
        log.info("카카오 로그인: 실제 카카오"); // 개발 서버에서 키를 빠뜨려 Fake로 떨어졌는지 시작 로그로 구분
    }

    @Override
    public Optional<KakaoProfile> fetchProfile(String code, String redirectUri) {
        if (code == null || code.isBlank() || redirectUri == null || redirectUri.isBlank()) {
            return Optional.empty();
        }
        try {
            String accessToken = requestToken(code, redirectUri);
            UserResponse me = rest.get()
                    .uri(ME_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(UserResponse.class);
            if (me == null || me.id() == null) {
                throw new IllegalStateException("카카오 사용자 정보에 회원번호가 없다");
            }
            return Optional.of(new KakaoProfile(Long.toString(me.id()), me.nickname()));
        } catch (HttpClientErrorException e) {
            // 인가 코드·토큰 값은 남기지 않는다. 카카오 에러 본문(KOE320 등)은 원인 확인용
            String body = abbreviate(e.getResponseBodyAsString());
            if (body.contains("\"invalid_client\"")) {
                // 앱 키·Client Secret이 틀린 우리 설정 문제(KOE101·KOE010). 사용자 로그인 실패로 묻히지 않게 500으로
                log.error("카카오 앱 설정 오류 {}: {}", e.getStatusCode().value(), body);
                throw new IllegalStateException("카카오 앱 키 또는 Client Secret 설정 오류", e);
            }
            log.warn("카카오 인증 거절 {}: {}", e.getStatusCode().value(), body);
            return Optional.empty();
        }
    }

    private String requestToken(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", restApiKey);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        if (!clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }
        TokenResponse token = rest.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
            throw new IllegalStateException("카카오 토큰 응답에 access_token이 없다");
        }
        return token.accessToken();
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    private static String abbreviate(String body) {
        return body.length() <= 200 ? body : body.substring(0, 200) + "...";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UserResponse(
            Long id, @JsonProperty("kakao_account") Account kakaoAccount, Properties properties) {

        /** 동의 항목 닉네임은 kakao_account.profile에 온다. 옛 앱 설정이면 properties에만 있을 수 있다 */
        String nickname() {
            if (kakaoAccount != null
                    && kakaoAccount.profile() != null
                    && kakaoAccount.profile().nickname() != null) {
                return kakaoAccount.profile().nickname();
            }
            return properties == null ? null : properties.nickname();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Account(Profile profile) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Profile(String nickname) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Properties(String nickname) {}
}
