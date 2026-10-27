package com.magister.backend.domain.analysis.converter;

import com.magister.backend.domain.analysis.dto.ReviewReportResponse;
import com.magister.backend.domain.analysis.dto.ReviewResponse;
import com.magister.backend.domain.analysis.entity.AnalysisSource;
import com.magister.backend.domain.analysis.entity.ReviewCandidate;
import com.magister.backend.domain.analysis.entity.ReviewEvidence;
import com.magister.backend.global.pagination.OffsetPageResponse;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ReviewConverter {
    public ReviewReportResponse toReport(
            Page<ReviewCandidate> candidates,
            List<ReviewEvidence> evidences,
            boolean legacy,
            boolean stale) {
        Map<String, List<ReviewEvidence>> byReview =
                evidences.stream().collect(Collectors.groupingBy(item -> item.getReview().getId()));
        return new ReviewReportResponse(
                OffsetPageResponse.from(
                        candidates.map(
                                candidate ->
                                        new ReviewResponse(
                                                candidate.getId(),
                                                candidate.getType(),
                                                candidate.getQuote(),
                                                candidate.getTargetSource().getId(),
                                                candidate.getStartOffset(),
                                                candidate.getEndOffset(),
                                                candidate.getReason(),
                                                candidate.getSuggestion(),
                                                byReview
                                                        .getOrDefault(candidate.getId(), List.of())
                                                        .stream()
                                                        .map(this::toEvidence)
                                                        .toList()))),
                legacy,
                stale);
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
