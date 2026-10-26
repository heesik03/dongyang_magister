package com.magister.backend.domain.analysis.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 공급자 응답 전용 경계 타입이며 검증 후 내부 검토 DTO로 변환한다. */
public record AnalysisOutput(@NotNull @Size(max = 100) List<@NotNull @Valid Candidate> reviews) {
    public record Candidate(
            @NotNull @Pattern(regexp = "설정 충돌 후보|설명 보완") String type,
            @NotBlank @Size(max = 10000) String quote,
            @NotBlank @Size(max = 64) String settingId,
            @NotBlank @Size(max = 4000) String reason,
            @NotBlank @Size(max = 4000) String suggestion) {}
}
