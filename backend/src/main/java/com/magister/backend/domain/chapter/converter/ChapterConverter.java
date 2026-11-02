package com.magister.backend.domain.chapter.converter;

import com.magister.backend.domain.chapter.dto.ChapterResponse;
import com.magister.backend.domain.chapter.dto.ManuscriptExportFile;
import com.magister.backend.domain.chapter.dto.ManuscriptImportResponse;
import com.magister.backend.domain.chapter.dto.ParsedManuscript;
import com.magister.backend.domain.chapter.entity.Chapter;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ChapterData;

import org.springframework.stereotype.Component;

@Component
public class ChapterConverter {
    public ChapterResponse toResponse(Chapter chapter) {
        return new ChapterResponse(
                chapter.getChapterKey(),
                chapter.getChapterNo(),
                chapter.getTitle(),
                chapter.getContent(),
                chapter.getRevision(),
                chapter.getVersion(),
                chapter.getUpdatedAt(),
                chapter.getWork().getVersion());
    }

    public ChapterData toWorkspaceChapter(Chapter chapter) {
        return new ChapterData(
                chapter.getChapterKey(),
                chapter.getTitle(),
                chapter.getContent(),
                chapter.getRevision());
    }

    public ManuscriptExportFile toExportFile(String filename, String contentType, byte[] content) {
        return new ManuscriptExportFile(filename, contentType, content);
    }

    public ManuscriptImportResponse toImportResponse(ParsedManuscript parsed) {
        return new ManuscriptImportResponse(
                parsed.workTitle(),
                parsed.genre(),
                parsed.chapterTitle(),
                parsed.content(),
                parsed.format().getExtension());
    }
}
