package com.magister.backend.domain.analysis.service;

import com.magister.backend.domain.analysis.converter.AnalysisJobConverter;
import com.magister.backend.domain.analysis.dto.AnalysisInput;
import com.magister.backend.domain.analysis.dto.AnalysisJobResponse;
import com.magister.backend.domain.analysis.dto.AnalysisRequest;
import com.magister.backend.domain.analysis.entity.AnalysisJob;
import com.magister.backend.domain.analysis.entity.AnalysisJobStatus;
import com.magister.backend.domain.analysis.repository.AnalysisAdmissionRepository;
import com.magister.backend.domain.analysis.repository.AnalysisJobRepository;
import com.magister.backend.domain.analysis.service.execution.AnalysisDispatcher;
import com.magister.backend.domain.analysis.service.execution.AnalysisExecutionProperties;
import com.magister.backend.domain.analysis.service.execution.AnalysisInstance;
import com.magister.backend.domain.analysis.service.input.AnalysisInputFactory;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.work.service.WorkAccessService;
import com.magister.backend.domain.workspace.dto.WorkspaceResponse;
import com.magister.backend.domain.workspace.service.WorkspaceSnapshotService;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AnalysisJobService {
    private static final int MAX_SNAPSHOT_CHARS = 200_000;
    private static final List<AnalysisJobStatus> ACTIVE_STATUSES =
            List.of(AnalysisJobStatus.QUEUED, AnalysisJobStatus.RUNNING);
    private final AnalysisAdmissionRepository admissionRepository;
    private final AnalysisJobRepository analysisJobRepository;
    private final WorkAccessService workAccessService;
    private final WorkspaceSnapshotService snapshotService;
    private final AnalysisInputFactory inputFactory;
    private final AnalysisDispatcher dispatcher;
    private final AnalysisExecutionProperties executionProperties;
    private final AnalysisInstance instance;
    private final AnalysisJobConverter converter;
    private final ObjectMapper objectMapper;

    /**
     * 같은 작품의 requestId와 요청 본문은 기존 작업을 반환한다.
     * 공통 접수 행 -> 작품 순서로 잠가 서로 다른 작품의 동시 접수도 한도를 지킨다.
     */
    @Transactional
    public AnalysisJobResponse create(Long ownerId, Long workId, AnalysisRequest request) {
        admissionRepository.lockAdmission().orElseThrow();
        Work work = workAccessService.lockOwned(ownerId, workId);
        String requestBody = objectMapper.writeValueAsString(request);
        Optional<AnalysisJob> existingJob = analysisJobRepository
                .findByWork_IdAndRequestId(workId, request.requestId());
        if (existingJob.isPresent()) {
            if (!existingJob.get().getRequestBody().equals(requestBody)) invalid();
            return converter.toResponse(existingJob.get());
        }
        if (analysisJobRepository.countByWork_Owner_IdAndStatusIn(ownerId, ACTIVE_STATUSES) >= 1
                || analysisJobRepository.countByStatusIn(ACTIVE_STATUSES) >= executionProperties.getMaxActiveJobs()) {
            throw new CustomException(CommonErrorCode.TOO_MANY_REQUESTS);
        }
        WorkspaceResponse workspace = snapshotService.getForOwnedWork(work);
        AnalysisInput input = inputFactory.create(workId, request, workspace);
        String snapshot = objectMapper.writeValueAsString(input);
        if (snapshot.length() > MAX_SNAPSHOT_CHARS) invalid();
        AnalysisJob job = new AnalysisJob(UUID.randomUUID().toString(), work, request.requestId(), requestBody, snapshot);
        job.assignLease(instance.id(), Instant.now().plus(executionProperties.getLeaseDuration()));
        analysisJobRepository.saveAndFlush(job);
        String jobId = job.getId();
        // 저장이 롤백된 작업은 실행하지 않는다. 접수 거절은 Dispatcher가 별도 커밋으로 기록한다.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatcher.submit(jobId);
            }
        });
        return converter.toResponse(job);
    }

    /** 입력 원고를 읽거나 쓰기 잠금을 획득하지 않는 소유자 전용 상태 조회다. */
    @Transactional(readOnly = true)
    public AnalysisJobResponse get(Long ownerId, Long workId, String jobId) {
        return converter.toResponse(analysisJobRepository.findOwnedView(jobId, workId, ownerId)
                .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND)));
    }

    /** 이미 종료된 상태는 유지하고 취소와 완료의 경합은 작업 행 잠금으로 직렬화한다. */
    @Transactional
    public AnalysisJobResponse cancel(Long ownerId, Long workId, String jobId) {
        workAccessService.requireOwned(ownerId, workId);
        AnalysisJob job = analysisJobRepository.lockById(jobId)
                .filter(item -> item.getWork().getId().equals(workId))
                .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND));
        job.cancel();
        if (job.getStatus() == AnalysisJobStatus.CANCELLED) {
            // DB 취소가 커밋되기 전에 워커를 인터럽트하면 실패가 먼저 기록될 수 있다.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatcher.cancel(jobId);
                }
            });
        }
        return converter.toResponse(job);
    }

    private void invalid() {
        throw new CustomException(CommonErrorCode.INVALID_REQUEST);
    }
}
