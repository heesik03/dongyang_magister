package com.magister.backend.domain.analysis.dto;

import java.time.Instant;

public record AnalysisJobSummary(
        String id,
        String chapterId,
        String chapterTitle,
        String status,
        Instant createdAt,
        Instant updatedAt,
        boolean previewConfirmed) {}
