package com.magister.backend.domain.workspace.service;

import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.work.repository.WorkRepository;
import com.magister.backend.domain.workspace.dto.*;
import com.magister.backend.domain.workspace.entity.Workspace;
import com.magister.backend.domain.workspace.repository.WorkspaceRepository;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WorkspaceService {
    private final WorkspaceRepository workspaces;
    private final WorkRepository works;
    private final ObjectMapper json;

    @Transactional(readOnly = true)
    public WorkspaceResponse get(Long ownerId, Long workId) {
        Work work =
                works.findByIdAndOwner_Id(workId, ownerId)
                        .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND));
        return workspaces
                .findById(workId)
                .map(this::response)
                .orElseGet(
                        () ->
                                new WorkspaceResponse(
                                        0,
                                        WorkspaceData.empty(),
                                        Instant.EPOCH,
                                        work.getVersion()));
    }

    @Transactional
    public WorkspaceResponse save(Long ownerId, Long workId, SaveWorkspaceRequest request) {
        // 최초 문서 생성의 경합도 부모 작품 잠금으로 직렬화한다. 외부 통신은 트랜잭션에 없다.
        Work work =
                works.lockOwned(workId, ownerId)
                        .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND));
        String content = json.writeValueAsString(request.content());
        if (content.length() > 2000000) invalid();
        validateReferences(request.content());
        Workspace workspace = workspaces.findById(workId).orElseGet(() -> new Workspace(work));
        // 응답 유실 후 동일 저장 재시도는 새 버전을 만들지 않는다.
        if (request.mutationId().equals(workspace.getMutationId())) {
            if (!content.equals(workspace.getContent())) invalid();
            return response(workspace);
        }
        if (workspace.getRevision() != request.version())
            throw new CustomException(WorkErrorCode.VERSION_CONFLICT);
        workspace.replace(content, request.mutationId());
        work.touch();
        return response(workspaces.saveAndFlush(workspace));
    }

    private WorkspaceResponse response(Workspace w) {
        return new WorkspaceResponse(
                w.getRevision(),
                json.readValue(w.getContent(), WorkspaceData.class),
                w.getUpdatedAt(),
                w.getWork().getVersion());
    }

    private void validateReferences(WorkspaceData data) {
        Set<String> chapters = new HashSet<>();
        for (var c : data.chapters()) if (!chapters.add(c.id())) invalid();
        Set<String> analyses = new HashSet<>();
        for (var a : data.analyses()) {
            if (!chapters.contains(a.chapterId())
                    || !analyses.add(a.chapterId())
                    || !chapters.containsAll(a.previousIds())) invalid();
            try {
                Instant.parse(a.createdAt());
            } catch (RuntimeException e) {
                invalid();
            }
            Set<String> reviews = new HashSet<>();
            for (var r : a.reviews()) {
                if (!reviews.add(r.id())
                        || !a.source().contains(r.quote())
                        || !a.settingIds().contains(r.settingId())) invalid();
                if (r.decision().equals("의도된 설정") && r.note().isBlank()) invalid();
            }
        }
        Set<String> events = new HashSet<>();
        for (var e : data.events())
            if (!events.add(e.id())
                    || (!e.chapterId().isEmpty() && !chapters.contains(e.chapterId()))) invalid();
    }

    private void invalid() {
        throw new CustomException(CommonErrorCode.INVALID_REQUEST);
    }
}
