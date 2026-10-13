package com.magister.backend.global.gemini;

import java.io.IOException;
import java.io.InterruptedIOException;

import com.google.genai.Models;
import com.google.genai.errors.ApiException;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;

import static com.magister.backend.global.gemini.GeminiApiException.ErrorCode;

public class GeminiClient {

    private final Models models;
    private final GeminiProperties properties;

    GeminiClient(Models models, GeminiProperties properties) {
        this.models = models;
        this.properties = properties;
    }

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
                    throw new GeminiApiException(ErrorCode.TIMEOUT);
                }
                if (cause instanceof IOException) {
                    throw new GeminiApiException(ErrorCode.UNAVAILABLE);
                }
            }
            throw exception;
        }

        String text = response == null ? null : response.text();
        if (text == null || text.isBlank()) {
            throw new GeminiApiException(ErrorCode.INVALID_RESPONSE);
        }
        return text;
    }

    private ErrorCode mapStatus(int status) {
        return switch (status) {
            case 401, 403 -> ErrorCode.AUTHENTICATION_FAILED;
            case 408, 504 -> ErrorCode.TIMEOUT;
            case 429 -> ErrorCode.RATE_LIMITED;
            default -> status >= 500 ? ErrorCode.UNAVAILABLE : ErrorCode.REQUEST_REJECTED;
        };
    }
}
