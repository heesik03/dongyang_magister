package com.magister.backend.domain.analysis.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AnalysisRequest(
        @NotBlank @Size(max = 64) String requestId,
        @NotBlank @Size(max = 64) String chapterId,
        @Min(0) long workspaceVersion,
        @NotEmpty @Size(max = 1000) List<@NotBlank String> settingIds,
        @NotNull @Size(max = 100) List<@NotBlank String> previousIds,
        boolean consistency,
        boolean motivation) {}
