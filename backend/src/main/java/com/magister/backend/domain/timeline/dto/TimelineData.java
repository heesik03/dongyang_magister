package com.magister.backend.domain.timeline.dto;

import com.magister.backend.domain.workspace.dto.WorkspaceData.EventData;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class TimelineData {
    private TimelineData() {}

    public record SaveEventRequest(
            @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String id,
            @NotBlank @Size(max = 100) String title,
            @NotNull @Size(max = 2000) String description,
            @NotNull @Size(max = 100) String time,
            @NotNull @Size(max = 500) String characters,
            @NotNull @Size(max = 64) String chapterId,
            @NotNull @Size(max = 10000) String quote,
            @Min(0) Integer sequence,
            @Pattern(regexp = "CONFIRMED|UNRESOLVED") String timeStatus,
            @Min(1) Long sourceRevision,
            @Min(0) Integer startOffset,
            @Min(1) Integer endOffset,
            @Size(max = 36) String extractionId) {}

    public record ReplaceTimelineRequest(
            @NotNull @Size(max = 1000) List<@NotNull @Valid EventData> events) {}
}
