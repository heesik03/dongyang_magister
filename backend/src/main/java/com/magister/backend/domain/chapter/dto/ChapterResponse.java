package com.magister.backend.domain.chapter.dto;

import java.time.Instant;

public record ChapterResponse(
        String id,
        int chapterNo,
        String title,
        String content,
        long revision,
        long version,
        Instant updatedAt,
        long workVersion) {}
