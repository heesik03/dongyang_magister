package com.magister.backend.domain.analysis.service;

import com.magister.backend.domain.analysis.dto.*;
import com.magister.backend.domain.analysis.entity.AnalysisJob;
import com.magister.backend.domain.analysis.repository.AnalysisJobRepository;
import com.magister.backend.domain.setting.entity.*;
import com.magister.backend.domain.setting.repository.WorkSettingRepository;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.work.repository.WorkRepository;
import com.magister.backend.domain.workspace.dto.WorkspaceData.*;
import com.magister.backend.domain.workspace.service.WorkspaceService;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;

import jakarta.annotation.PreDestroy;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;

import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class AnalysisJobService {
    private final AnalysisJobRepository jobs;
    private final WorkRepository works;
    private final WorkSettingRepository settings;
    private final WorkspaceService workspace;
    private final AnalysisWorker worker;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    private final ExecutorService executor =
            new ThreadPoolExecutor(
                    2,
                    2,
                    0,
                    TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(8),
                    new ThreadPoolExecutor.AbortPolicy());

    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void failInterrupted() {
        transaction.executeWithoutResult(status -> jobs.failInterrupted());
    }

    @Transactional
    public AnalysisJobResponse create(Long owner, Long workId, AnalysisRequest request) {
        var work =
                works.lockOwned(workId, owner)
                        .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND));
        String requestBody = json.writeValueAsString(request);
        var existing = jobs.findByWork_IdAndRequestId(workId, request.requestId());
        if (existing.isPresent()) {
            if (!existing.get().getRequestBody().equals(requestBody)) invalid();
            return response(existing.get());
        }
        if (jobs.countByWork_Owner_IdAndStatusIn(owner, List.of("QUEUED", "RUNNING")) >= 1
                || jobs.countByStatusIn(List.of("QUEUED", "RUNNING")) >= 10)
            throw new CustomException(CommonErrorCode.TOO_MANY_REQUESTS);
        var stored = workspace.get(owner, workId);
        if (stored.version() != request.workspaceVersion())
            throw new CustomException(WorkErrorCode.VERSION_CONFLICT);
        if (!request.consistency() && !request.motivation()) invalid();
        var content = stored.content();
        var chapter =
                content.chapters().stream()
                        .filter(item -> item.id().equals(request.chapterId()))
                        .findFirst()
                        .orElseThrow(() -> new CustomException(CommonErrorCode.INVALID_REQUEST));
        if (chapter.content().isBlank()) invalid();
        List<AnalysisInput.SettingReference> references = new ArrayList<>();
        List<Long> ids;
        try {
            ids = request.settingIds().stream().map(Long::valueOf).distinct().toList();
        } catch (NumberFormatException e) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
        var found = settings.findAllByWork_IdAndIdIn(workId, ids);
        if (found.size() != ids.size()) invalid();
        for (var setting : found) {
            if (setting.getStatus() != SettingStatus.CONFIRMED) invalid();
            String kind =
                    switch (setting.getKind()) {
                        case CHARACTER -> "인물";
                        case WORLD -> "세계관";
                        case MAJOR_EVENT -> "주요 사건";
                        case INTENDED_ADAPTATION -> "의도한 각색";
                    };
            references.add(
                    new AnalysisInput.SettingReference(
                            setting.getId().toString(),
                            setting.getTitle(),
                            setting.getContent(),
                            kind,
                            "확정",
                            setting.getVersion()));
        }
        var previous =
                content.chapters().stream()
                        .filter(
                                item ->
                                        request.previousIds().contains(item.id())
                                                && !item.id().equals(chapter.id()))
                        .toList();
        if (previous.size() != new HashSet<>(request.previousIds()).size()) invalid();
        var decisions =
                content.analyses().stream()
                        .filter(item -> item.chapterId().equals(chapter.id()))
                        .flatMap(item -> item.reviews().stream())
                        .toList();
        var input =
                new AnalysisInput(
                        chapter,
                        references,
                        previous,
                        decisions,
                        request.consistency(),
                        request.motivation());
        String snapshot = json.writeValueAsString(input);
        if (snapshot.length() > 200000) invalid();
        var job =
                jobs.saveAndFlush(
                        new AnalysisJob(
                                UUID.randomUUID().toString(),
                                work,
                                request.requestId(),
                                requestBody,
                                snapshot));
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            executor.submit(() -> worker.execute(job.getId()));
                        } catch (RejectedExecutionException error) {
                            transaction.executeWithoutResult(
                                    status ->
                                            jobs.findById(job.getId())
                                                    .ifPresent(
                                                            item ->
                                                                    item.transition(
                                                                            "FAILED",
                                                                            null,
                                                                            "AI 대기열이 가득 찼습니다. 다시"
                                                                                + " 시도해 주세요.")));
                        }
                    }
                });
        return response(job);
    }

    @Transactional
    public AnalysisJobResponse get(Long owner, Long workId, String id) {
        return response(owned(owner, workId, id));
    }

    @Transactional
    public AnalysisJobResponse cancel(Long owner, Long workId, String id) {
        var job = owned(owner, workId, id);
        if (!job.terminal()) job.transition("CANCELLED", null, null);
        return response(job);
    }

    private AnalysisJob owned(Long owner, Long workId, String id) {
        works.findByIdAndOwner_Id(workId, owner)
                .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND));
        return jobs.lockById(id)
                .filter(job -> job.getWork().getId().equals(workId))
                .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND));
    }

    private AnalysisJobResponse response(AnalysisJob job) {
        return new AnalysisJobResponse(
                job.getId(),
                job.getStatus(),
                job.getError(),
                job.getResult() == null
                        ? null
                        : json.readValue(job.getResult(), AnalysisData.class));
    }

    private void invalid() {
        throw new CustomException(CommonErrorCode.INVALID_REQUEST);
    }
}
