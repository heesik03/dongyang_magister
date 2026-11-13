package com.magister.backend.global.gemini;

import com.google.genai.Models;
import com.google.genai.errors.ApiException;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Blob;
import com.google.genai.types.Part;
import com.magister.backend.global.error.code.GeminiErrorCode;
import com.magister.backend.global.error.exception.GeminiApiException;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.InterruptedIOException;

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
        return generateText(
                prompt, GenerateContentConfig.builder().maxOutputTokens(maxOutputTokens).build());
    }

    /** 원고 검토는 짧은 연결 확인과 분리한다. 조건·예외 비교를 위한 추론과 JSON 출력을 사용한다. */
    public String generateAnalysis(String prompt) {
        return generateText(prompt, GeminiAnalysisConfig.create(properties.model()));
    }

    /** 사건 추출 전용 JSON 계약을 사용하며 기존 설정 검토의 응답 스키마는 유지한다. */
    public String generateEvents(String prompt) {
        return generateText(prompt, GeminiEventConfig.create());
    }

    /** 장면 분석은 별도 JSON 출력을 사용한다. */
    public String generateScene(String prompt) {
        return generateText(prompt, GeminiSceneConfig.create());
    }

    /** Gemini 이미지 응답에서 크기와 서명이 맞는 PNG/JPEG 한 장만 사용한다. */
    public GeneratedPortrait generatePortrait(String model, String prompt) {
        GenerateContentResponse response = generate(
                model, prompt, GenerateContentConfig.builder().responseModalities("IMAGE").build());
        if (response != null && response.parts() != null) {
            for (Part part : response.parts()) {
                Blob data = part.inlineData().orElse(null);
                if (data == null) {
                    continue;
                }
                byte[] bytes = data.data().orElse(null);
                String mimeType = data.mimeType().orElse("");
                if (validImage(mimeType, bytes)) {
                    return new GeneratedPortrait(mimeType, bytes);
                }
            }
        }
        throw new GeminiApiException(GeminiErrorCode.INVALID_RESPONSE);
    }

    private boolean validImage(String mimeType, byte[] bytes) {
        if (bytes == null || bytes.length < 8 || bytes.length > 5 * 1024 * 1024) {
            return false;
        }
        return switch (mimeType) {
            case "image/png" -> bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e
                    && bytes[3] == 0x47 && bytes[4] == 0x0d && bytes[5] == 0x0a
                    && bytes[6] == 0x1a && bytes[7] == 0x0a;
            case "image/jpeg" -> bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8
                    && bytes[2] == (byte) 0xff && bytes[bytes.length - 2] == (byte) 0xff
                    && bytes[bytes.length - 1] == (byte) 0xd9;
            default -> false;
        };
    }

    public record GeneratedPortrait(String mimeType, byte[] bytes) {}

    private String generateText(String prompt, GenerateContentConfig config) {
        GenerateContentResponse response = generate(properties.model(), prompt, config);
        String text = response == null ? null : response.text();
        if (text == null || text.isBlank()) {
            throw new GeminiApiException(GeminiErrorCode.INVALID_RESPONSE);
        }
        return text;
    }

    private GenerateContentResponse generate(String model, String prompt, GenerateContentConfig config) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }

        try {
            return models.generateContent(model, prompt, config);
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

    }

    private GeminiErrorCode mapStatus(int status) {
        return switch (status) {
            case 401, 403 -> GeminiErrorCode.AUTHENTICATION_FAILED;
            case 408, 504 -> GeminiErrorCode.TIMEOUT;
            case 429 -> GeminiErrorCode.RATE_LIMITED;
            default ->
                    status >= 500 ? GeminiErrorCode.UNAVAILABLE : GeminiErrorCode.REQUEST_REJECTED;
        };
    }
}
