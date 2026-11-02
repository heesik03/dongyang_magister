package com.magister.backend.domain.chapter.service;

import com.magister.backend.domain.chapter.converter.ChapterConverter;
import com.magister.backend.domain.chapter.dto.ExportFormat;
import com.magister.backend.domain.chapter.dto.ManuscriptExportFile;
import com.magister.backend.domain.chapter.entity.Chapter;
import com.magister.backend.domain.chapter.error.ChapterErrorCode;
import com.magister.backend.domain.chapter.repository.ChapterRepository;
import com.magister.backend.domain.chapter.service.support.ManuscriptDocxSupport;
import com.magister.backend.domain.chapter.service.support.ManuscriptPdfSupport;
import com.magister.backend.domain.chapter.service.support.ManuscriptTextFormatter;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.work.service.WorkAccessService;
import com.magister.backend.global.error.exception.CustomException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ManuscriptExportService {
    private final WorkAccessService workAccessService;
    private final ChapterRepository chapterRepository;
    private final ChapterConverter chapterConverter;
    private final ManuscriptTextFormatter textFormatter;
    private final ManuscriptPdfSupport pdfSupport;
    private final ManuscriptDocxSupport docxSupport;

    /**
     * 본인 작품의 특정 회차 원고를 요청 포맷(txt, md, pdf, docx)으로 내보낸다.
     * txt·md는 마크다운 원문을 유지하고, pdf·docx는 마크다운을 제목 크기·서식으로 변환한다.
     */
    @Transactional(readOnly = true)
    public ManuscriptExportFile exportChapter(
            Long ownerId, Long workId, String chapterId, String rawFormat) {
        ExportFormat format = ExportFormat.fromQuery(rawFormat);
        Work work = workAccessService.requireOwned(ownerId, workId);
        Chapter chapter =
                chapterRepository
                        .findByWork_IdAndChapterKey(workId, chapterId)
                        .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND));

        byte[] content =
                switch (format) {
                    case TXT, MD -> textFormatter.formatChapter(work, chapter);
                    case PDF -> pdfSupport.exportChapter(work, chapter);
                    case DOCX -> docxSupport.exportChapter(work, chapter);
                };

        String filename =
                textFormatter.buildExportFilename(work.getTitle(), chapter.getTitle(), format);
        return chapterConverter.toExportFile(filename, format.getContentType(), content);
    }

    /**
     * 본인 작품의 전체 회차를 회차 번호 오름차순으로 통합하여 요청 포맷으로 내보낸다.
     * 등록된 회차가 없으면 400(EMPTY_EXPORT_CHAPTERS)으로 응답한다.
     */
    @Transactional(readOnly = true)
    public ManuscriptExportFile exportWork(Long ownerId, Long workId, String rawFormat) {
        ExportFormat format = ExportFormat.fromQuery(rawFormat);
        Work work = workAccessService.requireOwned(ownerId, workId);
        List<Chapter> chapters = chapterRepository.findByWork_IdOrderByChapterNoAsc(workId);
        if (chapters.isEmpty()) {
            throw new CustomException(ChapterErrorCode.EMPTY_EXPORT_CHAPTERS);
        }

        byte[] content =
                switch (format) {
                    case TXT, MD -> textFormatter.formatWork(work, chapters);
                    case PDF -> pdfSupport.exportWork(work, chapters);
                    case DOCX -> docxSupport.exportWork(work, chapters);
                };

        String filename = textFormatter.buildExportFilename(work.getTitle(), null, format);
        return chapterConverter.toExportFile(filename, format.getContentType(), content);
    }
}
