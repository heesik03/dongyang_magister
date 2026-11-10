package com.magister.backend.domain.scene;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class SceneBoardData {
    private SceneBoardData() {}

    public record Selection(
            @NotBlank @Size(max = 64) String chapterId,
            @Positive long revision,
            @PositiveOrZero int start,
            @Positive int end,
            @NotNull @Size(max = 10) List<@NotNull @Positive Long> settingIds) {}

    public record CreateRequest(
            @NotBlank @Size(max = 64) String requestId,
            @NotNull @Valid Selection selection,
            @NotBlank @Size(max = 100) String token,
            boolean consent) {}

    public record Setting(long id, String title, String content) {}
    public record Snapshot(Selection selection, String chapterTitle, String text, List<Setting> settings) {}
    public record Preview(Snapshot snapshot, String token) {}
    public record Fact(String description, String quote, int start, int end) {}
    public record Reference(String id, String title, String author, String location, String url, String technique) {}
    public record Recommendation(Reference reference, String question) {}
    public record Result(
            List<Fact> facts,
            List<String> inferences,
            List<String> gaps,
            List<String> options,
            List<Recommendation> references) {}
    public record Board(String id, String status, Snapshot snapshot, Result result) {}
}
