package com.magister.backend.domain.decision.dto;

import java.util.List;

/** 실제 전송한 판단 버전·범위·근거를 작업 입력에도 고정한다. */
public record DecisionReference(
        Long scopeId,
        Long decisionId,
        String reviewId,
        long version,
        String status,
        String reason,
        String basisHash,
        String quote,
        String scopeType,
        String fromChapterId,
        String toChapterId,
        Long sceneSourceId,
        Integer startOffset,
        Integer endOffset,
        String conditionText,
        List<String> evidenceQuotes,
        String fromChapterTitle,
        String toChapterTitle) {}
