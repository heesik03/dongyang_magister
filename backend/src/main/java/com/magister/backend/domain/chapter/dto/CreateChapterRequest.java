package com.magister.backend.domain.chapter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateChapterRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String id,
        @NotBlank @Size(max = 100) String title,
        @NotNull @Size(max = 200000) String content,
        @NotBlank @Size(max = 64) String mutationId) {}
