package com.magister.backend.global.gemini;

import java.net.SocketTimeoutException;
import java.time.Duration;

import com.google.genai.Models;
import com.google.genai.errors.ApiException;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GeminiClientTests {

    private final Models models = mock(Models.class);
    private final GeminiClient client = new GeminiClient(models,
            new GeminiProperties("test-secret", "test-model", Duration.ofSeconds(30)));

    @Test
    void returnsGeneratedText() {
        when(models.generateContent(eq("test-model"), eq("hello"), any(GenerateContentConfig.class)))
                .thenReturn(GenerateContentResponse.fromJson(
                        "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"answer\"}]}}]}"));
        assertThat(client.generateText("hello")).isEqualTo("answer");
    }

    @Test
    void rejectsBlankInputWithoutCallingProvider() {
        assertThatThrownBy(() -> client.generateText(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.generateText(null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(models);
    }

    @Test
    void rejectsEmptyResponse() {
        when(models.generateContent(eq("test-model"), eq("hello"), any(GenerateContentConfig.class)))
                .thenReturn(GenerateContentResponse.fromJson("{}"));
        assertError(GeminiApiException.ErrorCode.INVALID_RESPONSE);
    }

    @ParameterizedTest
    @CsvSource({"401,AUTHENTICATION_FAILED", "403,AUTHENTICATION_FAILED", "429,RATE_LIMITED",
            "408,TIMEOUT", "504,TIMEOUT", "500,UNAVAILABLE", "400,REQUEST_REJECTED"})
    void translatesProviderErrorsWithoutExposingBody(int status, GeminiApiException.ErrorCode code) {
        when(models.generateContent(eq("test-model"), eq("hello"), any(GenerateContentConfig.class)))
                .thenThrow(new ApiException(status, "ERROR", "sensitive-provider-body"));
        assertError(code);
    }

    @Test
    void translatesNetworkTimeout() {
        when(models.generateContent(eq("test-model"), eq("hello"), any(GenerateContentConfig.class)))
                .thenThrow(new RuntimeException(new SocketTimeoutException("sensitive-provider-body")));
        assertError(GeminiApiException.ErrorCode.TIMEOUT);
    }

    private void assertError(GeminiApiException.ErrorCode code) {
        assertThatThrownBy(() -> client.generateText("hello"))
                .isInstanceOfSatisfying(GeminiApiException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(code);
                    assertThat(exception.getMessage()).doesNotContain("sensitive-provider-body");
                    assertThat(exception.getCause()).isNull();
                });
    }
}
