package com.magister.backend.domain.chapter.dto;

public record ParsedManuscript(
        String workTitle, String genre, String chapterTitle, String content, ExportFormat format) {}
