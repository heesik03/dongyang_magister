package com.magister.backend.domain.chapter.service;

import com.magister.backend.domain.chapter.converter.ChapterConverter;
import com.magister.backend.domain.chapter.entity.Chapter;
import com.magister.backend.domain.chapter.repository.ChapterRepository;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ChapterData;
import com.magister.backend.global.error.exception.CustomException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 단계 전환 동안 workspace·기존 분석에 회차 테이블의 원고를 제공하는 호환 경계. */
@Service
@RequiredArgsConstructor
public class ChapterWorkspaceService {
    private final ChapterRepository chapterRepository;
    private final ChapterConverter chapterConverter;

    /** 소유권 확인이 끝난 트랜잭션에서 최신 원고를 읽는다. 작품당 최대 100회차다. */
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<ChapterData> read(Long workId) {
        return chapterRepository.findByWork_IdOrderByChapterNoAsc(workId).stream()
                .map(chapterConverter::toWorkspaceChapter)
                .toList();
    }

    /** 최초 구형 초안 가져오기만 허용한다. 이후 구형 API가 회차 저장 버전을 우회하면 409로 막는다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void acceptLegacy(Work work, long workspaceRevision, List<ChapterData> requested) {
        List<ChapterData> current = read(work.getId());
        if (current.equals(requested)) {
            return;
        }
        if (!current.isEmpty() || workspaceRevision != 0) {
            throw new CustomException(WorkErrorCode.VERSION_CONFLICT);
        }
        int number = 0;
        for (ChapterData chapter : requested) {
            chapterRepository.save(
                    new Chapter(
                            work,
                            chapter.id(),
                            ++number,
                            chapter.title(),
                            chapter.content(),
                            chapter.revision(),
                            "legacy-import"));
        }
        chapterRepository.flush();
    }
}
