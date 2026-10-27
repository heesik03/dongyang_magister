package com.magister.backend.domain.analysis.service;

import com.magister.backend.domain.analysis.converter.AnalysisConverter;
import com.magister.backend.domain.analysis.dto.AnalysisInput;
import com.magister.backend.domain.analysis.dto.AnalysisPreviewResponse;
import com.magister.backend.domain.analysis.dto.AnalysisRequest;
import com.magister.backend.domain.analysis.error.AnalysisErrorCode;
import com.magister.backend.domain.analysis.service.support.AnalysisInputFactory;
import com.magister.backend.domain.analysis.service.support.AnalysisPreviewToken;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.service.WorkAccessService;
import com.magister.backend.domain.workspace.service.WorkspaceSnapshotService;
import com.magister.backend.global.config.AnalysisExecutionProperties;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.gemini.GeminiProperties;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AnalysisPreviewService {
    public static final String POLICY_VERSION = "2026-10-27.1";
    private final WorkAccessService workAccessService;
    private final WorkspaceSnapshotService workspaceSnapshotService;
    private final AnalysisInputFactory analysisInputFactory;
    private final AnalysisPreviewToken previewToken;
    private final AnalysisExecutionProperties executionProperties;
    private final GeminiProperties geminiProperties;
    private final AnalysisConverter analysisConverter;
    private final ObjectMapper objectMapper;

    /** 저장된 자료만 확인하며 AI 호출·작업 생성·동의 기록은 하지 않는다. */
    @Transactional(readOnly = true)
    public AnalysisPreviewResponse preview(Long ownerId, Long workId, AnalysisRequest request) {
        Work work = workAccessService.requireOwned(ownerId, workId);
        AnalysisInput input =
                analysisInputFactory.create(
                        workId, request, workspaceSnapshotService.getForOwnedWork(work));
        String snapshot = encode(input);
        Instant expiresAt = Instant.now().plusSeconds(600);
        String token = previewToken.issue(payload(ownerId, workId, snapshot), expiresAt);
        return analysisConverter.toPreview(
                token,
                expiresAt,
                input,
                POLICY_VERSION,
                geminiProperties.model(),
                executionProperties.getRetention());
    }

    /** 접수 트랜잭션의 같은 잠금 아래에서 재구성한 자료가 확인한 내용과 정확히 일치해야 한다. */
    public String verify(Long ownerId, Long workId, AnalysisRequest request, AnalysisInput input) {
        if (request.previewToken() == null
                || request.previewToken().isBlank()
                || !POLICY_VERSION.equals(request.consentPolicyVersion())) {
            throw new CustomException(AnalysisErrorCode.PREVIEW_REQUIRED);
        }
        String snapshot = encode(input);
        previewToken.verify(request.previewToken(), payload(ownerId, workId, snapshot));
        return snapshot;
    }

    private String encode(AnalysisInput input) {
        String snapshot = objectMapper.writeValueAsString(input);
        if (snapshot.length() > 200000) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
        return snapshot;
    }

    private String payload(Long ownerId, Long workId, String snapshot) {
        return ownerId
                + "|"
                + workId
                + "|"
                + POLICY_VERSION
                + "|"
                + geminiProperties.model()
                + "|"
                + executionProperties.getRetention()
                + "|"
                + snapshot;
    }
}
