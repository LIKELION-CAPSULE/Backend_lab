package com.capsule.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.TestcontainersConfiguration;
import com.capsule.content.api.AvatarQuery;
import com.capsule.user.api.ActiveSessionLookup;
import com.capsule.user.internal.FakeEmailSender;
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
        return read(result, "$.verificationToken");
    }

    ResultActions signup(String verificationToken, String password, String nickname, String termIds) throws Exception {
        return postJson("/api/auth/email/signup", """
                {"verificationToken":"%s","password":"%s","nickname":"%s","agreedTermVersionIds":%s}
                """.formatted(verificationToken, password, nickname, termIds));
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
        return "Bearer " + read(loginResult, "$.accessToken");
    }
}
