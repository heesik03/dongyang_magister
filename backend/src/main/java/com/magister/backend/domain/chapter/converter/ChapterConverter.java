package com.magister.backend.domain.chapter.converter;

import com.magister.backend.domain.chapter.dto.ChapterResponse;
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
}
