package com.capsule.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
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
        long userId = ((Number) read(user, "$.userId")).longValue();
        given(avatarQuery.defaultAvatar()).willReturn(Optional.of(AVATAR));
        given(avatarQuery.find(1)).willReturn(Optional.of(AVATAR));
        given(avatarQuery.find(2)).willReturn(Optional.of(INACTIVE));

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.nickname").value("승희"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.onboardingRequired").value(true))
                .andExpect(jsonPath("$.activeSession").value(nullValue()))
                .andExpect(jsonPath("$.profileImage").value(nullValue()));

        patchMe(auth, "{\"nickname\":\"\"}").andExpect(jsonPath("$.code").value("INVALID_NICKNAME"));
        patchMe(auth, "{\"nickname\":\"새닉\",\"avatarId\":99}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AVATAR_NOT_FOUND"));
        patchMe(auth, "{\"nickname\":\"새닉\",\"avatarId\":2}")
                .andExpect(jsonPath("$.code").value("AVATAR_NOT_FOUND"));
        patchMe(auth, "{\"nickname\":\" 새닉 \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("새닉"))
                .andExpect(jsonPath("$.onboardingRequired").value(false))
                .andExpect(jsonPath("$.profileImage").value(AVATAR.thumbnailUrl()));

        given(activeSessionLookup.find(userId))
                .willReturn(Optional.of(new ActiveSessionSummary(
                        512, 1, "쌀쌀맞은 갸루짝꿍이 나에게만 친절하다!", "채린", Instant.parse("2026-11-07T05:00:02Z"))));
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.activeSession.sessionId").value(512))
                .andExpect(jsonPath("$.activeSession.characterName").value("채린"))
                .andExpect(jsonPath("$.activeSession.startedAt").value("2026-11-07T05:00:02Z"))
                .andExpect(jsonPath("$.profileImage").value(AVATAR.thumbnailUrl()));

        assertThat(userQuery.find(userId)).hasValueSatisfying(p -> {
            assertThat(p.nickname()).isEqualTo("새닉");
            assertThat(p.avatarId()).isEqualTo(1L);
            assertThat(p.profileImageUrl()).isEqualTo(AVATAR.thumbnailUrl());
            assertThat(p.onboardingRequired()).isFalse();
        });
    }

    @Test
    void memorySaveRules() throws Exception {
        String auth = bearer(signedUp(uniqueEmail()));
        LocalDate today = LocalDate.now(ClockConfig.KST);

        for (String invalid : new String[] {
            "{\"type\":\"EXAM\",\"title\":\"시험\"}", // 시험인데 날짜 없음
            "{\"type\":\"EXAM\",\"title\":\"시험\",\"dueDate\":\"%s\"}".formatted(today.minusDays(1)), // 지난 날짜
            "{\"type\":\"GOAL\",\"title\":\"목표\",\"dueTime\":\"20:00\"}", // 날짜 없이 시간만
            "{\"type\":\"EXAM\",\"title\":\"시험\",\"dueDate\":\"2099/11/08\"}", // 형식 오류
            "{\"type\":\"EXAM\",\"title\":\"시험\",\"dueDate\":\"%s\",\"dueTime\":\"8pm\"}".formatted(today),
            "{\"type\":\"TRIP\",\"title\":\"여행\",\"dueDate\":\"%s\"}".formatted(today),
            "{\"type\":\"EXAM\",\"title\":\"  \",\"dueDate\":\"%s\"}".formatted(today) // 빈 제목
        }) {
            createMemory(auth, invalid)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_MEMORY"));
        }

        createMemory(
                        auth,
                        "{\"type\":\"PROMISE\",\"title\":\"\",\"dueDate\":\"%s\",\"dueTime\":\"20:00\"}"
                                .formatted(today.plusDays(1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memoryId").isNumber())
                .andExpect(jsonPath("$.type").value("PROMISE"))
                .andExpect(jsonPath("$.title").value("같이 공부하기"))
                .andExpect(jsonPath("$.dueDate").value(today.plusDays(1).toString()))
                .andExpect(jsonPath("$.dueTime").value("20:00"))
                .andExpect(jsonPath("$.dDay").value(1));
        createMemory(auth, "{\"type\":\"GOAL\",\"title\":\"토익 900\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dueDate").value(nullValue()))
                .andExpect(jsonPath("$.dDay").value(nullValue()));
        // 시간은 약속에서만: 시험에 시간이 와도 종일로 저장
        createMemory(
                        auth,
                        "{\"type\":\"EXAM\",\"title\":\"모의고사\",\"dueDate\":\"%s\",\"dueTime\":\"09:00\"}"
                                .formatted(today.plusDays(2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dueTime").value(nullValue()));
    }

    @Test
    void memoryListing() throws Exception {
        MvcResult user = signedUp(uniqueEmail());
        String auth = bearer(user);
        long userId = ((Number) read(user, "$.userId")).longValue();
        LocalDate today = LocalDate.now(ClockConfig.KST);
        createMemory(auth, "{\"type\":\"EXAM\",\"title\":\"영어 중간고사\",\"dueDate\":\"%s\"}".formatted(today.plusDays(5)))
                .andExpect(status().isCreated());
        createMemory(auth, "{\"type\":\"PROMISE\",\"title\":\"도서관\",\"dueDate\":\"%s\"}".formatted(today.plusDays(1)))
                .andExpect(status().isCreated());
        createMemory(
                        auth,
                        "{\"type\":\"PROMISE\",\"title\":\"같이 공부하기\",\"dueDate\":\"%s\",\"dueTime\":\"20:00\"}"
                                .formatted(today.plusDays(1)))
                .andExpect(status().isCreated());
        createMemory(auth, "{\"type\":\"GOAL\",\"title\":\"토익 900\"}").andExpect(status().isCreated());
        // 지난 일정은 API로 못 만들어서 직접 넣는다
        jdbc.update(
                "INSERT INTO user_memories (user_id, type, title, due_date) VALUES (?, 'EXAM', '지난 시험', ?)",
                userId,
                today.minusDays(3));

        listMemories(auth)
                .andExpect(jsonPath("$.memories[*].title").value(contains("같이 공부하기", "도서관", "영어 중간고사")))
                .andExpect(jsonPath("$.memories[2].dDay").value(5))
                .andExpect(jsonPath("$.datesWithMemories")
                        .value(contains(
                                today.minusDays(3).toString(),
                                today.plusDays(1).toString(),
                                today.plusDays(5).toString())));
        listMemories(auth, "status", "PAST")
                .andExpect(jsonPath("$.memories[*].title").value(contains("지난 시험")))
                .andExpect(jsonPath("$.memories[0].dDay").value(-3));
        listMemories(auth, "status", "ALL")
                .andExpect(jsonPath("$.memories[*].title")
                        .value(contains("지난 시험", "같이 공부하기", "도서관", "영어 중간고사", "토익 900")));
        listMemories(auth, "date", today.plusDays(5).toString())
                .andExpect(jsonPath("$.memories[*].title").value(contains("영어 중간고사")));
        listMemories(auth, "type", "PROMISE")
                .andExpect(jsonPath("$.memories[*].title").value(contains("같이 공부하기", "도서관")));
        listMemories(auth, "from", today.toString(), "to", today.plusDays(3).toString())
                .andExpect(jsonPath("$.memories[*].title").value(contains("같이 공부하기", "도서관")))
                .andExpect(jsonPath("$.datesWithMemories")
                        .value(contains(today.plusDays(1).toString())));
        listMemories(
                        auth,
                        "from",
                        today.plusDays(10).toString(),
                        "to",
                        today.plusDays(20).toString())
                .andExpect(jsonPath("$.memories").value(empty()));
        // 날짜 없는 목표는 기간과 상관없이 '전체'의 맨 아래
        listMemories(auth, "from", today.toString(), "to", today.plusDays(3).toString(), "status", "ALL")
                .andExpect(jsonPath("$.memories[*].title").value(contains("같이 공부하기", "도서관", "토익 900")));

        listMemories(auth, "from", today.toString(), "to", today.minusDays(1).toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
        listMemories(auth, "from", today.toString(), "to", today.plusDays(100).toString())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));

        assertThat(memoryQuery.memoriesForConversation(userId))
                .extracting(MemoryInfo::title)
                .containsExactly("같이 공부하기", "도서관", "영어 중간고사", "토익 900");
    }

    private ResultActions patchMe(String auth, String body) throws Exception {
        return mvc.perform(patch("/api/me")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions createMemory(String auth, String body) throws Exception {
        return mvc.perform(post("/api/me/memories")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** params = 이름, 값, 이름, 값 ... */
    private ResultActions listMemories(String auth, String... params) throws Exception {
        var request = get("/api/me/memories").header(HttpHeaders.AUTHORIZATION, auth);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request);
    }
}
