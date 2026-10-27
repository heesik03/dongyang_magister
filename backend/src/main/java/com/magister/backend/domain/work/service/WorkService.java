package com.magister.backend.domain.work.service;

import com.magister.backend.domain.user.repository.UserRepository;
import com.magister.backend.domain.work.converter.WorkConverter;
import com.magister.backend.domain.work.dto.CreateWorkRequest;
import com.magister.backend.domain.work.dto.UpdateWorkRequest;
import com.magister.backend.domain.work.dto.WorkResponse;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.work.repository.WorkRepository;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.pagination.OffsetPageRequest;
import com.magister.backend.global.pagination.OffsetPageResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkService {
    private final WorkRepository workRepository;
    private final UserRepository userRepository;
    private final WorkAccessService workAccessService;

    /** 소유자는 인증 정보에서 얻고 생성된 식별자·버전을 컨버터로 반환한다. */
    @Transactional
    public WorkResponse create(Long ownerId, CreateWorkRequest request) {
        // 소유자는 요청 본문이 아닌 인증된 세션에서 전달받는다.
        Work work =
                new Work(
                        userRepository.getReferenceById(ownerId),
                        request.title(),
                        request.genre(),
                        request.era(),
                        request.description());
        return WorkConverter.toResponse(workRepository.saveAndFlush(work));
    }

    /** 전체 개수를 제공하는 오프셋 목록이다. 같은 수정 시각은 ID로 순서를 고정한다. */
    @Transactional(readOnly = true)
    public OffsetPageResponse<WorkResponse> list(
            Long ownerId, String keyword, String genre, int page, int size) {
        Pageable paging =
                OffsetPageRequest.of(page, size)
                        .toPageable(Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
        return OffsetPageResponse.from(
                workRepository
                        .search(ownerId, keyword.strip(), genre.strip(), paging)
                        .map(WorkConverter::toResponse));
    }

    /** 소유하지 않은 작품은 존재 여부를 구분하지 않고 WORK_NOT_FOUND로 응답한다. */
    @Transactional(readOnly = true)
    public WorkResponse get(Long ownerId, Long workId) {
        return WorkConverter.toResponse(workAccessService.requireOwned(ownerId, workId));
    }

    /** 부모 행 잠금과 클라이언트 버전 검사를 함께 적용해 오래된 편집의 덮어쓰기를 차단한다. */
    @Transactional
    public WorkResponse update(Long ownerId, Long workId, UpdateWorkRequest request) {
        Work work = workAccessService.lockOwned(ownerId, workId);
        if (!work.getVersion().equals(request.version())) {
            throw new CustomException(WorkErrorCode.VERSION_CONFLICT);
        }

        work.update(request.title(), request.genre(), request.era(), request.description());
        // @Version의 실제 DB 충돌 검사를 마친 후 최신 버전을 응답한다.
        workRepository.flush();

        return WorkConverter.toResponse(work);
    }
}
