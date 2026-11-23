package com.magister.backend.domain.user.dto;

import java.time.Instant;

public record UserActivityResponse(
        long workCount,
        long chapterCount,
        long unreviewedCount,
        RecentManuscriptSummary recentManuscript,
        AiQuotaSummary aiQuota) {
    public UserActivityResponse(
            long workCount,
            long chapterCount,
            long unreviewedCount,
            RecentManuscriptSummary recentManuscript) {
        this(workCount, chapterCount, unreviewedCount, recentManuscript, null);
    }

    public record RecentManuscriptSummary(
            Long workId,
            String workTitle,
            String chapterId,
            int chapterNo,
            String chapterTitle,
            Instant updatedAt) {}

    public record AiQuotaSummary(
            int textUsed,
            int textLimit,
            int textRemaining,
            int characterImageUsed,
            int characterImageLimit,
            int characterImageRemaining,
            Instant resetAt) {}
}
