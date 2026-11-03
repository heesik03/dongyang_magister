package com.magister.backend.domain.chapter.dto;

public record ManuscriptExportFile(String filename, String contentType, byte[] content) {}
