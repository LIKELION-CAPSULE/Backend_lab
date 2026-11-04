package com.capsule.dialogue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.TestcontainersConfiguration;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 실험실 토큰을 넣었을 때: 화면만 열리고 API는 X-Lab-Token이 맞아야 한다. 인코딩한 주소로 돌아가도 막힌다 */
@SpringBootTest(properties = "capsule.llm.lab.token=lab-secret")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class LabSecurityTest {

    @Autowired
    MockMvc mvc;

    @Test
    void apiNeedsTheTokenButThePageDoesNot() throws Exception {
        mvc.perform(get("/internal/llm-lab")).andExpect(status().isOk());
        mvc.perform(get("/internal/llm-lab/flow")).andExpect(status().isOk());

        mvc.perform(get("/internal/llm-lab/api/meta")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/llm-lab/api/meta").header("X-Lab-Token", "wrong"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/llm-lab/api/meta").header("X-Lab-Token", "lab-secret"))
                .andExpect(status().isOk());

        // %61 = 'a'. 예전 검사(주소에 /api/가 들어 있는지)는 이 주소를 통과시켰다
        mvc.perform(get(URI.create("/internal/llm-lab/%61pi/meta"))).andExpect(status().is4xxClientError());
    }
}
