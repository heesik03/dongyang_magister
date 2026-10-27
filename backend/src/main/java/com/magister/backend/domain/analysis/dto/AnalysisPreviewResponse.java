package com.magister.backend.domain.analysis.dto;

import java.time.Instant;

public record AnalysisPreviewResponse(
        String previewToken,
        Instant expiresAt,
        AnalysisInput input,
        String policyVersion,
        String provider,
        String model,
        String retentionNotice,
        String deletionNotice,
        String providerPolicyUrl) {}
