package com.magister.backend.domain.work.converter;

import com.magister.backend.domain.work.dto.WorkResponse;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.repository.WorkSummary;

public final class WorkConverter {
    private WorkConverter() {}

    public static WorkResponse toResponse(Work work) {
        return toResponse(work, null);
    }

    public static WorkResponse toResponse(Work work, WorkSummary summary) {
        return new WorkResponse(
                work.getId(),
                work.getTitle(),
                work.getGenre(),
                work.getEra(),
                work.getDescription(),
                work.getCreatedAt(),
                work.getUpdatedAt(),
                work.getVersion(),
                summary == null ? 0 : summary.getChapterCount(),
                summary == null ? 0 : summary.getSettingCount(),
                summary == null ? null : summary.getLatestChapterId(),
                summary == null ? null : summary.getLatestChapterTitle());
    }
}
