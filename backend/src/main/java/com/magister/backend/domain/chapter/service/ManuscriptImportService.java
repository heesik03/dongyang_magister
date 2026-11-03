package com.magister.backend.domain.chapter.service;

import com.magister.backend.domain.chapter.converter.ChapterConverter;
import com.magister.backend.domain.chapter.dto.ChapterResponse;
import com.magister.backend.domain.chapter.dto.CreateChapterRequest;
import com.magister.backend.domain.chapter.dto.ExportFormat;
import com.magister.backend.domain.chapter.dto.ManuscriptImportResponse;
import com.magister.backend.domain.chapter.dto.ParsedManuscript;
import com.magister.backend.domain.chapter.dto.UpdateChapterRequest;
import com.magister.backend.domain.chapter.entity.Chapter;
import com.magister.backend.domain.chapter.error.ChapterErrorCode;
import com.magister.backend.domain.chapter.repository.ChapterRepository;
import com.magister.backend.domain.chapter.service.support.ManuscriptDocxSupport;
import com.magister.backend.domain.chapter.service.support.ManuscriptPdfSupport;
import com.magister.backend.domain.chapter.service.support.ManuscriptTextFormatter;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.work.service.WorkAccessService;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ManuscriptImportService {
    private static final int MAX_CONTENT_LENGTH = 200_000;
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_KEY_LENGTH = 64;
    private static final Pattern CHAPTER_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");

    private final WorkAccessService workAccessService;
    private final ChapterRepository chapterRepository;
    private final ChapterService chapterService;
    private final ChapterConverter chapterConverter;
    private final ManuscriptTextFormatter textFormatter;
    private final ManuscriptPdfSupport pdfSupport;
    private final ManuscriptDocxSupport docxSupport;

    /**
     * 업로드된 파일(txt, md, pdf, docx)에서 원고 본문과 메타데이터를 추출하여 반환한다.
     * DB 상태를 변경하지 않으며 본인 작품 소유권을 먼저 확인한다.
     */
    @Transactional(readOnly = true)
    public ManuscriptImportResponse parse(Long ownerId, Long workId, MultipartFile file) {
        workAccessService.requireOwned(ownerId, workId);
        ParsedManuscript parsed = parseMultipartFile(file);
        return chapterConverter.toImportResponse(parsed);
    }

    /**
     * 업로드된 파일에서 원고 내용을 추출하여 본인 작품의 새 회차로 생성·저장한다.
     * 제목을 별도로 지정하지 않으면 파일 내부 회차 헤더 또는 파일명을 회차 제목으로 사용한다.
     */
    @Transactional
    public ChapterResponse importAsNewChapter(
            Long ownerId,
            Long workId,
            MultipartFile file,
            String requestedId,
            String requestedTitle,
            String requestedMutationId) {
        ParsedManuscript parsed = parseMultipartFile(file);
        String resolvedId = resolveChapterKey(requestedId);
        String resolvedMutationId = resolveMutationId(requestedMutationId);
        String resolvedTitle = resolveTitle(requestedTitle, parsed.chapterTitle());

        CreateChapterRequest createRequest =
                chapterConverter.toCreateRequest(
                        resolvedId, resolvedTitle, parsed.content(), resolvedMutationId);
        return chapterService.create(ownerId, workId, createRequest);
    }

    /**
     * 업로드된 파일에서 원고 내용을 추출하여 본인 작품의 기존 회차 원고에 반영한다.
     * 회차 저장 버전(version)과 mutationId를 검증하여 동시 편집 충돌을 방지한다.
     */
    @Transactional
    public ChapterResponse importIntoChapter(
            Long ownerId,
            Long workId,
            String chapterId,
            MultipartFile file,
            long version,
            String mutationId,
            String requestedTitle) {
        if (version < 0 || mutationId == null || mutationId.isBlank() || mutationId.length() > MAX_KEY_LENGTH) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
        ParsedManuscript parsed = parseMultipartFile(file);
        workAccessService.requireOwned(ownerId, workId);
        Chapter existing =
                chapterRepository
                        .findByWork_IdAndChapterKey(workId, chapterId)
                        .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND));

        String resolvedTitle =
                (requestedTitle != null && !requestedTitle.isBlank())
                        ? resolveTitle(requestedTitle, existing.getTitle())
                        : existing.getTitle();

        UpdateChapterRequest updateRequest =
                chapterConverter.toUpdateRequest(
                        resolvedTitle, parsed.content(), version, mutationId.strip());
        return chapterService.update(ownerId, workId, chapterId, updateRequest);
    }

    private ParsedManuscript parseMultipartFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ChapterErrorCode.EMPTY_IMPORT_FILE);
        }
        ExportFormat format =
                ExportFormat.fromUpload(file.getOriginalFilename(), file.getContentType());
        String fallbackTitle =
                textFormatter.deriveChapterTitleFromFilename(file.getOriginalFilename());

        byte[] rawBytes;
        try {
            rawBytes = file.getBytes();
        } catch (IOException exception) {
            throw new CustomException(ChapterErrorCode.INVALID_MANUSCRIPT_FILE);
        }
        if (rawBytes.length == 0) {
            throw new CustomException(ChapterErrorCode.EMPTY_IMPORT_FILE);
        }

        ParsedManuscript parsed =
                switch (format) {
                    case TXT, MD ->
                            textFormatter.parseTextBytes(rawBytes, format, fallbackTitle);
                    case PDF -> pdfSupport.parsePdfBytes(rawBytes, fallbackTitle);
                    case DOCX -> docxSupport.parseDocxBytes(rawBytes, fallbackTitle);
                };

        if (parsed.content().length() > MAX_CONTENT_LENGTH) {
            throw new CustomException(ChapterErrorCode.MANUSCRIPT_TOO_LONG);
        }
        return parsed;
    }

    private String resolveChapterKey(String requestedId) {
        if (requestedId == null || requestedId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String trimmed = requestedId.strip();
        if (trimmed.length() > MAX_KEY_LENGTH
                || !CHAPTER_KEY_PATTERN.matcher(trimmed).matches()) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
        return trimmed;
    }

    private String resolveMutationId(String requestedMutationId) {
        if (requestedMutationId == null || requestedMutationId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String trimmed = requestedMutationId.strip();
        if (trimmed.length() > MAX_KEY_LENGTH) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
        return trimmed;
    }

    private String resolveTitle(String requestedTitle, String fallbackTitle) {
        String candidate =
                (requestedTitle != null && !requestedTitle.isBlank())
                        ? requestedTitle.strip()
                        : fallbackTitle.strip();
        if (candidate.isEmpty() || candidate.length() > MAX_TITLE_LENGTH) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
        return candidate;
    }
}
