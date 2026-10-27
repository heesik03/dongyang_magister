package com.magister.backend.domain.decision.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DecisionRequest(
        @NotNull @Pattern(regexp = "미검토|수용|보류|의도된 설정") String status,
        @NotNull @Size(max = 4000) String reason,
        @Min(0) long expectedVersion,
        @NotBlank @Size(max = 64) String mutationId,
        @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String basisHash,
        boolean reconfirm,
        @NotNull @Size(max = 10) List<@NotNull @Valid Scope> scopes) {
    public record Scope(
            @NotNull @Pattern(regexp = "CHAPTER_RANGE|SCENE") String scopeType,
            @Size(max = 64) String fromChapterId,
            @Size(max = 64) String toChapterId,
            @Positive Long sceneSourceId,
            @Min(0) Integer startOffset,
            @Min(1) Integer endOffset,
            @NotBlank @Size(max = 2000) String conditionText) {}
}
