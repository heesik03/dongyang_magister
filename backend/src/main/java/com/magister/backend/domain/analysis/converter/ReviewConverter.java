package com.magister.backend.domain.analysis.converter;

import com.magister.backend.domain.analysis.dto.ReviewReportResponse;
import com.magister.backend.domain.analysis.dto.ReviewResponse;
import com.magister.backend.domain.analysis.entity.AnalysisSource;
import com.magister.backend.domain.analysis.entity.ReviewCandidate;
import com.magister.backend.domain.analysis.entity.ReviewEvidence;
import com.magister.backend.domain.decision.dto.DecisionResponse;
import com.magister.backend.global.pagination.OffsetPageResponse;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.magister.backend.domain.analysis.dto.HistoryDecision;
import com.magister.backend.domain.analysis.dto.HistoryJobReport;
import com.magister.backend.domain.analysis.entity.HistoryFindingDecision;
import com.magister.backend.domain.history.dto.HistoryCheck;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ReviewConverter {
    public HistoryDecision toHistoryDecision(HistoryFindingDecision entity) {
        return new HistoryDecision(entity.getChoice(), entity.getReason());
    }

    public HistoryJobReport toHistoryJobReport(
            long revision,
            HistoryJobReport.Status historicalStatus,
            HistoryJobReport.Status standardStatus,
            List<HistoryCheck.Finding> findings) {
        return new HistoryJobReport(
                revision, Instant.now(), historicalStatus, standardStatus, List.copyOf(findings));
    }

    public ReviewReportResponse toReport(
            Page<ReviewCandidate> candidates,
            List<ReviewEvidence> evidences,
            boolean legacy,
            boolean stale,
            Map<String, DecisionResponse> decisions, HistoryJobReport history, Map<Integer, HistoryDecision> historyDecisions) {
        Map<String, List<ReviewEvidence>> byReview =
                evidences.stream().collect(Collectors.groupingBy(item -> item.getReview().getId()));
        return new ReviewReportResponse(
                OffsetPageResponse.from(
                        candidates.map(
                                candidate ->
                                        toCandidateResponse(
                                                candidate,
                                                byReview.getOrDefault(
                                                        candidate.getId(), List.of())))),
                legacy,
                stale,
                decisions, history, historyDecisions);
    }

    public ReviewResponse toCandidateResponse(
            ReviewCandidate candidate, List<ReviewEvidence> candidateEvidences) {
        List<ReviewResponse.Evidence> mappedEvidences =
                candidateEvidences.stream().map(this::toEvidence).toList();
        return new ReviewResponse(
                candidate.getId(),
                candidate.getType(),
                candidate.getQuote(),
                candidate.getTargetSource().getId(),
                candidate.getStartOffset(),
                candidate.getEndOffset(),
                candidate.getReason(),
                candidate.getSuggestion(),
                mappedEvidences);
    }

    private ReviewResponse.Evidence toEvidence(ReviewEvidence evidence) {
        AnalysisSource source = evidence.getSource();
        return new ReviewResponse.Evidence(
                source.getId(),
                source.getType(),
                source.getSourceKey(),
                source.getTitle(),
                source.getRevision(),
                evidence.getQuote(),
                evidence.getStartOffset(),
                evidence.getEndOffset(),
                evidence.getExplanation());
    }
}
