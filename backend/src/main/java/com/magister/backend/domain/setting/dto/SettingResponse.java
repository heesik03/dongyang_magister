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
        Long version) {}
