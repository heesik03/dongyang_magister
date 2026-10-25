package com.magister.backend.domain.work.dto;

import jakarta.validation.constraints.*;

public record UpdateWorkRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 30) String genre,
        @NotNull @Size(max = 100) String era,
        @NotNull @Size(max = 500) String description,
        @NotNull @PositiveOrZero Long version) {
    public UpdateWorkRequest {
        title = title == null ? null : title.strip();
        genre = genre == null ? null : genre.strip();
        era = era == null ? null : era.strip();
        description = description == null ? null : description.strip();
    }
}
