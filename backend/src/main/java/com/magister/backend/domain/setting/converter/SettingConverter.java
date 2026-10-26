package com.magister.backend.domain.setting.converter;

import com.magister.backend.domain.setting.dto.SettingResponse;
import com.magister.backend.domain.setting.entity.*;

import java.time.Instant;

public final class SettingConverter {
    private SettingConverter() {}

    public static SettingResponse toResponse(WorkSetting setting) {
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
