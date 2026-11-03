package com.magister.backend.domain.chapter.service.support;

import com.magister.backend.domain.chapter.converter.ChapterConverter;
import com.magister.backend.domain.chapter.dto.ExportFormat;
import com.magister.backend.domain.chapter.dto.ParsedManuscript;
import com.magister.backend.domain.chapter.entity.Chapter;
import com.magister.backend.domain.chapter.error.ChapterErrorCode;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.global.error.exception.CustomException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * TXT·MD 원고 내보내기 문자열 조립과 가져오기 시 UTF-8 디코딩·Magister 상단 헤더 분리를 담당한다.
 */
@Component
@RequiredArgsConstructor
public class ManuscriptTextFormatter {
    private static final String WORK_TITLE_PREFIX = "제목: ";
    private static final String GENRE_PREFIX = "장르: ";
    private static final String CHAPTER_TITLE_PREFIX = "회차: ";
    private static final String HEADER_SEPARATOR = "---";

    private final ChapterConverter chapterConverter;

    public byte[] formatChapter(Work work, Chapter chapter) {
        StringBuilder builder = new StringBuilder();
        builder.append(WORK_TITLE_PREFIX).append(work.getTitle()).append("\n");
        builder.append(GENRE_PREFIX).append(work.getGenre()).append("\n");
        builder.append(CHAPTER_TITLE_PREFIX).append(chapter.getTitle()).append("\n");
        builder.append(HEADER_SEPARATOR).append("\n\n");
        builder.append(chapter.getContent());
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] formatWork(Work work, List<Chapter> chapters) {
        StringBuilder builder = new StringBuilder();
        builder.append(WORK_TITLE_PREFIX).append(work.getTitle()).append("\n");
        builder.append(GENRE_PREFIX).append(work.getGenre()).append("\n");
        builder.append(HEADER_SEPARATOR).append("\n\n");

        for (int index = 0; index < chapters.size(); index++) {
            Chapter chapter = chapters.get(index);
            if (index > 0) {
                builder.append("\n\n").append(HEADER_SEPARATOR).append("\n\n");
            }
            builder.append("=== ").append(chapter.getTitle()).append(" ===\n\n");
            builder.append(chapter.getContent());
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    public ParsedManuscript parseTextBytes(
            byte[] rawBytes, ExportFormat format, String fallbackChapterTitle) {
        String decoded = decodeUtf8Strict(rawBytes);
        return extractHeaderAndBody(decoded, format, fallbackChapterTitle);
    }

    public ParsedManuscript extractHeaderAndBody(
            String rawText, ExportFormat format, String fallbackChapterTitle) {
        String normalized = stripBom(rawText).replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);

        String workTitle = "";
        String genre = "";
        String chapterTitle = "";
        int bodyStartLine = 0;

        if (lines.length >= 3
                && lines[0].startsWith(WORK_TITLE_PREFIX)
                && lines[1].startsWith(GENRE_PREFIX)) {
            int cursor = 2;
            if (cursor < lines.length && lines[cursor].startsWith(CHAPTER_TITLE_PREFIX)) {
                chapterTitle = lines[cursor].substring(CHAPTER_TITLE_PREFIX.length()).strip();
                cursor++;
            }
            if (cursor < lines.length && lines[cursor].strip().equals(HEADER_SEPARATOR)) {
                workTitle = lines[0].substring(WORK_TITLE_PREFIX.length()).strip();
                genre = lines[1].substring(GENRE_PREFIX.length()).strip();
                cursor++;
                if (cursor < lines.length && lines[cursor].isEmpty()) {
                    cursor++;
                }
                bodyStartLine = cursor;
            }
        }

        if (bodyStartLine < lines.length) {
            String firstBodyLine = lines[bodyStartLine].strip();
            if (firstBodyLine.startsWith("=== ")
                    && firstBodyLine.endsWith(" ===")
                    && firstBodyLine.length() > 8) {
                if (chapterTitle.isEmpty()) {
                    chapterTitle =
                            firstBodyLine.substring(4, firstBodyLine.length() - 4).strip();
                }
                bodyStartLine++;
                if (bodyStartLine < lines.length && lines[bodyStartLine].isEmpty()) {
                    bodyStartLine++;
                }
            }
        }

        StringBuilder bodyBuilder = new StringBuilder();
        for (int index = bodyStartLine; index < lines.length; index++) {
            if (index > bodyStartLine) {
                bodyBuilder.append("\n");
            }
            bodyBuilder.append(lines[index]);
        }

        String resolvedChapterTitle =
                chapterTitle.isBlank() ? fallbackChapterTitle : truncateTitle(chapterTitle);
        return chapterConverter.toParsedManuscript(
                workTitle, genre, resolvedChapterTitle, bodyBuilder.toString(), format);
    }

    public String deriveChapterTitleFromFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "가져온 원고";
        }
        String name = originalFilename.strip();
        int slashIndex = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slashIndex >= 0 && slashIndex + 1 < name.length()) {
            name = name.substring(slashIndex + 1);
        }
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            name = name.substring(0, dotIndex);
        }
        String cleaned = name.strip();
        if (cleaned.isEmpty()) {
            return "가져온 원고";
        }
        return truncateTitle(cleaned);
    }

    public String buildExportFilename(
            String workTitle, String chapterTitle, ExportFormat format) {
        String safeWorkTitle = sanitizeFilenamePart(workTitle, "work");
        if (chapterTitle == null || chapterTitle.isBlank()) {
            return safeWorkTitle + "." + format.getExtension();
        }
        String safeChapterTitle = sanitizeFilenamePart(chapterTitle, "chapter");
        return safeWorkTitle + "_" + safeChapterTitle + "." + format.getExtension();
    }

    private String sanitizeFilenamePart(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String sanitized =
                value.strip().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", "_").strip();
        if (sanitized.isEmpty()) {
            return fallback;
        }
        if (sanitized.length() > 60) {
            return sanitized.substring(0, 60).strip();
        }
        return sanitized;
    }

    private String truncateTitle(String title) {
        String trimmed = title.strip();
        if (trimmed.length() > 100) {
            return trimmed.substring(0, 100).strip();
        }
        return trimmed;
    }

    private String decodeUtf8Strict(byte[] rawBytes) {
        CharsetDecoder decoder =
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            CharBuffer decoded = decoder.decode(ByteBuffer.wrap(rawBytes));
            return decoded.toString();
        } catch (CharacterCodingException exception) {
            throw new CustomException(ChapterErrorCode.INVALID_MANUSCRIPT_FILE);
        }
    }

    private String stripBom(String text) {
        if (text != null && !text.isEmpty() && text.charAt(0) == '\uFEFF') {
            return text.substring(1);
        }
        return text == null ? "" : text;
    }
}
