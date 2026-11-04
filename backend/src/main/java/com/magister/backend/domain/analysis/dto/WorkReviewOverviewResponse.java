package com.magister.backend.domain.analysis.dto;

import java.time.Instant;
import java.util.List;

public record WorkReviewOverviewResponse(
        Long workId,
        int totalChapters,
        int analyzedChapters,
        int currentRevisionChapters,
        int outdatedRevisionChapters,
        int unanalyzedChapters,
        int totalConflictCount,
        int totalSupplementCount,
        int totalUnreviewedCount,
        String aggregationBasisNote,
        List<ChapterReviewSummary> chapters) {

    public record ChapterReviewSummary(
            String chapterId,
            int chapterNo,
            String chapterTitle,
            long currentRevision,
            String analysisStatus,
            String latestJobId,
            Long analyzedRevision,
            Instant analyzedAt,
            int settingCount,
            int previousChapterCount,
            int analysisRunCount,
            int totalCandidateCount,
            int conflictCount,
            int supplementCount,
            int unreviewedCount,
            int acceptedCount,
            int holdCount,
            int intendedCount) {}
}
