package com.magister.backend.domain.workspace.service;

import com.magister.backend.domain.chapter.service.ChapterWorkspaceService;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.workspace.converter.WorkspaceConverter;
import com.magister.backend.domain.workspace.dto.WorkspaceResponse;
import com.magister.backend.domain.workspace.repository.WorkspaceRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkspaceSnapshotService {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceConverter converter;
    private final ChapterWorkspaceService chapterWorkspaceService;

    /** 이미 소유권을 확인한 Work를 받아 같은 작품에 대한 재조회를 피한다. */
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public WorkspaceResponse getForOwnedWork(Work work) {
        WorkspaceResponse stored =
                workspaceRepository
                        .findById(work.getId())
                        .map(converter::toResponse)
                        .orElseGet(() -> converter.empty(work));
        return converter.withChapters(stored, chapterWorkspaceService.read(work.getId()));
    }
}
