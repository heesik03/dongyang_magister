package com.magister.backend.domain.analysis.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public final class RevisionSuggestion {
    private RevisionSuggestion() {}

    public record Target(
            String chapterId, long revision, int startOffset, int endOffset, String quote) {}

    public record Setting(String title, String quote) {}

    public record Previous(String title, String quote, String explanation) {}

    public record Material(
            String before,
            String quote,
            String after,
            String reason,
            String direction,
            List<Setting> settings,
            List<Previous> previous) {}

    public record Context(Target target, Material material, String contextHash) {}

    public record Request(@NotNull @Pattern(regexp = "[0-9a-f]{64}") String contextHash) {}

    public record Result(Target target, String replacement) {}
}
