package com.magister.backend.domain.analysis.service;

import com.magister.backend.domain.analysis.converter.AnalysisConverter;
import com.magister.backend.domain.analysis.dto.AnalysisInput;
import com.magister.backend.domain.analysis.dto.AnalysisJobResponse;
import com.magister.backend.domain.analysis.dto.AnalysisJobSummary;
import com.magister.backend.domain.analysis.dto.AnalysisRequest;
import com.magister.backend.domain.analysis.entity.AnalysisJob;
import com.magister.backend.domain.analysis.entity.AnalysisJobStatus;
import com.magister.backend.domain.analysis.repository.AnalysisAdmissionRepository;
import com.magister.backend.domain.analysis.repository.AnalysisJobRepository;
import com.magister.backend.domain.analysis.service.support.AnalysisInputFactory;
import com.magister.backend.domain.chapter.repository.ChapterRepository;
import com.magister.backend.domain.decision.service.DecisionReferenceService;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.work.service.WorkAccessService;
import com.magister.backend.domain.workspace.dto.WorkspaceResponse;
import com.magister.backend.domain.workspace.service.WorkspaceSnapshotService;
import com.magister.backend.global.config.AnalysisExecutionProperties;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.gemini.GeminiProperties;
import com.magister.backend.global.pagination.OffsetPageRequest;
import com.magister.backend.global.pagination.OffsetPageResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 분석 접수·조회·취소의 업무 경계다. 스레드 실행과 개별 상태 저장은 별도 빈에 위임한다. */
@Service
@RequiredArgsConstructor
public class AnalysisJobService {
    private static final List<AnalysisJobStatus> ACTIVE_STATUSES =
            List.of(AnalysisJobStatus.QUEUED, AnalysisJobStatus.RUNNING);
    private final AnalysisAdmissionRepository admissionRepository;
    private final AnalysisJobRepository analysisJobRepository;
    private final WorkAccessService workAccessService;
    private final WorkspaceSnapshotService snapshotService;
    private final AnalysisInputFactory inputFactory;
    private final AnalysisDispatcher dispatcher;
    private final AnalysisExecutionProperties executionProperties;
    private final AnalysisConverter converter;
    private final ObjectMapper objectMapper;
    private final AnalysisPreviewService previewService;
    private final AnalysisSnapshotService analysisSnapshotService;
    private final ChapterRepository chapterRepository;
    private final GeminiProperties geminiProperties;
    private final DecisionReferenceService decisionReferenceService;

    /**
     * 선택한 원고·확정 설정의 현재 스냅샷을 저장하고 커밋 후 분석을 접수한다. 동일 작품의 requestId와 같은 요청은 종료 여부와 관계없이 기존 작업을 반환한다.
     * 같은 키의 다른 요청, 오래된 Workspace 버전, 작업 수 초과는 새 작업을 만들지 않고 거절한다.
     *
     * @param ownerId 요청 본문이 아닌 인증된 세션에서 얻은 사용자 ID
     * @return 접수 시점의 작업 상태. 실제 실행·접수 실패는 이후 상태 조회에 반영된다.
     */
    @Transactional
    public AnalysisJobResponse create(Long ownerId, Long workId, AnalysisRequest request) {
        // 작품만 잠그면 한 사용자의 서로 다른 작품 요청이 동시에 한도 검사를 통과할 수 있다.
        // 모든 신규 접수는 공통 행 -> 작품 순서로 잠가 count와 insert를 같은 임계 구역에 둔다.
        admissionRepository.lockAdmission().orElseThrow();

        Work work = workAccessService.lockOwned(ownerId, workId);

        // 응답 유실로 재전송된 요청은 작업 수 제한보다 먼저 찾아 중복 과금·실행을 막는다.
        String requestBody = objectMapper.writeValueAsString(request);
        Optional<AnalysisJob> existingJob =
                analysisJobRepository.findByWork_IdAndRequestId(workId, request.requestId());

        if (existingJob.isPresent()) {
            if (!objectMapper
                    .readValue(existingJob.get().getRequestBody(), AnalysisRequest.class)
                    .equals(request)) {
                invalid();
            }
            return converter.toResponse(existingJob.get());
        }

        // 이 검사는 공통 접수 잠금을 보유한 상태에서만 원자적인 예약 의미를 가진다.
        if (analysisJobRepository.countByWork_Owner_IdAndStatusIn(ownerId, ACTIVE_STATUSES) >= 1
                || analysisJobRepository.countByStatusIn(ACTIVE_STATUSES)
                        >= executionProperties.getMaxActiveJobs()) {
            throw new CustomException(CommonErrorCode.TOO_MANY_REQUESTS);
        }

        // 이미 소유권과 잠금을 확인한 Work를 재사용한다. 저장 후 원고가 바뀌어도 이번 입력은 고정된다.
        WorkspaceResponse workspace = snapshotService.getForOwnedWork(work);
        AnalysisInput input = inputFactory.create(workId, request, workspace);
        String snapshot = previewService.verify(ownerId, workId, request, input);

        // 실행 프로세스가 사라지면 heartbeat가 끊겨 이 lease를 기준으로 미완료 작업을 복구한다.
        AnalysisJob job =
                new AnalysisJob(
                        UUID.randomUUID().toString(),
                        work,
                        request.requestId(),
                        requestBody,
                        snapshot);
        job.assignLease(
                executionProperties.getInstanceId(),
                Instant.now().plus(executionProperties.getLeaseDuration()));
        job.recordConfirmation(
                chapterRepository
                        .findByWork_IdAndChapterKey(workId, input.chapter().id())
                        .orElseThrow(),
                AnalysisPreviewService.POLICY_VERSION,
                geminiProperties.model());
        analysisJobRepository.saveAndFlush(job);
        analysisSnapshotService.save(job, input);
        decisionReferenceService.save(job, input.decisionReferences());

        String jobId = job.getId();

        // 저장이 롤백된 작업은 실행하지 않는다. 접수 거절은 Dispatcher가 별도 커밋으로 기록한다.
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        dispatcher.submit(jobId);
                    }
                });

        return converter.toResponse(job);
    }

    /**
     * 작업·작품·소유자를 하나의 조회 조건으로 확인하고 상태 응답에 필요한 필드만 읽는다. 반복 폴링이 완료·취소를 막지 않도록 쓰기 잠금을 사용하지 않으며 입력 원고도
     * 읽지 않는다. 다른 작품의 작업 ID와 존재하지 않는 작업은 같은 WORK_NOT_FOUND로 응답한다.
     */
    @Transactional(readOnly = true)
    public AnalysisJobResponse get(Long ownerId, Long workId, String jobId) {
        return converter.toResponse(
                analysisJobRepository
                        .findOwnedView(jobId, workId, ownerId)
                        .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND)));
    }

    /** 빈 이력도 소유권을 검사하고 원고를 읽지 않는 목록으로 반환한다. */
    @Transactional(readOnly = true)
    public OffsetPageResponse<AnalysisJobSummary> list(
            Long ownerId,
            Long workId,
            String chapterId,
            AnalysisJobStatus status,
            OffsetPageRequest page) {
        workAccessService.requireOwned(ownerId, workId);
        return OffsetPageResponse.from(
                analysisJobRepository
                        .findHistory(
                                workId,
                                ownerId,
                                chapterId,
                                status,
                                page.toPageable(
                                        Sort.by(
                                                Sort.Order.desc("createdAt"),
                                                Sort.Order.desc("id"))))
                        .map(converter::toSummary));
    }

    /**
     * 소유한 작품의 미완료 작업을 취소한다. 완료·실패 등 이미 종료된 상태는 보존한다. 작업 행 잠금으로 완료 저장과 순서를 정한 뒤, 취소 커밋 후 실행기에 인터럽트를
     * 요청한다. 다른 서버의 실행기는 주기 상태 확인으로 취소를 반영하며 공급자의 과금 중단까지 보장하지 않는다.
     */
    @Transactional
    public AnalysisJobResponse cancel(Long ownerId, Long workId, String jobId) {
        workAccessService.requireOwned(ownerId, workId);

        // 사용자 소유 작품을 확인해도 해당 작업이 바로 그 작품에 속하는지는 별도로 검사해야 한다.
        AnalysisJob job =
                analysisJobRepository
                        .lockById(jobId)
                        .filter(item -> item.getWork().getId().equals(workId))
                        .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND));

        job.cancel();

        if (job.getStatus() == AnalysisJobStatus.CANCELLED) {
            // DB 취소가 커밋되기 전에 워커를 인터럽트하면 실패가 먼저 기록될 수 있다.
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
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
