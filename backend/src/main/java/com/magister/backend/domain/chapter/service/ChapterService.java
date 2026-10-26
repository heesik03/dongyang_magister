package com.magister.backend.domain.chapter.service;

import com.magister.backend.domain.chapter.converter.ChapterConverter;
import com.magister.backend.domain.chapter.dto.ChapterResponse;
import com.magister.backend.domain.chapter.dto.CreateChapterRequest;
import com.magister.backend.domain.chapter.dto.UpdateChapterRequest;
import com.magister.backend.domain.chapter.entity.Chapter;
import com.magister.backend.domain.chapter.repository.ChapterRepository;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.work.service.WorkAccessService;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.pagination.OffsetPageRequest;
import com.magister.backend.global.pagination.OffsetPageResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChapterService {
    private final WorkAccessService workAccessService;
    private final ChapterRepository chapterRepository;
    private final ChapterConverter chapterConverter;

    /** 작품 잠금으로 번호 할당을 직렬화하고 같은 ID·mutation의 생성 재시도를 중복 저장하지 않는다. */
    @Transactional
    public ChapterResponse create(Long ownerId, Long workId, CreateChapterRequest request) {
        Work work = workAccessService.lockOwned(ownerId, workId);
        Optional<Chapter> existing =
                chapterRepository.findByWork_IdAndChapterKey(workId, request.id());
        if (existing.isPresent()) {
            requireRetry(existing.get(), request.title(), request.content(), request.mutationId());
            return chapterConverter.toResponse(existing.get());
        }

        long count = chapterRepository.countByWork_Id(workId);
        if (count >= 100) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
        Chapter chapter =
                new Chapter(
                        work,
                        request.id(),
                        (int) count + 1,
                        request.title(),
                        request.content(),
                        1,
                        request.mutationId());
        work.touch();
        chapterRepository.saveAndFlush(chapter);
        return chapterConverter.toResponse(chapter);
    }

    /** 본인 작품만 회차 번호 순으로 페이지 조회한다. 빈 목록도 소유권 확인을 생략하지 않는다. */
    @Transactional(readOnly = true)
    public OffsetPageResponse<ChapterResponse> list(
            Long ownerId, Long workId, OffsetPageRequest page) {
        workAccessService.requireOwned(ownerId, workId);
        return OffsetPageResponse.from(
                chapterRepository
                        .findByWork_Id(workId, page.toPageable(Sort.by("chapterNo")))
                        .map(chapterConverter::toResponse));
    }

    /** 본인 작품에 속한 현재 원고와 저장 버전을 반환한다. */
    @Transactional(readOnly = true)
    public ChapterResponse get(Long ownerId, Long workId, String chapterId) {
        workAccessService.requireOwned(ownerId, workId);
        return chapterConverter.toResponse(requireChapter(workId, chapterId));
    }

    /** 회차 자체 버전만 검사한다. 판단·다른 회차의 저장은 이 버전을 바꾸지 않는다. */
    @Transactional
    public ChapterResponse update(
            Long ownerId, Long workId, String chapterId, UpdateChapterRequest request) {
        Work work = workAccessService.lockOwned(ownerId, workId);
        Chapter chapter = requireChapter(workId, chapterId);
        if (chapter.getMutationId().equals(request.mutationId())) {
            requireRetry(chapter, request.title(), request.content(), request.mutationId());
            return chapterConverter.toResponse(chapter);
        }
        if (chapter.getVersion() != request.version()) {
            throw new CustomException(WorkErrorCode.VERSION_CONFLICT);
        }

        chapter.update(request.title(), request.content(), request.mutationId());
        work.touch();
        chapterRepository.flush();
        return chapterConverter.toResponse(chapter);
    }

    private Chapter requireChapter(Long workId, String chapterId) {
        return chapterRepository
                .findByWork_IdAndChapterKey(workId, chapterId)
                .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND));
    }

    private void requireRetry(Chapter chapter, String title, String content, String mutationId) {
        if (!chapter.getMutationId().equals(mutationId)
                || !chapter.getTitle().equals(title)
                || !chapter.getContent().equals(content)) {
            throw new CustomException(WorkErrorCode.VERSION_CONFLICT);
        }
    }
}
