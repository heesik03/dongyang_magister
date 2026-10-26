package com.magister.backend.domain.work.service;

import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.work.repository.WorkRepository;
import com.magister.backend.global.error.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkAccessService {
    private final WorkRepository workRepository;

    /** 빈 하위 목록을 반환하는 경우에도 작품 소유권을 먼저 확인한다. */
    @Transactional(readOnly = true)
    public Work requireOwned(Long ownerId, Long workId) {
        return workRepository.findByIdAndOwner_Id(workId, ownerId)
                .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND));
    }

    /** 부모 버전 변경과 최초 하위 문서 생성을 상위 쓰기 트랜잭션에서 직렬화한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Work lockOwned(Long ownerId, Long workId) {
        return workRepository.lockOwned(workId, ownerId)
                .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND));
    }
}
