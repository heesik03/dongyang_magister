package com.magister.backend.domain.analysis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HistoryDecision(@NotNull Choice choice, @NotBlank @Size(max = 500) String reason) {
    public enum Choice { ACCEPT, HOLD, INTENDED }
}
