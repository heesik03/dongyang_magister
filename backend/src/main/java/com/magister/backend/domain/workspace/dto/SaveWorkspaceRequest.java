package com.magister.backend.domain.workspace.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record SaveWorkspaceRequest(
        @Min(0) long version,
        @NotBlank @Size(max = 64) String mutationId,
        @NotNull @Valid WorkspaceData content) {}
