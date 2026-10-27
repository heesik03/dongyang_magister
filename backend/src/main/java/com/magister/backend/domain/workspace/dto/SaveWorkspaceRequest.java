package com.magister.backend.domain.workspace.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveWorkspaceRequest(
        @Min(0) long version,
        @NotBlank @Size(max = 64) String mutationId,
        @NotNull @Valid WorkspaceData content,
        Boolean separateChapters,
        Boolean separateReviews) {
    @JsonCreator
    public SaveWorkspaceRequest {}

    public SaveWorkspaceRequest(
            long version, String mutationId, WorkspaceData content, Boolean separateChapters) {
        this(version, mutationId, content, separateChapters, false);
    }

    public SaveWorkspaceRequest(long version, String mutationId, WorkspaceData content) {
        this(version, mutationId, content, false, false);
    }
}
