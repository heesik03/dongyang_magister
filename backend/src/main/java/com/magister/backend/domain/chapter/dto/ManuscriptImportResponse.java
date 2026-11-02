package com.magister.backend.domain.chapter.dto;

public record ManuscriptImportResponse(
        String workTitle, String genre, String chapterTitle, String content, String format) {}
