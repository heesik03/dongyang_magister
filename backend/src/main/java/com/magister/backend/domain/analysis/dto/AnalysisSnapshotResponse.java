package com.magister.backend.domain.analysis.dto;

import java.util.List;

public record AnalysisSnapshotResponse(
        String jobId,
        AnalysisInput input,
        List<AnalysisSourceResponse> sources,
        boolean legacy,
        boolean stale) {}
