package com.magister.backend.domain.setting.dto;

import com.magister.backend.domain.setting.entity.*;

import jakarta.validation.constraints.*;

public record UpdateSettingRequest(
        @NotBlank @Size(max = 100) String title,
        @NotNull SettingKind kind,
        @NotNull SettingStatus status,
        @NotBlank @Size(max = 10_000) String content,
        @NotNull @PositiveOrZero Long version) {
    public UpdateSettingRequest {
        title = title == null ? null : title.strip();
    }
}
