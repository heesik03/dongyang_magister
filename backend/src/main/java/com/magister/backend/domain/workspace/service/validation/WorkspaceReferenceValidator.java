package com.magister.backend.domain.workspace.service.validation;

import com.magister.backend.domain.workspace.dto.WorkspaceData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.AnalysisData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ChapterData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.EventData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ReviewData;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Set;

@Component
public class WorkspaceReferenceValidator {
    /** 중복 ID·끊어진 참조·근거 불일치·사유 없는 의도 처리를 저장 전에 거절한다. */
    public void validate(WorkspaceData data) {
        Set<String> chapterIds = new HashSet<>();
        for (ChapterData chapter : data.chapters()) {
            if (!chapterIds.add(chapter.id())) {
                invalid();
            }
        }
        Set<String> analysisChapterIds = new HashSet<>();
        for (AnalysisData analysis : data.analyses()) {
            if (!chapterIds.contains(analysis.chapterId())
                    || !analysisChapterIds.add(analysis.chapterId())
                    || !chapterIds.containsAll(analysis.previousIds())) {
                invalid();
            }
            try {
                Instant.parse(analysis.createdAt());
            } catch (DateTimeParseException error) {
                invalid();
            }
            Set<String> reviewIds = new HashSet<>();
            Set<String> settingIds = new HashSet<>(analysis.settingIds());
            for (ReviewData review : analysis.reviews()) {
                if (!reviewIds.add(review.id())
                        || !analysis.source().contains(review.quote())
                        || !(settingIds.contains(review.settingId())
                                || (review.settingId().startsWith("previous:")
                                        && analysis.previousIds()
                                                .contains(review.settingId().substring(9))))) {
                    invalid();
                }
                if (review.decision().equals("의도된 설정") && review.note().isBlank()) {
                    invalid();
                }
            }
        }
        Set<String> eventIds = new HashSet<>();
        for (EventData event : data.events()) {
            if (!eventIds.add(event.id())
                    || (!event.chapterId().isEmpty() && !chapterIds.contains(event.chapterId()))) {
                invalid();
            }
            boolean located =
                    event.startOffset() != null
                            || event.endOffset() != null
                            || event.sourceRevision() != null;
            if (located) {
                ChapterData chapter =
                        data.chapters().stream()
                                .filter(item -> item.id().equals(event.chapterId()))
                                .findFirst()
                                .orElse(null);
                if (chapter == null
                        || event.startOffset() == null
                        || event.endOffset() == null
                        || event.sourceRevision() == null
                        || event.sourceRevision() > chapter.revision()
                        || event.startOffset() < 0
                        || event.endOffset() <= event.startOffset()
                        || event.endOffset() - event.startOffset() != event.quote().length()) {
                    invalid();
                }
                // 과거 버전의 근거는 보존한다. 현재 버전이라고 주장한 인용은 정확한 위치까지 검사한다.
                if (event.sourceRevision() == chapter.revision()
                        && (event.endOffset() > chapter.content().length()
                                || !chapter.content()
                                        .substring(event.startOffset(), event.endOffset())
                                        .equals(event.quote()))) {
                    invalid();
                }
            }
        }
    }

    private void invalid() {
        throw new CustomException(CommonErrorCode.INVALID_REQUEST);
    }
}
