package com.capsule.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.common.time.ClockConfig;
import com.capsule.content.api.ContentTypes.AvatarInfo;
import com.capsule.user.api.MemoryQuery;
import com.capsule.user.api.UserQuery;
import com.capsule.user.api.UserTypes.ActiveSessionSummary;
import com.capsule.user.api.UserTypes.MemoryInfo;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class ProfileAndMemoryApiTest extends UserApiTestSupport {

    private static final AvatarInfo AVATAR = new AvatarInfo(1, "기본 아바타", "https://cdn.test/avatar1.png", true, null);
    private static final AvatarInfo INACTIVE = new AvatarInfo(2, "옛 아바타", "https://cdn.test/avatar2.png", false, null);

    @Autowired
    UserQuery userQuery;

    @Autowired
    MemoryQuery memoryQuery;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void meAndProfileUpdate() throws Exception {
        String email = uniqueEmail();
        MvcResult user = signedUp(email);
        String auth = bearer(user);
        long userId = ((Number) read(user, "$.data.userId")).longValue();
        given(avatarQuery.defaultAvatar()).willReturn(Optional.of(AVATAR));
        given(avatarQuery.find(1)).willReturn(Optional.of(AVATAR));
        given(avatarQuery.find(2)).willReturn(Optional.of(INACTIVE));

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.nickname").value("승희"))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.onboardingRequired").value(true))
                .andExpect(jsonPath("$.data.activeConnection").value(nullValue()))
                .andExpect(jsonPath("$.data.profileImage").value(nullValue()));

        patchMe(auth, "{\"nickname\":\"\"}").andExpect(jsonPath("$.code").value("INVALID_NICKNAME"));
        patchMe(auth, "{\"nickname\":\"새닉\",\"avatarId\":99}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AVATAR_NOT_FOUND"));
        patchMe(auth, "{\"nickname\":\"새닉\",\"avatarId\":2}")
                .andExpect(jsonPath("$.code").value("AVATAR_NOT_FOUND"));
        patchMe(auth, "{\"nickname\":\" 새닉 \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("새닉"))
                .andExpect(jsonPath("$.data.onboardingRequired").value(false))
                .andExpect(jsonPath("$.data.profileImage").value(AVATAR.thumbnailUrl()));

        given(activeSessionLookup.find(userId))
                .willReturn(Optional.of(new ActiveSessionSummary(512, 1, "채린", Instant.parse("2026-11-07T05:00:02Z"))));
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.data.activeConnection.connectionId").value(512))
                .andExpect(jsonPath("$.data.activeConnection.mateId").value(1))
                .andExpect(jsonPath("$.data.activeConnection.mateName").value("채린"))
                .andExpect(jsonPath("$.data.activeConnection.startedAt").value("2026-11-07T05:00:02Z"))
                .andExpect(jsonPath("$.data.profileImage").value(AVATAR.thumbnailUrl()));

        assertThat(userQuery.find(userId)).hasValueSatisfying(p -> {
            assertThat(p.nickname()).isEqualTo("새닉");
            assertThat(p.avatarId()).isEqualTo(1L);
            assertThat(p.profileImageUrl()).isEqualTo(AVATAR.thumbnailUrl());
            assertThat(p.onboardingRequired()).isFalse();
        });
    }

    @Test
    void memoryApisAreGoneButOldRowsStillReachTheConversation() throws Exception {
        // 일정 저장 · 조회(13 · 14)는 2026-11-24에 지웠다. 이미 저장된 일정은 대화 프롬프트가 계속 읽는다
        MvcResult user = signedUp(uniqueEmail());
        String auth = bearer(user);
        long userId = ((Number) read(user, "$.data.userId")).longValue();
        mvc.perform(post("/api/me/memories")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GOAL\",\"title\":\"토익 900\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/me/memories").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNotFound());

        LocalDate today = LocalDate.now(ClockConfig.KST);
        jdbc.update(
                "INSERT INTO user_memories (user_id, type, title, due_date) VALUES (?, 'EXAM', '영어 중간고사', ?)",
                userId,
                today.plusDays(5));
        jdbc.update(
                "INSERT INTO user_memories (user_id, type, title, due_date) VALUES (?, 'EXAM', '지난 시험', ?)",
                userId,
                today.minusDays(3));
        assertThat(memoryQuery.memoriesForConversation(userId))
                .extracting(MemoryInfo::title)
                .containsExactly("영어 중간고사");
    }

    @Test
    void loginTypeTellsKakaoAccountsApart() throws Exception {
        // 계정 설정 보안 탭의 '로그인 방식' (SCR-60): 비밀번호가 없으면 카카오 가입자
        MvcResult user = signedUp(uniqueEmail());
        String auth = bearer(user);
        long userId = ((Number) read(user, "$.data.userId")).longValue();
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.data.loginType").value("EMAIL"));
        jdbc.update("UPDATE users SET password = NULL, email = NULL WHERE id = ?", userId);
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.data.loginType").value("KAKAO"))
                .andExpect(jsonPath("$.data.email").value(nullValue()));
    }

    @Test
    void profileUpdateKeepsWhatIsNotSent() throws Exception {
        String auth = bearer(signedUp(uniqueEmail()));
        given(avatarQuery.defaultAvatar()).willReturn(Optional.of(AVATAR));
        given(avatarQuery.find(1)).willReturn(Optional.of(AVATAR));

        // H02 '나중에 바꿀게요': 빈 요청 → 기본 아바타, 닉네임은 가입 때 값 그대로
        patchMe(auth, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("승희"))
                .andExpect(jsonPath("$.data.onboardingRequired").value(false))
                .andExpect(jsonPath("$.data.profileImage").value(AVATAR.thumbnailUrl()));

        // H14에서 닉네임만 바꾸면 아바타는 그대로. 기본 아바타를 다시 찾지 않으므로
        // 기본 아바타가 없어도(AVATAR_NOT_FOUND가 날 상황) 200
        given(avatarQuery.defaultAvatar()).willReturn(Optional.empty());
        patchMe(auth, "{\"nickname\":\"바꾼닉\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("바꾼닉"))
                .andExpect(jsonPath("$.data.profileImage").value(AVATAR.thumbnailUrl()));
    }

    private ResultActions patchMe(String auth, String body) throws Exception {
        return mvc.perform(patch("/api/me")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
