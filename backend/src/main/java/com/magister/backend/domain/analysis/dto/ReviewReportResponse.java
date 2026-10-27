package com.magister.backend.domain.analysis.dto;

import com.magister.backend.domain.decision.dto.DecisionResponse;
import com.magister.backend.global.pagination.OffsetPageResponse;

import java.util.Map;

public record ReviewReportResponse(
        OffsetPageResponse<ReviewResponse> candidates,
        boolean legacy,
        boolean stale,
        Map<String, DecisionResponse> decisions) {}
