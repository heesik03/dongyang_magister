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
        List<Evidence> evidences,
        String issueGroupKey,
        String issueGroupTitle,
        String confirmedFact,
        String interpretation,
        String missingInfo,
        List<RevisionOption> options) {
    public ReviewResponse(
            String id,
            String type,
            String quote,
            Long targetSourceId,
            int startOffset,
            int endOffset,
            String reason,
            String suggestion,
            List<Evidence> evidences) {
        this(
                id,
                type,
                quote,
                targetSourceId,
                startOffset,
                endOffset,
                reason,
                suggestion,
                evidences,
                null,
                null,
                null,
                null,
                null,
                List.of());
    }

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

    public record RevisionOption(
            String title, String direction, String premise, String rationale) {}
}
