package com.magister.backend.domain.workspace.service;

import com.magister.backend.domain.work.service.WorkAccessService;
import com.magister.backend.domain.workspace.dto.SaveWorkspaceRequest;
import com.magister.backend.domain.workspace.dto.WorkspaceData;
import com.magister.backend.domain.workspace.dto.WorkspaceResponse;
import com.magister.backend.domain.workspace.service.support.WorkspaceCodec;
import com.magister.backend.domain.workspace.service.validation.WorkspaceReferenceValidator;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkspaceService {
    private final WorkAccessService workAccessService;
    private final WorkspaceSnapshotService snapshotService;
    private final WorkspacePersistenceService persistenceService;
    private final WorkspaceCodec workspaceCodec;
    private final WorkspaceReferenceValidator referenceValidator;

    /** 소유권을 먼저 확인하고 미생성 문서는 빈 초기 상태로 반환한다. */
    @Transactional(readOnly = true)
    public WorkspaceResponse get(Long ownerId, Long workId) {
        return snapshotService.getForOwnedWork(workAccessService.requireOwned(ownerId, workId));
    }

    /** 검토·사건 JSON 직렬화는 잠금 밖에서 처리한다. 최신 회차 참조 검증은 저장 트랜잭션에서 수행한다. */
    public WorkspaceResponse save(Long ownerId, Long workId, SaveWorkspaceRequest request) {
        if (Boolean.TRUE.equals(request.separateChapters())) {
            if (!request.content().chapters().isEmpty()) {
                throw new CustomException(CommonErrorCode.INVALID_REQUEST);
            }
        } else {
            // 구형 초안의 중복 ID·잘못된 참조를 INSERT 이전에 400으로 거절한다.
            referenceValidator.validate(request.content());
            workspaceCodec.encode(request.content());
        }
        // 원고는 회차 저장 API만 수정한다. workspace의 영속 JSON에는 검토·사건만 둔다.
        String encodedContent =
                workspaceCodec.encode(
                        new WorkspaceData(
                                List.of(),
                                request.content().analyses(),
                                request.content().events()));
        return persistenceService.save(ownerId, workId, request, encodedContent);
    }
}
