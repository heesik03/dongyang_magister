package com.magister.backend.domain.workspace.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

// 호환 응답은 회차 테이블의 원고를 합친다. workspace의 영속 JSON에는 검토·판단·사건만 저장한다.
public record WorkspaceData(
        @NotNull @Size(max = 100) List<@NotNull @Valid ChapterData> chapters,
        @NotNull @Size(max = 100) List<@NotNull @Valid AnalysisData> analyses,
        @NotNull @Size(max = 1000) List<@NotNull @Valid EventData> events) {
    public static WorkspaceData empty() {
        return new WorkspaceData(List.of(), List.of(), List.of());
    }

    public record ChapterData(
            @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String id,
            @NotBlank @Size(max = 100) String title,
            @NotNull @Size(max = 200000) String content,
            @Min(1) long revision) {}

    public record ReviewData(
            @NotBlank @Size(max = 128) String id,
            @NotNull @Pattern(regexp = "설정 충돌 후보|설명 보완") String type,
            @NotBlank @Size(max = 10000) String quote,
            @NotBlank @Size(max = 73) String settingId,
            @NotNull @Size(max = 10000) String evidence,
            @NotBlank @Size(max = 4000) String reason,
            @NotBlank @Size(max = 4000) String suggestion,
            @NotNull @Pattern(regexp = "미검토|수용|보류|의도된 설정") String decision,
            @NotNull @Size(max = 4000) String note) {}

    public record AnalysisData(
            @NotBlank @Size(max = 64) String chapterId,
            @Min(1) long revision,
            @NotNull @Size(max = 200000) String source,
            @NotNull @Size(max = 1000) List<@NotBlank @Size(max = 64) String> settingIds,
            @NotNull @Size(max = 100) List<@NotBlank @Size(max = 64) String> previousIds,
            @NotNull @Size(max = 100) List<@NotNull @Valid ReviewData> reviews,
            @NotBlank @Size(max = 50) String createdAt,
            @Size(max = 1000000) String settingsSnapshot) {}

    public record EventData(
            @NotBlank @Size(max = 64) String id,
            @NotBlank @Size(max = 100) String title,
            @NotNull @Size(max = 100) String time,
            @NotNull @Size(max = 500) String characters,
            @NotNull @Size(max = 64) String chapterId,
            @NotNull @Size(max = 10000) String quote) {}
}
