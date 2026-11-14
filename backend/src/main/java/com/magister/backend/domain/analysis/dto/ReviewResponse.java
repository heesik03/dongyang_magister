package com.magister.backend.domain.analysis.dto;

import java.util.List;

public record ReviewResponse(
        String id,
        String type,
        String quote,
        Long targetSourceId,
        int startOffset,
        int endOffset,
        String reason,
        String suggestion,
        List<Evidence> evidences) {
    public record Evidence(
            Long sourceId,
            String sourceType,
            String sourceKey,
            String title,
            long revision,
            String quote,
            int startOffset,
            int endOffset,
            String explanation) {}
}
