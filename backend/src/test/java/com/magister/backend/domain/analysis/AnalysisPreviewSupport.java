package com.magister.backend.domain.analysis;

import com.magister.backend.domain.analysis.dto.AnalysisPreviewResponse;
import com.magister.backend.domain.analysis.dto.AnalysisRequest;
import com.magister.backend.domain.analysis.service.AnalysisPreviewService;

/** 기존 수명주기 검증도 실제 사전 확인 계약을 거친다. */
public final class AnalysisPreviewSupport {
    private AnalysisPreviewSupport() {}

    public static AnalysisRequest confirm(
            AnalysisPreviewService service, Long ownerId, Long workId, AnalysisRequest request) {
        AnalysisPreviewResponse preview = service.preview(ownerId, workId, request);
        return request.confirmed(preview.previewToken(), preview.policyVersion());
    }
}
