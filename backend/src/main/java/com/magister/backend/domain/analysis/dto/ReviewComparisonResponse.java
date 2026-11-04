package com.magister.backend.domain.analysis.dto;

import java.time.Instant;
import java.util.List;

public record ReviewComparisonResponse(
        String currentJobId,
        String baseJobId,
        boolean hasBaseJob,
        String chapterId,
        String chapterTitle,
        long currentChapterRevision,
        Long baseChapterRevision,
        Instant currentAnalyzedAt,
        Instant baseAnalyzedAt,
        boolean scopeChanged,
        boolean manuscriptModified,
        String scopeComparisonNote,
        String resolutionCautionNote,
        List<ComparedCandidate> retained,
        List<ComparedCandidate> added,
        List<ComparedCandidate> notReappeared,
        List<ComparedCandidate> incomparable) {

    public record ComparedCandidate(
            String category,
            String candidateId,
            String matchedPreviousCandidateId,
            String type,
            String quote,
            int startOffset,
            int endOffset,
            Integer previousStartOffset,
            Integer previousEndOffset,
            String reason,
            String suggestion,
            List<String> sourceTitles,
            String comparisonNote) {}
}
