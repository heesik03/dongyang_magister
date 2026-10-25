package com.magister.backend.domain.setting.dto;

import com.magister.backend.domain.setting.entity.*;

import java.time.Instant;

public record SettingResponse(
        Long id,
        Long workId,
        String title,
        SettingKind kind,
        SettingStatus status,
        String content,
        Instant createdAt,
        Instant updatedAt,
        Long version) {
    public static SettingResponse from(WorkSetting setting) {
        return new SettingResponse(
                setting.getId(),
                setting.getWork().getId(),
                setting.getTitle(),
                setting.getKind(),
                setting.getStatus(),
                setting.getContent(),
                setting.getCreatedAt(),
                setting.getUpdatedAt(),
                setting.getVersion());
    }
}
