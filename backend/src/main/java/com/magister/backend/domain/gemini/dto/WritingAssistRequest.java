package com.magister.backend.domain.gemini.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record WritingAssistRequest(
        @NotNull Action action,
        @NotBlank @Size(max = 1800) String excerpt,
        @NotNull @Size(max = 300) String before,
        @NotNull @Size(max = 300) String after,
        @NotNull @Size(max = 5) List<@NotNull @Positive Long> settingIds) {
    public enum Action {
        CONTINUE,
        DESCRIBE,
        DIALOGUE
    }
}
