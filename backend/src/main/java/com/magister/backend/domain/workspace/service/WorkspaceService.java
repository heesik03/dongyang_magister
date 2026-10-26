package com.magister.backend.domain.workspace.service;

import com.magister.backend.domain.work.service.WorkAccessService;
import com.magister.backend.domain.workspace.dto.SaveWorkspaceRequest;
import com.magister.backend.domain.workspace.dto.WorkspaceResponse;
import com.magister.backend.domain.workspace.service.support.WorkspaceCodec;
import com.magister.backend.domain.workspace.service.validation.WorkspaceReferenceValidator;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkspaceService {
    private final WorkAccessService workAccessService;
    private final WorkspaceSnapshotService snapshotService;
    private final WorkspacePersistenceService persistenceService;
    private final WorkspaceReferenceValidator referenceValidator;
    private final WorkspaceCodec workspaceCodec;

    /** 소유권을 먼저 확인하고 미생성 문서는 빈 초기 상태로 반환한다. */
    @Transactional(readOnly = true)
    public WorkspaceResponse get(Long ownerId, Long workId) {
        return snapshotService.getForOwnedWork(workAccessService.requireOwned(ownerId, workId));
    }

    /** 큰 JSON과 순수 검증은 트랜잭션 밖에서 처리해 DB 잠금 시간을 줄인다. */
    public WorkspaceResponse save(Long ownerId, Long workId, SaveWorkspaceRequest request) {
        referenceValidator.validate(request.content());
        String encodedContent = workspaceCodec.encode(request.content());
        return persistenceService.save(ownerId, workId, request, encodedContent);
    }
}
