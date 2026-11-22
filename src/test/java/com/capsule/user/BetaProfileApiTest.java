package com.capsule.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.common.time.ClockConfig;
import com.capsule.content.api.ContentTypes.AvatarInfo;
import com.capsule.user.api.UserQuery;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** 12/1 베타 1단계 회원 (api.md 4 · 6 · 8 · 10 · 29 · 30) */
class BetaProfileApiTest extends UserApiTestSupport {

    private static final AvatarInfo AVATAR = new AvatarInfo(1, "기본 아바타", "https://cdn.test/avatar1.png", true, null);

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserQuery userQuery;

    private static LocalDate today() {
        return LocalDate.now(ClockConfig.KST);
    }

    @Test
    void signupChecksBirthDateAndDevice() throws Exception {
        String under14 = today().minusYears(14).plusDays(1).toString(); // 내일이 만 14세 생일
        signup(verificationToken(uniqueEmail()), PASSWORD, "어린이", "[3,4]", under14)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNDER_14"));
        signup(
                        verificationToken(uniqueEmail()),
                        PASSWORD,
                        "미래",
                        "[3,4]",
                        today().plusDays(1).toString())
                .andExpect(jsonPath("$.code").value("INVALID_BIRTH_DATE"));
        signup(verificationToken(uniqueEmail()), PASSWORD, "형식", "[3,4]", "2001/04/10")
                .andExpect(jsonPath("$.code").value("INVALID_BIRTH_DATE"));
        postJson("/api/auth/email/signup", """
                        {"verificationToken":"%s","password":"%s","nickname":"기기없음","agreedTermVersionIds":[3,4],
                         "birthDate":"2001-04-10"}
                        """.formatted(verificationToken(uniqueEmail()), PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        // 만 14세 생일 당일은 가입된다
        String exactly14 = today().minusYears(14).toString();
        MvcResult ok = signup(verificationToken(uniqueEmail()), PASSWORD, "열네살", "[3,4]", exactly14)
                .andExpect(status().isCreated())
                .andReturn();
        long userId = ((Number) read(ok, "$.data.userId")).longValue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM signup_devices WHERE user_id = ?", Integer.class, userId))
                .isEqualTo(1);

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(ok)))
                .andExpect(jsonPath("$.data.birthDate").value(exactly14))
                .andExpect(jsonPath("$.data.isMinor").value(true))
                .andExpect(jsonPath("$.data.honorific").doesNotExist())
                .andExpect(
                        jsonPath("$.data.referralCode").value(org.hamcrest.Matchers.matchesPattern("CAP-[0-9A-Z]{4}")));
        assertThat(userQuery.find(userId))
                .hasValueSatisfying(p -> assertThat(p.minor()).isTrue());
    }

    @Test
    void kakaoUserFillsBirthDateOnceInOnboarding() throws Exception {
        given(avatarQuery.defaultAvatar()).willReturn(Optional.of(AVATAR));
        given(avatarQuery.find(1)).willReturn(Optional.of(AVATAR));
        String code = "fake-kakao:%s:카카오".formatted(UUID.randomUUID());
        postJson("/api/auth/social/KAKAO", """
                        {"code":"%s","redirectUri":"http://localhost/oauth/kakao"}
                        """.formatted(code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        MvcResult kakao = postJson("/api/auth/social/KAKAO", kakaoBody(code))
                .andExpect(status().isOk())
                .andReturn();
        String auth = bearer(kakao);

        // 아바타만 고르면 아직 온보딩 (생년월일 없음)
        patchMe(auth, "{\"avatarId\":1}")
                .andExpect(jsonPath("$.data.onboardingRequired").value(true));
        patchMe(auth, "{\"birthDate\":\"%s\"}".formatted(today().minusYears(10)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNDER_14"));
        patchMe(auth, "{\"birthDate\":\"2001-04-10\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.birthDate").value("2001-04-10"))
                .andExpect(jsonPath("$.data.isMinor").value(false))
                .andExpect(jsonPath("$.data.onboardingRequired").value(false));
        // 한 번 저장하면 바뀌지 않는다
        patchMe(auth, "{\"birthDate\":\"1999-01-01\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.birthDate").value("2001-04-10"));
        // 다시 로그인해도 온보딩이 끝난 상태
        postJson("/api/auth/social/KAKAO", kakaoBody(code))
                .andExpect(jsonPath("$.data.onboardingRequired").value(false));
    }

    @Test
    void honorificAndAboutMe() throws Exception {
        MvcResult user = signedUp(uniqueEmail());
        String auth = bearer(user);
        long userId = ((Number) read(user, "$.data.userId")).longValue();
        given(avatarQuery.defaultAvatar()).willReturn(Optional.of(AVATAR));
        given(avatarQuery.find(1)).willReturn(Optional.of(AVATAR));

        patchMe(auth, "{\"honorific\":\" 승희야 \",\"aboutMe\":\"백엔드 개발 공부 중\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.honorific").value("승희야"))
                .andExpect(jsonPath("$.data.aboutMe").value("백엔드 개발 공부 중"));
        assertThat(userQuery.find(userId)).hasValueSatisfying(p -> {
            assertThat(p.honorific()).isEqualTo("승희야");
            assertThat(p.aboutMe()).isEqualTo("백엔드 개발 공부 중");
        });

        patchMe(auth, "{\"honorific\":\"%s\"}".formatted("가".repeat(11)))
                .andExpect(jsonPath("$.code").value("INVALID_HONORIFIC"));
        patchMe(auth, "{\"aboutMe\":\"%s\"}".formatted("가".repeat(51))) // 50자까지 (2026-11-23)
                .andExpect(jsonPath("$.code").value("INVALID_ABOUT_ME")); // 하나라도 틀리면 다른 칸도 저장하지 않는다
        patchMe(auth, "{\"aboutMe\":\"바뀌면 안 됨\",\"honorific\":\"%s\"}".formatted("가".repeat(11)))
                .andExpect(status().isBadRequest());
        // 안 보낸 칸은 그대로, 빈 문자열은 지움
        patchMe(auth, "{\"honorific\":\"\"}")
                .andExpect(jsonPath("$.data.honorific").doesNotExist())
                .andExpect(jsonPath("$.data.aboutMe").value("백엔드 개발 공부 중"));
    }

    @Test
    void settingsDefaultsAndPartialUpdate() throws Exception {
        String auth = bearer(signedUp(uniqueEmail()));

        settings(auth)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.talkFrequency").value("SOMETIMES"))
                .andExpect(jsonPath("$.data.voiceEnabled").value(true))
                .andExpect(jsonPath("$.data.voiceVolume").value(70))
                .andExpect(jsonPath("$.data.voiceSpeed").value("NORMAL"))
                .andExpect(jsonPath("$.data.pipBubble").value(true))
                .andExpect(jsonPath("$.data.defaultMode").value("BASIC"))
                .andExpect(jsonPath("$.data.pipSize").value("M"));

        patchSettings(auth, "{\"voiceEnabled\":false,\"voiceVolume\":30}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.voiceEnabled").value(false))
                .andExpect(jsonPath("$.data.voiceVolume").value(30))
                .andExpect(jsonPath("$.data.talkFrequency").value("SOMETIMES"));

        for (String invalid : new String[] {
            "{\"voiceVolume\":101}",
            "{\"voiceVolume\":\"50\"}",
            "{\"talkFrequency\":\"LOUD\"}",
            "{\"voiceEnabld\":true}", // 오타
            "{\"pipSize\":\"M\",\"theme\":\"DARK\"}",
            "[]"
        }) {
            patchSettings(auth, invalid)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_SETTINGS"));
        }
        // 틀린 요청은 아무것도 바꾸지 않았다
        settings(auth).andExpect(jsonPath("$.data.pipSize").value("M"));

        // null이면 기본값으로
        patchSettings(auth, "{\"voiceVolume\":null}")
                .andExpect(jsonPath("$.data.voiceVolume").value(70));
        settings(auth)
                .andExpect(jsonPath("$.data.voiceVolume").value(70))
                .andExpect(jsonPath("$.data.voiceEnabled").value(false));
    }

    private ResultActions patchMe(String auth, String body) throws Exception {
        return mvc.perform(patch("/api/me")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions settings(String auth) throws Exception {
        return mvc.perform(get("/api/me/settings").header(HttpHeaders.AUTHORIZATION, auth));
    }

    private ResultActions patchSettings(String auth, String body) throws Exception {
        return mvc.perform(patch("/api/me/settings")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
