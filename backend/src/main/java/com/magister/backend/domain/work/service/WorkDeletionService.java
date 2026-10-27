package com.magister.backend.domain.work.service;

import com.magister.backend.domain.analysis.service.AnalysisDispatcher;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.work.repository.WorkDeletionRepository;
import com.magister.backend.global.error.exception.CustomException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkDeletionService {
    private final WorkAccessService workAccessService;
    private final WorkDeletionRepository workDeletionRepository;
    private final AnalysisDispatcher analysisDispatcher;

    /** 본인 작품·최신 버전을 확인해 모두 삭제하고 커밋 후에만 로컬 실행을 중단한다. 공급자 자료 삭제는 보장하지 않는다. */
    @Transactional
    public void delete(Long ownerId, Long workId, long expectedVersion) {
        Work work = workAccessService.lockOwned(ownerId, workId);
        if (work.getVersion() != expectedVersion) {
            throw new CustomException(WorkErrorCode.VERSION_CONFLICT);
        }
        List<String> activeIds =
                workDeletionRepository.lockJobs(workId).stream()
                        .filter(
                                job ->
                                        job.status().equals("QUEUED")
                                                || job.status().equals("RUNNING"))
                        .map(WorkDeletionRepository.LockedJob::id)
                        .toList();
        workDeletionRepository.delete(workId);
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        activeIds.forEach(analysisDispatcher::cancel);
                    }
                });
    }
}
