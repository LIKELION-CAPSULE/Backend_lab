package com.capsule.dialogue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/** 관리자 계정이 있으면 실험실(운영 설정 포함)은 로그인해야 쓴다. 이 컴퓨터에서 온 요청도 마찬가지 (ADR 0027) */
@SpringBootTest(properties = "capsule.admin.users.tester={noop}pw-1234")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class LabLoginTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    ResultActions login(String password, String from) throws Exception {
        return mvc.perform(post("/internal/llm-lab/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"tester\", \"password\": \"" + password + "\"}")
                .with(r -> {
                    r.setRemoteAddr(from);
                    return r;
                }));
    }

    @Test
    void loginIsRequiredAndTheTokenOpensTheLab() throws Exception {
        mvc.perform(get("/internal/llm-lab/api/meta"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LAB_LOGIN_REQUIRED"));
        mvc.perform(get("/internal/llm-lab")).andExpect(status().isOk()); // 화면은 열려 있어야 로그인 칸이 뜬다

        String from = "10.1.0." + (UUID.randomUUID().hashCode() & 0xff);
        login("틀림", from)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        String body = login("pw-1234", from)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("tester"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = json.readTree(body).path("token").asString();

        mvc.perform(get("/internal/llm-lab/api/meta").header("X-Lab-Token", token))
                .andExpect(status().isOk());
        mvc.perform(put("/internal/llm-lab/api/settings/relation")
                        .header("X-Lab-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mvc.perform(get("/internal/llm-lab/api/meta").header("X-Lab-Token", token + "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tooManyAttemptsFromOneAddressAreRefused() throws Exception {
        String from = "10.2." + (UUID.randomUUID().hashCode() & 0xff) + ".1";
        for (int i = 0; i < 10; i++) login("틀림", from).andExpect(status().isUnauthorized());
        login("pw-1234", from).andExpect(status().isTooManyRequests());
    }
}
