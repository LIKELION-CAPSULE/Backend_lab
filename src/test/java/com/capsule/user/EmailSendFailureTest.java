package com.capsule.user;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.TestcontainersConfiguration;
import com.capsule.user.client.EmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 메일 발송이 실패하면 500이고, 1분 제한에 걸리지 않고 바로 다시 요청할 수 있다 (api.md 2) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class EmailSendFailureTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    EmailSender emailSender;

    @Test
    void failedSendDoesNotBlockRetry() throws Exception {
        String body = "{\"email\":\"send-fail@example.com\"}";
        doThrow(new IllegalStateException("메일 서비스 장애"))
                .when(emailSender)
                .sendVerificationCode(anyString(), anyString());

        request(body).andExpect(status().isInternalServerError());

        doNothing().when(emailSender).sendVerificationCode(anyString(), anyString());
        request(body).andExpect(status().isAccepted()); // 429가 아님
    }

    private org.springframework.test.web.servlet.ResultActions request(String body) throws Exception {
        return mvc.perform(post("/api/auth/email/verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
