package com.magister.backend.domain.user.dto;

import java.time.Instant;

public record UserActivityResponse(
        long workCount,
        long chapterCount,
        long unreviewedCount,
        RecentManuscriptSummary recentManuscript) {
    public record RecentManuscriptSummary(
            Long workId,
            String workTitle,
            String chapterId,
            int chapterNo,
            String chapterTitle,
            Instant updatedAt) {}
}
