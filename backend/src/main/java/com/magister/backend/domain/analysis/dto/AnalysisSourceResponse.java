package com.magister.backend.domain.analysis.dto;

public record AnalysisSourceResponse(
        Long id,
        String type,
        String sourceId,
        long revision,
        String title,
        String content,
        String metadata,
        String contentHash) {}
