package com.magister.backend.domain.analysis.dto;

import com.magister.backend.global.pagination.OffsetPageResponse;

public record ReviewReportResponse(
        OffsetPageResponse<ReviewResponse> candidates, boolean legacy, boolean stale) {}
