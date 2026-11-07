package com.magister.backend.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(@NotBlank @Size(max = 30) String name) {
    public UpdateProfileRequest {
        name = name == null ? null : name.strip();
    }
}
