package com.magister.backend.domain.analysis.dto;

import com.magister.backend.domain.decision.dto.DecisionResponse;
import com.magister.backend.global.pagination.OffsetPageResponse;

import java.util.List;
import java.util.Map;

public record ReviewReportResponse(
        OffsetPageResponse<ReviewResponse> candidates,
        boolean legacy,
        boolean stale,
        Map<String, DecisionResponse> decisions,
        ScopeSummary summary) {
    public ReviewReportResponse(
            OffsetPageResponse<ReviewResponse> candidates,
            boolean legacy,
            boolean stale,
            Map<String, DecisionResponse> decisions) {
        this(candidates, legacy, stale, decisions, null);
    }

    public record ScopeSummary(
            String scopeNotice,
            int settingCount,
            int previousChapterCount,
            int totalCandidates,
            int conflictCount,
            int supplementCount,
            int unreviewedCount,
            int issueGroupCount,
            List<PriorityIssue> priorityItems) {}

    public record PriorityIssue(
            String groupKey,
            String title,
            String type,
            String summary,
            List<String> candidateIds,
            List<String> sourceTitles) {}
}
