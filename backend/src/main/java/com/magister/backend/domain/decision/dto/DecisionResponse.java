package com.magister.backend.domain.decision.dto;

import java.time.Instant;
import java.util.List;

public record DecisionResponse(
        Long id,
        String reviewId,
        String quote,
        long version,
        String status,
        String reason,
        String basisHash,
        boolean stale,
        boolean canReconfirm,
        Instant createdAt,
        List<Scope> scopes) {
    public record Scope(
            Long id,
            String scopeType,
            String fromChapterId,
            String toChapterId,
            Long sceneSourceId,
            Integer startOffset,
            Integer endOffset,
            String conditionText,
            String fromChapterTitle,
            String toChapterTitle) {}
}
