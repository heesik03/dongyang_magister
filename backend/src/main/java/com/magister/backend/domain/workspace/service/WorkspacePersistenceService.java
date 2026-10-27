package com.magister.backend.domain.workspace.service;

import com.magister.backend.domain.chapter.service.ChapterWorkspaceService;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.work.service.WorkAccessService;
import com.magister.backend.domain.workspace.dto.SaveWorkspaceRequest;
import com.magister.backend.domain.workspace.dto.WorkspaceData;
import com.magister.backend.domain.workspace.dto.WorkspaceResponse;
import com.magister.backend.domain.workspace.entity.Workspace;
import com.magister.backend.domain.workspace.repository.WorkspaceRepository;
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
public class WorkspacePersistenceService {
    private final WorkAccessService workAccessService;
    private final WorkspaceRepository workspaceRepository;
    private final ChapterWorkspaceService chapterWorkspaceService;
    private final WorkspaceReferenceValidator referenceValidator;
    private final WorkspaceSnapshotService snapshotService;
    private final WorkspaceCodec workspaceCodec;

    /** 직렬화한 검토·사건과 현재 회차의 참조를 검증하고 workspace 버전으로 저장한다. */
    @Transactional
    public WorkspaceResponse save(
            Long ownerId, Long workId, SaveWorkspaceRequest request, String content) {
        Work work = workAccessService.lockOwned(ownerId, workId);
        Workspace workspace =
                workspaceRepository.findById(workId).orElseGet(() -> new Workspace(work));
        WorkspaceData stored =
                workspace.getContent() == null
                        ? new WorkspaceData(List.of(), List.of(), List.of())
                        : workspaceCodec.decode(workspace.getContent());
        boolean initialLegacyImport =
                workspace.getRevision() == 0
                        && !Boolean.TRUE.equals(request.separateChapters())
                        && !Boolean.TRUE.equals(request.separateReviews());
        if (!initialLegacyImport
                && !Boolean.TRUE.equals(request.separateReviews())
                && !stored.analyses().equals(request.content().analyses())) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
        WorkspaceData effective =
                new WorkspaceData(
                        List.of(),
                        Boolean.TRUE.equals(request.separateReviews())
                                ? stored.analyses()
                                : request.content().analyses(),
                        request.content().events());
        if (Boolean.TRUE.equals(request.separateReviews())) {
            content = workspaceCodec.encode(effective);
        }
        if (request.mutationId().equals(workspace.getMutationId())) {
            if (!content.equals(workspace.getContent())) {
                throw new CustomException(CommonErrorCode.INVALID_REQUEST);
            }
            if (!Boolean.TRUE.equals(request.separateChapters())
                    && !chapterWorkspaceService.read(workId).equals(request.content().chapters())) {
                throw new CustomException(CommonErrorCode.INVALID_REQUEST);
            }
            return snapshotService.getForOwnedWork(work);
        }
        if (!Boolean.TRUE.equals(request.separateChapters())) {
            chapterWorkspaceService.acceptLegacy(
                    work, workspace.getRevision(), request.content().chapters());
        }
        if (workspace.getRevision() != request.version()) {
            throw new CustomException(WorkErrorCode.VERSION_CONFLICT);
        }
        referenceValidator.validate(
                new WorkspaceData(
                        chapterWorkspaceService.read(workId),
                        effective.analyses(),
                        request.content().events()));

        workspace.replace(content, request.mutationId());
        // 프런트엔드의 workVersion 계약을 보존한다. 모든 하위 쓰기는 부모 잠금 순서를 따른다.
        work.touch();
        workspaceRepository.saveAndFlush(workspace);
        return snapshotService.getForOwnedWork(work);
    }
}
