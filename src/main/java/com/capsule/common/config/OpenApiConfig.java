package com.capsule.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger 화면 설정 (ADR 0019). 명세 원본은 docs/specs/api.md이고, 이 화면은 지금 코드의 실제 모양을 보여준다.
 * Authorize 버튼에 액세스 토큰(Bearer 없이 값만)을 넣으면 로그인이 필요한 API를 부를 수 있다.
 */
@Configuration
class OpenApiConfig {

    private static final String BEARER = "bearer";

    @Bean
    OpenAPI capsuleOpenApi() {
        return new OpenAPI()
                // 상대 주소로 고정: Railway 프록시 뒤에서 첫 요청의 http 주소가 캐시되어 https 화면에서 막히는 일을 피한다
                .servers(List.of(new Server().url("/")))
                .info(new Info()
                        .title("캡슐 API")
                        .description("명세 원본: docs/specs/api.md")
                        .version("dev"))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
