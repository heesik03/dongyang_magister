package com.magister.backend.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
public class SwaggerConfig {
    @Bean
    public OpenAPI magisterOpenApi() {
        Info info = new Info().title("Magister API").version("0.0.1");
        info.setDescription("Magister 백엔드 API 문서. Gemini 예시 실행 시 실제 API 호출이 발생합니다.");
        return new OpenAPI().info(info);
    }

    @Bean
    public OpenApiCustomizer sessionEndpoints() {
        return api -> {
            SecurityScheme session =
                    new SecurityScheme()
                            .type(SecurityScheme.Type.APIKEY)
                            .in(SecurityScheme.In.COOKIE)
                            .name("JSESSIONID");
            api.getComponents().addSecuritySchemes("session", session);
            // 로그아웃은 Security 필터가 처리하므로 Controller 없이 문서 경로를 등록한다.
            ApiResponses responses = new ApiResponses();
            responses.addApiResponse("200", new ApiResponse().description("로그아웃 성공"));
            responses.addApiResponse("403", new ApiResponse().description("CSRF 토큰 누락 또는 만료"));
            Operation logout = new Operation().summary("로그아웃").responses(responses);
            logout.setDescription("CSRF 헤더가 필요합니다. 세션을 무효화하고 세션 쿠키를 삭제합니다.");
            api.path("/api/user/logout", new PathItem().post(logout));
            SecurityRequirement requirement = new SecurityRequirement().addList("session");
            for (String path : List.of("/api/user/me", "/api/user/logout", "/api/gemini")) {
                if (api.getPaths().containsKey(path)) {
                    for (Operation operation : api.getPaths().get(path).readOperations()) {
                        operation.addSecurityItem(requirement);
                    }
                }
            }
        };
    }
}
