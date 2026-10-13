package com.magister.backend.domain.gemini.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GeminiRequest(
        @NotBlank(message = "prompt is required")
        @Size(max = 2000, message = "prompt must not exceed 2000 characters") String prompt) {
}
