package com.capsule.user;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 같은 이메일 가입이 동시에 와서 중복 확인을 둘 다 지난 경우 (api.md 4, #40). 저장소를 감싸야 해서 컨텍스트를 따로 쓴다 */
class SignupRaceTest extends UserApiTestSupport {

    @MockitoSpyBean
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void lateSignupOfSameEmailIs409Not500() throws Exception {
        String email = uniqueEmail();
        String token = verificationToken(email);
        // 다른 요청이 먼저 저장을 끝냈지만, 이 요청의 중복 확인은 그 전에 지나간 상황
        jdbc.update("INSERT INTO users (email, password, nickname) VALUES (?, 'hash', '먼저')", email);
        doReturn(false).when(users).existsByEmail(eq(email));

        signup(token, PASSWORD, "나중", "[3,4]")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
    }
}
