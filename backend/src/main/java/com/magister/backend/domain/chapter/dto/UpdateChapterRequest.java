package com.magister.backend.domain.chapter.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateChapterRequest(
        @NotBlank @Size(max = 100) String title,
        @NotNull @Size(max = 200000) String content,
        @Min(0) long version,
        @NotBlank @Size(max = 64) String mutationId) {}
