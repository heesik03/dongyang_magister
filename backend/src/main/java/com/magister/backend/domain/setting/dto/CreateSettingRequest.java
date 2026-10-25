package com.magister.backend.domain.setting.dto;

import com.magister.backend.domain.setting.entity.*;

import jakarta.validation.constraints.*;

public record CreateSettingRequest(
        @NotBlank @Size(max = 100) String title,
        @NotNull SettingKind kind,
        @NotNull SettingStatus status,
        @NotBlank @Size(max = 10_000) String content) {
    public CreateSettingRequest {
        title = title == null ? null : title.strip();
    }
}
