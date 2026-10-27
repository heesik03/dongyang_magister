package com.magister.backend.domain.work.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateWorkRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 30) String genre,
        @NotNull @Size(max = 100) String era,
        @NotNull @Size(max = 500) String description) {
    public CreateWorkRequest {
        title = title == null ? null : title.strip();
        genre = genre == null ? null : genre.strip();
        era = era == null ? null : era.strip();
        description = description == null ? null : description.strip();
    }
}
