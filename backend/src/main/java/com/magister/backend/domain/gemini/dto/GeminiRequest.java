package com.magister.backend.domain.gemini.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GeminiRequest(
        @NotBlank(message = "prompt is required")
                @Size(max = 2000, message = "prompt must not exceed 2000 characters")
                @Schema(description = "Gemini에 전달할 질문", example = "안녕하세요라고 짧게 답해줘.")
                String prompt) {}
