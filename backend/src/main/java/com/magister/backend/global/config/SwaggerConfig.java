package com.magister.backend.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SwaggerConfig {

    @Bean
    public OpenAPI magisterOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Magister API")
                .description("Magister 백엔드 API 문서. Gemini 예시 실행 시 실제 API 호출이 발생합니다.")
                .version("0.0.1"));
    }
}
