package com.magister.backend.domain.workspace.service;

import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.work.service.WorkAccessService;
import com.magister.backend.domain.workspace.converter.WorkspaceConverter;
import com.magister.backend.domain.workspace.dto.SaveWorkspaceRequest;
import com.magister.backend.domain.workspace.dto.WorkspaceResponse;
import com.magister.backend.domain.workspace.entity.Workspace;
import com.magister.backend.domain.workspace.repository.WorkspaceRepository;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkspacePersistenceService {
    private final WorkAccessService workAccessService;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceConverter converter;

    /** 직렬화·순수 검증이 끝난 요청의 잠금·버전·멱등성·저장만 처리한다. */
    @Transactional
    public WorkspaceResponse save(
            Long ownerId, Long workId, SaveWorkspaceRequest request, String content) {
        Work work = workAccessService.lockOwned(ownerId, workId);
        Workspace workspace =
                workspaceRepository.findById(workId).orElseGet(() -> new Workspace(work));
        if (request.mutationId().equals(workspace.getMutationId())) {
            if (!content.equals(workspace.getContent())) {
                throw new CustomException(CommonErrorCode.INVALID_REQUEST);
            }
            return converter.toResponse(workspace, request.content());
        }
        if (workspace.getRevision() != request.version()) {
            throw new CustomException(WorkErrorCode.VERSION_CONFLICT);
        }
        workspace.replace(content, request.mutationId());
        // 프런트엔드의 workVersion 계약을 보존한다. 모든 하위 쓰기는 부모 잠금 순서를 따른다.
        work.touch();
        return converter.toResponse(workspaceRepository.saveAndFlush(workspace), request.content());
    }
}
