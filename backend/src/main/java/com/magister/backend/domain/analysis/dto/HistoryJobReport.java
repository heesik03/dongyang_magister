package com.magister.backend.domain.analysis.dto;

import com.magister.backend.domain.history.dto.HistoryCheck.Finding;
import java.time.Instant;
import java.util.List;

/** 작업에 고정한 회차 버전과 조회 당시의 공식 근거를 함께 보관한다. */
public record HistoryJobReport(long revision, Instant checkedAt, Status historicalStatus,
        Status standardStatus, List<Finding> findings) {
    public enum Status { NOT_REQUESTED, COMPLETED, PARTIAL, FAILED }
}
