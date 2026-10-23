package com.magister.backend.global.gemini;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GeminiProperties.class)
public class GeminiConfig {

    @Bean(destroyMethod = "close")
    public Client geminiSdkClient(GeminiProperties properties) {
        return Client.builder()
                .apiKey(properties.apiKey())
                .vertexAI(false)
                .httpOptions(
                        HttpOptions.builder()
                                .timeout(Math.toIntExact(properties.timeout().toMillis()))
                                // 생성 요청의 중복 실행과 과금을 피하기 위해 SDK 재시도를 비활성화한다.
                                .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                                .build())
                .build();
    }

    @Bean
    public GeminiClient geminiClient(Client geminiSdkClient, GeminiProperties properties) {
        return new GeminiClient(geminiSdkClient.models, properties);
    }
}
