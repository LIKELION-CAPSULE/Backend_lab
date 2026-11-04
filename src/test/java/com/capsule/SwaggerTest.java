package com.capsule;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        void swaggerUiOpenWithoutLogin() throws Exception {
            mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        }
    }
}
