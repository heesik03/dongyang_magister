package com.magister.backend.domain.workspace.converter;

import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.workspace.dto.WorkspaceData;
import com.magister.backend.domain.workspace.dto.WorkspaceResponse;
import com.magister.backend.domain.workspace.entity.Workspace;
import com.magister.backend.domain.workspace.service.support.WorkspaceCodec;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class WorkspaceConverter {
    private final WorkspaceCodec workspaceCodec;

    public WorkspaceResponse empty(Work work) {
        return new WorkspaceResponse(0, WorkspaceData.empty(), Instant.EPOCH, work.getVersion());
    }

    public WorkspaceResponse toResponse(Workspace workspace) {
        return toResponse(workspace, workspaceCodec.decode(workspace.getContent()));
    }

    public WorkspaceResponse toResponse(Workspace workspace, WorkspaceData content) {
        return new WorkspaceResponse(
                workspace.getRevision(),
                content,
                workspace.getUpdatedAt(),
                workspace.getWork().getVersion());
    }
}
