package com.magister.backend.global.gemini;

import java.io.IOException;
import java.io.InterruptedIOException;

import com.google.genai.Models;
import com.google.genai.errors.ApiException;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.magister.backend.global.error.code.GeminiErrorCode;
import com.magister.backend.global.error.exception.GeminiApiException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class GeminiClient {

    private final Models models;
    private final GeminiProperties properties;

    public String generateText(String prompt) {
        return generateText(prompt, GenerateContentConfig.builder().build());
    }

    public String generateText(String prompt, int maxOutputTokens) {
        if (maxOutputTokens < 1) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
        return generateText(prompt, GenerateContentConfig.builder()
                .maxOutputTokens(maxOutputTokens).build());
    }

    private String generateText(String prompt, GenerateContentConfig config) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }

        GenerateContentResponse response;
        try {
            response = models.generateContent(properties.model(), prompt, config);
        } catch (ApiException exception) {
            throw new GeminiApiException(mapStatus(exception.code()));
        } catch (RuntimeException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof InterruptedIOException) {
                    throw new GeminiApiException(GeminiErrorCode.TIMEOUT);
                }
                if (cause instanceof IOException) {
                    throw new GeminiApiException(GeminiErrorCode.UNAVAILABLE);
                }
            }
            throw exception;
        }

        String text = response == null ? null : response.text();
        if (text == null || text.isBlank()) {
            throw new GeminiApiException(GeminiErrorCode.INVALID_RESPONSE);
        }
        return text;
    }

    private GeminiErrorCode mapStatus(int status) {
        return switch (status) {
            case 401, 403 -> GeminiErrorCode.AUTHENTICATION_FAILED;
            case 408, 504 -> GeminiErrorCode.TIMEOUT;
            case 429 -> GeminiErrorCode.RATE_LIMITED;
            default -> status >= 500 ? GeminiErrorCode.UNAVAILABLE : GeminiErrorCode.REQUEST_REJECTED;
        };
    }
}
