package com.magister.backend.domain.work.service;

import com.magister.backend.domain.work.converter.WorkConverter;
import com.magister.backend.domain.user.repository.UserRepository;
import com.magister.backend.domain.work.dto.*;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.work.repository.WorkRepository;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.response.PageResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkService {
    private final WorkRepository works;
    private final UserRepository users;
    private final WorkAccessService workAccessService;

    @Transactional
    public WorkResponse create(Long ownerId, CreateWorkRequest request) {
        // 소유자는 요청 본문이 아닌 인증된 세션에서 전달받는다.
        Work work =
                new Work(
                        users.getReferenceById(ownerId),
                        request.title(),
                        request.genre(),
                        request.era(),
                        request.description());
        return WorkConverter.toResponse(works.saveAndFlush(work));
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkResponse> list(
            Long ownerId, String keyword, String genre, int page, int size) {
        PageRequest paging =
                PageRequest.of(
                        page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
        return PageResponse.from(
                works.search(ownerId, keyword.strip(), genre.strip(), paging)
                        .map(WorkConverter::toResponse));
    }

    @Transactional(readOnly = true)
    public WorkResponse get(Long ownerId, Long workId) {
        return WorkConverter.toResponse(workAccessService.requireOwned(ownerId, workId));
    }

    @Transactional
    public WorkResponse update(Long ownerId, Long workId, UpdateWorkRequest request) {
        Work work = workAccessService.lockOwned(ownerId, workId);
        if (!work.getVersion().equals(request.version()))
            throw new CustomException(WorkErrorCode.VERSION_CONFLICT);
        work.update(request.title(), request.genre(), request.era(), request.description());
        // @Version의 실제 DB 충돌 검사를 마친 후 최신 버전을 응답한다.
        works.flush();
        return WorkConverter.toResponse(work);
    }

}
