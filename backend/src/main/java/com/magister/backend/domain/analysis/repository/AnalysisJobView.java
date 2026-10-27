package com.magister.backend.domain.analysis.repository;

import com.magister.backend.domain.analysis.entity.AnalysisJobStatus;

/** 폴링에 필요한 필드만 조회해 원고 스냅샷의 반복 로딩을 방지한다. */
public interface AnalysisJobView {
    String getId();

    AnalysisJobStatus getStatus();

    String getError();

    String getResult();
}
