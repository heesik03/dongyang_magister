package com.magister.backend.domain.gemini.converter;

import com.magister.backend.domain.gemini.dto.GeminiResponse;

public final class GeminiConverter {
    private GeminiConverter() {}

    public static GeminiResponse toResponse(String text) {
        return new GeminiResponse(text);
    }
}
