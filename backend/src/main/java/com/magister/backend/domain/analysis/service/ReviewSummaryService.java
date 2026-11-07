package com.magister.backend.domain.analysis.service;

import com.magister.backend.domain.analysis.converter.ReviewConverter;
import com.magister.backend.domain.analysis.dto.ReviewReportResponse.PriorityIssue;
import com.magister.backend.domain.analysis.dto.ReviewReportResponse.ScopeSummary;
import com.magister.backend.domain.analysis.entity.ReviewCandidate;
import com.magister.backend.domain.analysis.entity.ReviewEvidence;
import com.magister.backend.domain.decision.dto.DecisionResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ReviewSummaryService {
    private final ReviewConverter reviewConverter;

    /** 저장된 후보를 대상으로 우선 확인할 쟁점을 계산한다. 공급자 호출은 하지 않는다. */
    public ScopeSummary buildScopeSummary(
        List<String> sourceTypes,
        List<ReviewCandidate> allCandidates,
        List<ReviewEvidence> allEvidences,
        Map<String, DecisionResponse> decisions) {
        int settingCount =
                (int)
                        sourceTypes.stream()
                                .filter("SETTING"::equals)
                                .count();
        int previousChapterCount =
                (int)
                        sourceTypes.stream()
                                .filter("PREVIOUS"::equals)
                                .count();
        String scopeNotice =
                "선택한 분석 범위(대상 회차 1건, 확정 설정 "
                        + settingCount
                        + "건, 이전 회차 "
                        + previousChapterCount
                        + "건)만 근거로 평가했으며, 선택하지 않은 설정·회차는 평가하지 않았습니다.";

        Map<String, List<ReviewEvidence>> evidencesByReview =
                allEvidences.stream()
                        .collect(Collectors.groupingBy(item -> item.getReview().getId()));

        int conflictCount = 0;
        int supplementCount = 0;
        int unreviewedCount = 0;
        Map<String, List<ReviewCandidate>> grouped = new LinkedHashMap<>();

        for (ReviewCandidate candidate : allCandidates) {
            if (candidate.getType().equals("설정 충돌 후보")) {
                conflictCount++;
            } else {
                supplementCount++;
            }
            DecisionResponse decision = decisions.get(candidate.getId());
            if (decision == null || decision.status().equals("미검토")) {
                unreviewedCount++;
            }
            List<ReviewEvidence> candidateEvidences =
                    evidencesByReview.getOrDefault(candidate.getId(), List.of());
            String key = reviewConverter.resolveGroupKey(candidate, candidateEvidences);
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(candidate);
        }

        List<PriorityIssue> priorityItems =
                grouped.entrySet().stream()
                        .map(
                                entry -> {
                                    List<ReviewCandidate> items = entry.getValue();
                                    ReviewCandidate primary = items.get(0);
                                    List<ReviewEvidence> primaryEvidences =
                                            evidencesByReview.getOrDefault(
                                                    primary.getId(), List.of());
                                    List<String> sourceTitles =
                                            items.stream()
                                                    .flatMap(
                                                            item ->
                                                                    evidencesByReview
                                                                            .getOrDefault(
                                                                                    item
                                                                                            .getId(),
                                                                                    List.of())
                                                                            .stream())
                                                    .map(
                                                            evidence ->
                                                                    evidence.getSource()
                                                                            .getTitle())
                                                    .distinct()
                                                    .toList();
                                    List<String> candidateIds =
                                            items.stream()
                                                    .map(ReviewCandidate::getId)
                                                    .toList();
                                    return reviewConverter.toPriorityIssue(
                                            entry.getKey(),
                                            reviewConverter.resolveGroupTitle(primary, primaryEvidences),
                                            primary.getType(),
                                            primary.getReason(),
                                            candidateIds,
                                            sourceTitles);
                                })
                        .sorted(
                                Comparator.comparingInt(
                                                (PriorityIssue issue) ->
                                                        issue.type().equals("설정 충돌 후보") ? 0 : 1)
                                        .thenComparing(
                                                issue -> -issue.candidateIds().size()))
                        .limit(5)
                        .toList();

        return reviewConverter.toScopeSummary(
                scopeNotice,
                settingCount,
                previousChapterCount,
                allCandidates.size(),
                conflictCount,
                supplementCount,
                unreviewedCount,
                grouped.size(),
                priorityItems);
    }
}
