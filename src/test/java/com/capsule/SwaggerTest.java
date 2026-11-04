package com.capsule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.common.config.ApiTags;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Swagger는 기본으로 꺼져 있고(운영), local처럼 켜면 로그인 없이 열린다 (ADR 0019) */
class SwaggerTest {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @Import(TestcontainersConfiguration.class)
    class OffByDefault {

        @Autowired
        MockMvc mvc;

        @Test
        void apiDocsNotServed() throws Exception {
            mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        }
    }

    @Nested
    @SpringBootTest(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @Import(TestcontainersConfiguration.class)
    class OnLikeLocal {

        @Autowired
        MockMvc mvc;

        @Test
        void apiDocsOpenWithoutLogin() throws Exception {
            mvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths['/api/terms']").exists())
                    .andExpect(jsonPath("$.components.securitySchemes.bearer.scheme")
                            .value("bearer"));
        }

        @Test
        void loginUserIsNotAnInput() throws Exception {
            // @LoginUser long userId는 토큰에서 채우므로 화면 입력칸(파라미터)이 아니다
            mvc.perform(get("/v3/api-docs"))
                    .andExpect(jsonPath("$.paths['/api/me'].get.parameters").doesNotExist())
                    .andExpect(jsonPath("$.paths['/api/sessions/{sessionId}/end'].post.parameters[*].name")
                            .value(contains("sessionId")));
        }

        @Test
        void tagsInSpecOrder() throws Exception {
            mvc.perform(get("/v3/api-docs"))
                    .andExpect(jsonPath("$.tags[*].name").value(contains("인증", "온보딩 · 홈", "공부 세션", "대화", "리포트")));
        }

        @Test
        void everyApiHasSpecTagAndSummary() throws Exception {
            // 새 API에 @Tag · @Operation을 빠뜨리면 여기서 걸린다 (빠지면 'xxx-controller' 묶음으로 따로 나옴)
            String json = mvc.perform(get("/v3/api-docs"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);
            int operations = JsonPath.<List<?>>read(json, "$.paths.*.*").size();
            List<String> tags = JsonPath.read(json, "$.paths.*.*.tags[*]");
            List<String> summaries = JsonPath.read(json, "$.paths.*.*.summary");
            List<String> known = List.of(
                    ApiTags.AUTH, ApiTags.ONBOARDING_HOME, ApiTags.STUDY_SESSION, ApiTags.DIALOGUE, ApiTags.REPORT);

            assertThat(tags).hasSize(operations).allMatch(known::contains);
            assertThat(summaries).hasSize(operations).allMatch(s -> s.matches("\\d+\\. .+")); // "명세 번호. 제목"
        }

        @Test
        void swaggerUiOpenWithoutLogin() throws Exception {
            mvc.perform(get("/swagger-ui.html"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/swagger-ui/index.html"));
            mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
            mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
        }
    }
}
