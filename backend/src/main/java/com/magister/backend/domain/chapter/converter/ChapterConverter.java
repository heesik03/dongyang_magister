package com.magister.backend.domain.chapter.converter;

import com.magister.backend.domain.chapter.dto.ChapterResponse;
import com.magister.backend.domain.chapter.dto.CreateChapterRequest;
import com.magister.backend.domain.chapter.dto.ExportFormat;
import com.magister.backend.domain.chapter.dto.ManuscriptExportFile;
import com.magister.backend.domain.chapter.dto.ManuscriptImportResponse;
import com.magister.backend.domain.chapter.dto.ParsedManuscript;
import com.magister.backend.domain.chapter.dto.UpdateChapterRequest;
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

    public ParsedManuscript toParsedManuscript(
            String workTitle,
            String genre,
            String chapterTitle,
            String content,
            ExportFormat format) {
        return new ParsedManuscript(workTitle, genre, chapterTitle, content, format);
    }

    public CreateChapterRequest toCreateRequest(
            String id, String title, String content, String mutationId) {
        return new CreateChapterRequest(id, title, content, mutationId);
    }

    public UpdateChapterRequest toUpdateRequest(
            String title, String content, long version, String mutationId) {
        return new UpdateChapterRequest(title, content, version, mutationId);
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
