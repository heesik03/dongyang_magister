package com.magister.backend.domain.chapter.dto;

import com.magister.backend.domain.chapter.error.ChapterErrorCode;
import com.magister.backend.global.error.exception.CustomException;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

@Getter
@RequiredArgsConstructor
public enum ExportFormat {
    TXT("txt", "text/plain; charset=UTF-8"),
    MD("md", "text/markdown; charset=UTF-8"),
    PDF("pdf", "application/pdf"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final String extension;
    private final String contentType;

    public static ExportFormat fromQuery(String rawFormat) {
        if (rawFormat == null || rawFormat.isBlank()) {
            return TXT;
        }
        String normalized = rawFormat.strip().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        return switch (normalized) {
            case "txt", "text" -> TXT;
            case "md", "markdown" -> MD;
            case "pdf" -> PDF;
            case "docx" -> DOCX;
            default -> throw new CustomException(ChapterErrorCode.UNSUPPORTED_EXPORT_FORMAT);
        };
    }

    public static ExportFormat fromUpload(String originalFilename, String contentType) {
        if (originalFilename != null && !originalFilename.isBlank()) {
            String trimmed = originalFilename.strip();
            int dotIndex = trimmed.lastIndexOf('.');
            if (dotIndex >= 0 && dotIndex + 1 < trimmed.length()) {
                String extension = trimmed.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
                return switch (extension) {
                    case "txt" -> TXT;
                    case "md", "markdown" -> MD;
                    case "pdf" -> PDF;
                    case "docx" -> DOCX;
                    default ->
                            throw new CustomException(ChapterErrorCode.UNSUPPORTED_IMPORT_FORMAT);
                };
            }
        }
        if (contentType != null && !contentType.isBlank()) {
            String normalizedType = contentType.strip().toLowerCase(Locale.ROOT);
            if (normalizedType.startsWith("text/plain")) {
                return TXT;
            }
            if (normalizedType.startsWith("text/markdown")
                    || normalizedType.startsWith("text/x-markdown")) {
                return MD;
            }
            if (normalizedType.startsWith("application/pdf")) {
                return PDF;
            }
            if (normalizedType.startsWith(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
                return DOCX;
            }
        }
        throw new CustomException(ChapterErrorCode.UNSUPPORTED_IMPORT_FORMAT);
    }
}
