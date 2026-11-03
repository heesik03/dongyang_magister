package com.magister.backend.domain.timeline.service;

import com.magister.backend.domain.analysis.entity.AnalysisJobStatus;
import com.magister.backend.domain.analysis.repository.AnalysisAdmissionRepository;
import com.magister.backend.domain.analysis.repository.AnalysisJobRepository;
import com.magister.backend.domain.analysis.service.support.AnalysisPreviewToken;
import com.magister.backend.domain.timeline.converter.ExtractionConverter;
import com.magister.backend.domain.timeline.dto.ExtractionData.Candidate;
import com.magister.backend.domain.timeline.dto.ExtractionData.CreateRequest;
import com.magister.backend.domain.timeline.dto.ExtractionData.Input;
import com.magister.backend.domain.timeline.dto.ExtractionData.Job;
import com.magister.backend.domain.timeline.dto.ExtractionData.Preview;
import com.magister.backend.domain.timeline.dto.ExtractionData.PreviewRequest;
import com.magister.backend.domain.timeline.dto.ExtractionData.SaveCandidates;
import com.magister.backend.domain.timeline.entity.EventExtraction;
import com.magister.backend.domain.timeline.repository.EventExtractionRepository;
import com.magister.backend.domain.timeline.repository.ExtractionView;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.work.service.WorkAccessService;
import com.magister.backend.domain.workspace.dto.WorkspaceData.EventData;
import com.magister.backend.domain.workspace.dto.WorkspaceResponse;
import com.magister.backend.domain.workspace.service.WorkspaceService;
import com.magister.backend.global.config.AnalysisExecutionProperties;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.pagination.OffsetPageResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExtractionService {
    private static final List<String> ACTIVE = List.of("QUEUED", "RUNNING");
    private static final List<AnalysisJobStatus> ANALYSIS_ACTIVE =
            List.of(AnalysisJobStatus.QUEUED, AnalysisJobStatus.RUNNING);
    private final EventExtractionRepository eventExtractionRepository;
    private final AnalysisJobRepository analysisJobRepository;
    private final AnalysisAdmissionRepository analysisAdmissionRepository;
    private final WorkAccessService workAccessService;
    private final ExtractionInputService extractionInputService;
    private final ExtractionConverter extractionConverter;
    private final AnalysisPreviewToken previewToken;
    private final ExtractionDispatcher extractionDispatcher;
    private final AnalysisExecutionProperties executionProperties;
    private final ObjectMapper objectMapper;
    private final WorkspaceService workspaceService;

    /** 외부 통신 없이 실제 전송할 원고·모델·10분 유효 토큰을 반환한다. */
    @Transactional(readOnly = true)
    public Preview preview(Long ownerId, Long workId, PreviewRequest request) {
        workAccessService.requireOwned(ownerId, workId);
        Input input = extractionInputService.snapshot(workId, request.chapters());
        return extractionConverter.preview(
                input,
                previewToken.issue(
                        payload(ownerId, workId, input), Instant.now().plusSeconds(600)));
    }

    /** 동일 키 재시도는 기존 작업을 반환한다. 공통 잠금으로 분석·사건 추출 합산 한도를 지킨다. */
    @Transactional
    public Job create(Long ownerId, Long workId, CreateRequest request) {
        analysisAdmissionRepository.lockAdmission().orElseThrow();
        Work work = workAccessService.lockOwned(ownerId, workId);
        EventExtraction existing =
                eventExtractionRepository
                        .findByWork_IdAndRequestId(workId, request.requestId())
                        .orElse(null);
        if (existing != null) {
            if (!objectMapper
                    .readValue(existing.getRequestBody(), CreateRequest.class)
                    .equals(request)) {
                invalid();
            }
            return toJob(existing);
        }
        if (!request.consent()) {
            invalid();
        }
        long own =
                eventExtractionRepository.countByWork_Owner_IdAndStatusIn(ownerId, ACTIVE)
                        + analysisJobRepository.countByWork_Owner_IdAndStatusIn(
                                ownerId, ANALYSIS_ACTIVE);
        long total =
                eventExtractionRepository.countByStatusIn(ACTIVE)
                        + analysisJobRepository.countByStatusIn(ANALYSIS_ACTIVE);
        if (own >= 1 || total >= executionProperties.getMaxActiveJobs()) {
            throw new CustomException(CommonErrorCode.TOO_MANY_REQUESTS);
        }
        Input input = extractionInputService.snapshot(workId, request.chapters());
        previewToken.verify(request.token(), payload(ownerId, workId, input));
        EventExtraction extraction =
                new EventExtraction(
                        UUID.randomUUID().toString(),
                        work,
                        request.requestId(),
                        objectMapper.writeValueAsString(request),
                        objectMapper.writeValueAsString(input));
        eventExtractionRepository.saveAndFlush(extraction);
        afterCommit(() -> extractionDispatcher.submit(extraction.getId()));
        return toJob(extraction);
    }

    /** 소유권을 확인하고 후보만 조회한다. 원고는 별도 근거 API에서 요청한다. */
    @Transactional(readOnly = true)
    public Job get(Long ownerId, Long workId, String id) {
        workAccessService.requireOwned(ownerId, workId);
        return toJob(
                eventExtractionRepository
                        .view(id, workId)
                        .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND)));
    }

    /** 이력의 페이지 크기는 5이며 원고 본문은 응답에서 제외한다. */
    @Transactional(readOnly = true)
    public OffsetPageResponse<Job> list(Long ownerId, Long workId, int page) {
        workAccessService.requireOwned(ownerId, workId);
        if (page < 0) {
            invalid();
        }
        return OffsetPageResponse.from(
                eventExtractionRepository
                        .history(workId, PageRequest.of(page, 5))
                        .map(this::toJob));
    }

    /** 추출 당시 원고만 반환하고 현재 원고로 대체하지 않는다. */
    @Transactional(readOnly = true)
    public Input source(Long ownerId, Long workId, String id) {
        return objectMapper.readValue(owned(ownerId, workId, id, false).getInput(), Input.class);
    }

    /** 후보 변경은 버전을 확인하며 근거·식별자는 바꾸지 못한다. */
    @Transactional
    public Job save(Long ownerId, Long workId, String id, SaveCandidates request) {
        workAccessService.lockOwned(ownerId, workId);
        EventExtraction extraction = owned(ownerId, workId, id, true);
        if (!extraction.getStatus().equals("SUCCEEDED")) {
            conflict();
        }
        if (extraction.getVersion() != request.version()) {
            // 저장 응답만 유실된 재시도는 같은 내용일 때에만 현재 결과를 돌려준다.
            if (extraction
                    .getResult()
                    .equals(objectMapper.writeValueAsString(request.candidates()))) {
                return toJob(extraction);
            }
            conflict();
        }
        List<Candidate> original = readCandidates(extraction.getResult());
        if (original.size() != request.candidates().size()) {
            invalid();
        }
        Set<String> ids = new HashSet<>();
        for (Candidate next : request.candidates()) {
            Candidate previous =
                    original.stream()
                            .filter(item -> item.id().equals(next.id()))
                            .findFirst()
                            .orElseThrow(
                                    () -> new CustomException(CommonErrorCode.INVALID_REQUEST));
            if (!ids.add(next.id())
                    || !previous.chapterId().equals(next.chapterId())
                    || previous.sourceRevision() != next.sourceRevision()
                    || previous.startOffset() != next.startOffset()
                    || previous.endOffset() != next.endOffset()
                    || !previous.quote().equals(next.quote())) {
                invalid();
            }
        }
        extraction.edit(objectMapper.writeValueAsString(request.candidates()));
        eventExtractionRepository.flush();
        return toJob(extraction);
    }

    /** 선택 후보만 기존 사건 뒤에 추가한다. 기존 ID는 재생성·덮어쓰기하지 않으며 Workspace 버전을 검사한다. */
    @Transactional
    public WorkspaceResponse confirm(
            Long ownerId,
            Long workId,
            String id,
            long version,
            long workspaceVersion,
            String mutationId) {
        workAccessService.lockOwned(ownerId, workId);
        EventExtraction extraction = owned(ownerId, workId, id, true);
        if (!extraction.getStatus().equals("SUCCEEDED") || extraction.getVersion() != version) {
            conflict();
        }
        WorkspaceResponse current = workspaceService.get(ownerId, workId);
        List<EventData> events = new ArrayList<>(current.content().events());
        List<Candidate> included =
                readCandidates(extraction.getResult()).stream()
                        .filter(candidate -> candidate.decision().equals("INCLUDE"))
                        .toList();
        if (included.isEmpty()) {
            invalid();
        }
        for (Candidate candidate : included) {
            if (events.stream().anyMatch(event -> event.id().equals(candidate.id()))) {
                continue;
            }
            if (events.size() >= 1000) {
                invalid();
            }
            events.add(extractionConverter.event(candidate, id));
        }
        if (events.equals(current.content().events())) {
            return current;
        }
        if (current.version() != workspaceVersion) {
            conflict();
        }
        return workspaceService.save(
                ownerId,
                workId,
                extractionConverter.confirmation(workspaceVersion, mutationId, events));
    }

    /** DB 상태를 먼저 종료시켜 취소 후 늦게 온 결과가 성공으로 덮어쓰지 못하게 한다. */
    @Transactional
    public Job cancel(Long ownerId, Long workId, String id) {
        workAccessService.lockOwned(ownerId, workId);
        EventExtraction extraction = owned(ownerId, workId, id, true);
        extraction.cancel();
        eventExtractionRepository.flush();
        afterCommit(() -> extractionDispatcher.cancel(id));
        return toJob(extraction);
    }

    private Job toJob(ExtractionView extraction) {
        return extractionConverter.response(extraction, readCandidates(extraction.getResult()));
    }

    private Job toJob(EventExtraction extraction) {
        return extractionConverter.response(extraction, readCandidates(extraction.getResult()));
    }

    private List<Candidate> readCandidates(String resultJson) {
        return Arrays.asList(objectMapper.readValue(resultJson, Candidate[].class));
    }

    private EventExtraction owned(Long ownerId, Long workId, String id, boolean lock) {
        workAccessService.requireOwned(ownerId, workId);
        EventExtraction extraction =
                (lock
                                ? eventExtractionRepository.lockById(id)
                                : eventExtractionRepository.findById(id))
                        .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND));
        if (!extraction.getWork().getId().equals(workId)) {
            throw new CustomException(WorkErrorCode.WORK_NOT_FOUND);
        }
        return extraction;
    }

    private String payload(Long ownerId, Long workId, Input input) {
        return "event-extraction:v1|"
                + ownerId
                + "|"
                + workId
                + "|"
                + objectMapper.writeValueAsString(input);
    }

    private void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                });
    }

    private void invalid() {
        throw new CustomException(CommonErrorCode.INVALID_REQUEST);
    }

    private void conflict() {
        throw new CustomException(WorkErrorCode.VERSION_CONFLICT);
    }
}
