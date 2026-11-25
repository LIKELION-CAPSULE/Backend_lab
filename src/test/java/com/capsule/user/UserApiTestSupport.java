package com.capsule.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.TestcontainersConfiguration;
import com.capsule.content.api.AvatarQuery;
import com.capsule.user.api.ActiveSessionLookup;
import com.capsule.user.client.FakeEmailSender;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** user 모듈 통합 테스트 공통. 다른 모듈 계약은 목. 테스트 클래스끼리 같은 목을 써서 컨텍스트를 하나로 재사용한다 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
abstract class UserApiTestSupport {

    static final String PASSWORD = "capsule1234";
    static final String ADULT_BIRTH_DATE = "2001-04-10";

    @MockitoBean
    AvatarQuery avatarQuery;

    @MockitoBean
    ActiveSessionLookup activeSessionLookup;

    @Autowired
    MockMvc mvc;

    @Autowired
    FakeEmailSender emailSender;

    static String uniqueEmail() {
        return "u" + UUID.randomUUID().toString().substring(0, 12) + "@example.com";
    }

    ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    String verificationToken(String email) throws Exception {
        postJson("/api/auth/email/verification", "{\"email\":\"%s\"}".formatted(email))
                .andExpect(status().isAccepted());
        String code = emailSender.lastCode(email).orElseThrow();
        MvcResult result = postJson(
                        "/api/auth/email/verification/confirm",
                        "{\"email\":\"%s\",\"code\":\"%s\"}".formatted(email, code))
                .andExpect(status().isOk())
                .andReturn();
        return read(result, "$.data.verificationToken");
    }

    ResultActions signup(String verificationToken, String password, String nickname, String termIds) throws Exception {
        return signup(verificationToken, password, nickname, termIds, ADULT_BIRTH_DATE);
    }

    ResultActions signup(String verificationToken, String password, String nickname, String termIds, String birthDate)
            throws Exception {
        return postJson("/api/auth/email/signup", """
                {"verificationToken":"%s","password":"%s","nickname":"%s","agreedTermVersionIds":%s,
                 "birthDate":"%s","deviceHash":"%s"}
                """.formatted(
                        verificationToken, password, nickname, termIds, birthDate, deviceHash()));
    }

    static String deviceHash() {
        return "dev-" + UUID.randomUUID();
    }

    static String kakaoBody(String code) {
        return """
                {"code":"%s","redirectUri":"http://localhost/oauth/kakao","deviceHash":"%s"}
                """.formatted(code, deviceHash());
    }

    /** 6: 새 카카오 사용자 → 403 KAKAO_SIGNUP_REQUIRED의 가입 토큰 */
    String kakaoSignupToken(String code) throws Exception {
        MvcResult result = postJson("/api/auth/social/KAKAO", kakaoBody(code))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("KAKAO_SIGNUP_REQUIRED"))
                .andExpect(jsonPath("$.detail.expiresInSeconds").value(600))
                .andReturn();
        return read(result, "$.detail.signupToken");
    }

    /** 49: 가입 토큰 + 약관 동의로 카카오 가입 완료 */
    ResultActions kakaoSignup(String signupToken, String termIds) throws Exception {
        return postJson("/api/auth/social/KAKAO/signup", """
                {"signupToken":"%s","agreedTermVersionIds":%s,"deviceHash":"%s"}
                """.formatted(signupToken, termIds, deviceHash()));
    }

    /** 6 → 49로 카카오 가입까지 마친 응답 (accessToken, userId, isNewUser) */
    MvcResult kakaoSignedUp(String code) throws Exception {
        return kakaoSignup(kakaoSignupToken(code), "[3,4]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(true))
                .andReturn();
    }

    /** 가입까지 마친 사용자의 응답 (accessToken, userId) */
    MvcResult signedUp(String email) throws Exception {
        return signup(verificationToken(email), PASSWORD, "승희", "[3,4]")
                .andExpect(status().isCreated())
                .andReturn();
    }

    static <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    static String bearer(MvcResult loginResult) throws Exception {
        return "Bearer " + read(loginResult, "$.data.accessToken");
    }
}
