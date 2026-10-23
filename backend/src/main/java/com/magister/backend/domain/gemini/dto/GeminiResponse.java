package com.magister.backend.domain.gemini.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record GeminiResponse(
        @Schema(description = "Gemini가 생성한 텍스트", example = "안녕하세요!") String text) {}
