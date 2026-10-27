package com.magister.backend.domain.analysis.repository;

import com.magister.backend.domain.analysis.entity.AnalysisJobStatus;

import java.time.Instant;

/** 원고·설정·결과 JSON을 선택하지 않는 이력 목록 전용 projection. */
public interface AnalysisJobSummaryView {
    String getId();

    String getChapterKey();

    String getChapterTitle();

    AnalysisJobStatus getStatus();

    Instant getCreatedAt();

    Instant getUpdatedAt();

    String getPolicyVersion();
}
