package com.magister.backend.domain.history.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class HistoryCheck {
    private HistoryCheck() {}

    public record Request(@NotBlank @Size(max = 6000)
            @Schema(example = "1392년, 사신은 경복궁에 도착했다.") String manuscript) {}

    public enum Kind { EXISTENCE, OTHER }
    public enum Status { POSSIBLE_CONFLICT, NO_CONFLICT_FOUND, INSUFFICIENT_EVIDENCE, LOOKUP_FAILED }

    public record Claim(@NotBlank @Size(max = 500) String quote,
            @NotNull @Min(1) @Max(6000) Integer occurrence,
            @NotBlank @Size(max = 60) @Pattern(regexp = "[가-힣ㄱ-ㅎㅏ-ㅣ一-龥A-Za-z0-9· ]+") String target,
            @NotNull @Size(max = 60) String sceneTime, @NotBlank @Size(max = 200) String assertion,
            @NotNull Kind kind, @NotNull @Size(min = 1, max = 4) List<@NotBlank @Size(max = 30) String> keywords) {}

    public record Extraction(@NotNull @Size(max = 3) List<@NotNull @Valid Claim> claims) {}

    public record Finding(String quote, int startOffset, int endOffset, String sceneTime,
            String target, String assertion, Status status, String reason, List<HistoryEvidence> evidence) {}

    public record Assessment(@NotNull @Min(0) @Max(2) Integer index, @NotNull Status status,
            @NotBlank @Size(max = 350) String reason, @NotNull @Size(max = 500) String citation,
            @NotNull @Min(0) @Max(9999) Integer foundingYear) {}

    public record Assessments(@NotNull @Size(max = 3) List<@NotNull @Valid Assessment> assessments) {}
}
