package com.magister.backend.domain.analysis.dto;

import com.magister.backend.domain.workspace.dto.WorkspaceData.AnalysisData;

public record AnalysisJobResponse(String id, String status, String error, AnalysisData result) {}
