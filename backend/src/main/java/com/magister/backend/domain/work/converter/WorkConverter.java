package com.magister.backend.domain.work.converter;

import com.magister.backend.domain.work.dto.WorkResponse;
import com.magister.backend.domain.work.entity.Work;

import java.time.Instant;

public final class WorkConverter {
    private WorkConverter() {}

    public static WorkResponse toResponse(Work work) {
        return new WorkResponse(
                work.getId(),
                work.getTitle(),
                work.getGenre(),
                work.getEra(),
                work.getDescription(),
                work.getCreatedAt(),
                work.getUpdatedAt(),
                work.getVersion());
    }
}
