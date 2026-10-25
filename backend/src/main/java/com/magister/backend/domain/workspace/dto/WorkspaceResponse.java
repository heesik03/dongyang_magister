package com.magister.backend.domain.workspace.dto;

import java.time.Instant;

public record WorkspaceResponse(
        long version, WorkspaceData content, Instant updatedAt, long workVersion) {}
