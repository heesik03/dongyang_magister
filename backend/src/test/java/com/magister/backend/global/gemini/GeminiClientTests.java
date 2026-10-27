package com.magister.backend.global.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.genai.Models;
import com.google.genai.errors.ApiException;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Schema;
import com.magister.backend.global.error.code.GeminiErrorCode;
import com.magister.backend.global.error.exception.GeminiApiException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.net.SocketTimeoutException;
import java.time.Duration;

class GeminiClientTests {

    private final Models models = mock(Models.class);
    private final GeminiClient client =
            new GeminiClient(
                    models,
                    new GeminiProperties("test-secret", "test-model", Duration.ofSeconds(30)));

    @Test
    void analysisUsesJsonAndHighThinkingForGeminiThreeButLeavesOtherModelsCompatible() {
        when(models.generateContent(
                        any(String.class), eq("manuscript"), any(GenerateContentConfig.class)))
                .thenReturn(
                        GenerateContentResponse.fromJson(
                                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{}\"}]}}]}"));
        GeminiClient analysisClient =
                new GeminiClient(
                        models,
                        new GeminiProperties(
                                "test-secret", "gemini-3.5-flash-lite", Duration.ofSeconds(30)));
        analysisClient.generateAnalysis("manuscript");
        ArgumentCaptor<GenerateContentConfig> captured =
                ArgumentCaptor.forClass(GenerateContentConfig.class);
        verify(models)
                .generateContent(eq("gemini-3.5-flash-lite"), eq("manuscript"), captured.capture());
        assertThat(captured.getValue().maxOutputTokens()).contains(8192);
        assertThat(captured.getValue().responseMimeType()).contains("application/json");
        assertThat(
                        captured.getValue()
                                .thinkingConfig()
                                .orElseThrow()
                                .thinkingLevel()
                                .orElseThrow()
                                .toString())
                .isEqualTo("HIGH");
        Schema candidate =
                captured.getValue()
                        .responseSchema()
                        .orElseThrow()
                        .properties()
                        .orElseThrow()
                        .get("reviews")
                        .items()
                        .orElseThrow();
        assertThat(candidate.required().orElseThrow())
                .contains("quote", "evidences", "startOffset", "endOffset");
        Schema sourceType =
                candidate
                        .properties()
                        .orElseThrow()
                        .get("evidences")
                        .items()
                        .orElseThrow()
                        .properties()
                        .orElseThrow()
                        .get("sourceType");
        assertThat(sourceType.enum_().orElseThrow()).containsExactly("SETTING", "PREVIOUS");
        analysisClient.generateText("manuscript", 1024);
        verify(models, times(2))
                .generateContent(eq("gemini-3.5-flash-lite"), eq("manuscript"), captured.capture());
        assertThat(captured.getValue().maxOutputTokens()).contains(1024);
        assertThat(captured.getValue().thinkingConfig()).isEmpty();
        assertThat(captured.getValue().responseMimeType()).isEmpty();
        assertThat(captured.getValue().responseSchema()).isEmpty();
        client.generateAnalysis("manuscript");
        verify(models).generateContent(eq("test-model"), eq("manuscript"), captured.capture());
        assertThat(captured.getValue().thinkingConfig()).isEmpty();
    }

    @Test
    void returnsGeneratedText() {
        when(models.generateContent(
                        eq("test-model"), eq("hello"), any(GenerateContentConfig.class)))
                .thenReturn(
                        GenerateContentResponse.fromJson(
                                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"answer\"}]}}]}"));
        assertThat(client.generateText("hello")).isEqualTo("answer");
    }

    @Test
    void rejectsBlankInputWithoutCallingProvider() {
        assertThatThrownBy(() -> client.generateText(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.generateText(null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(models);
    }

    @Test
    void rejectsEmptyResponse() {
        when(models.generateContent(
                        eq("test-model"), eq("hello"), any(GenerateContentConfig.class)))
                .thenReturn(GenerateContentResponse.fromJson("{}"));
        assertError(GeminiErrorCode.INVALID_RESPONSE);
    }

    @ParameterizedTest
    @CsvSource({
        "401,AUTHENTICATION_FAILED",
        "403,AUTHENTICATION_FAILED",
        "429,RATE_LIMITED",
        "408,TIMEOUT",
        "504,TIMEOUT",
        "500,UNAVAILABLE",
        "400,REQUEST_REJECTED"
    })
    void translatesProviderErrorsWithoutExposingBody(int status, GeminiErrorCode code) {
        when(models.generateContent(
                        eq("test-model"), eq("hello"), any(GenerateContentConfig.class)))
                .thenThrow(new ApiException(status, "ERROR", "sensitive-provider-body"));
        assertError(code);
    }

    @Test
    void translatesNetworkTimeout() {
        when(models.generateContent(
                        eq("test-model"), eq("hello"), any(GenerateContentConfig.class)))
                .thenThrow(
                        new RuntimeException(
                                new SocketTimeoutException("sensitive-provider-body")));
        assertError(GeminiErrorCode.TIMEOUT);
    }

    private void assertError(GeminiErrorCode code) {
        assertThatThrownBy(() -> client.generateText("hello"))
                .isInstanceOfSatisfying(
                        GeminiApiException.class,
                        exception -> {
                            assertThat(exception.getErrorCode()).isEqualTo(code);
                            assertThat(exception.getMessage())
                                    .doesNotContain("sensitive-provider-body");
                            assertThat(exception.getCause()).isNull();
                        });
    }
}
