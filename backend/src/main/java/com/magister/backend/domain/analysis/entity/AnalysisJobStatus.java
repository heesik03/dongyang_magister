package com.magister.backend.domain.analysis.entity;

/** 영속 상태와 외부 API의 대문자 상태값을 함께 정의한다. */
public enum AnalysisJobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public boolean terminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
