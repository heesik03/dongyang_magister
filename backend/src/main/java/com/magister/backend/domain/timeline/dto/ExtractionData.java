package com.magister.backend.domain.timeline.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** 회차 스냅샷과 사건 후보는 확정된 타임라인과 분리해서 보관한다. */
public final class ExtractionData {
    private ExtractionData() {}

    public record Selection(@NotBlank @Size(max = 64) String id, @Min(1) long revision) {}

    public record PreviewRequest(
            @NotEmpty @Size(max = 10) List<@Valid @NotNull Selection> chapters) {}

    public record Source(String id, String title, long revision, int chapterNo, String content) {}

    public record Preview(List<Source> sources, String token, String model) {}

    public record CreateRequest(
            @NotEmpty @Size(max = 10) List<@Valid @NotNull Selection> chapters,
            @NotBlank @Size(max = 100) String token,
            @AssertTrue boolean consent,
            @NotBlank @Size(max = 64) String requestId) {}

    public record Input(List<Source> sources, String model) {}

    public record Candidate(
            @NotBlank @Size(max = 36) String id,
            @NotBlank @Size(max = 100) String title,
            @NotNull @Size(max = 2000) String description,
            @NotNull @Size(max = 100) String time,
            @NotNull @Size(max = 500) String characters,
            @NotBlank @Size(max = 64) String chapterId,
            long sourceRevision,
            int startOffset,
            int endOffset,
            @NotBlank @Size(max = 10000) String quote,
            @NotNull @Pattern(regexp = "HOLD|INCLUDE|EXCLUDE") String decision,
            @NotNull @Pattern(regexp = "CONFIRMED|UNRESOLVED") String timeStatus,
            @Min(0) int sequence) {}

    public record SaveCandidates(
            @Min(0) long version,
            @NotNull @Size(max = 100) List<@NotNull @Valid Candidate> candidates) {}

    public record ConfirmationRequest(
            @Min(0) long version,
            @Min(0) long workspaceVersion,
            @NotBlank @Size(max = 64) String mutationId) {}

    public record Job(
            String id,
            String status,
            long version,
            Instant createdAt,
            String error,
            List<Candidate> candidates,
            List<Source> sources) {}
}
