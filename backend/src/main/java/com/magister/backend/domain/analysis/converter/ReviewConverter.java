package com.magister.backend.domain.analysis.converter;

import com.magister.backend.domain.analysis.dto.ReviewReportResponse;
import com.magister.backend.domain.analysis.dto.ReviewReportResponse.PriorityIssue;
import com.magister.backend.domain.analysis.dto.ReviewReportResponse.ScopeSummary;
import com.magister.backend.domain.analysis.dto.ReviewResponse;
import com.magister.backend.domain.analysis.dto.ReviewResponse.RevisionOption;
import com.magister.backend.domain.analysis.entity.AnalysisSource;
import com.magister.backend.domain.analysis.entity.ReviewCandidate;
import com.magister.backend.domain.analysis.entity.ReviewEvidence;
import com.magister.backend.domain.decision.dto.DecisionResponse;
import com.magister.backend.global.pagination.OffsetPageResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ReviewConverter {
    private final ObjectMapper objectMapper;

    @Autowired
    public ReviewConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ReviewConverter() {
        this(new ObjectMapper());
    }

    public ReviewReportResponse toReport(
            Page<ReviewCandidate> candidates,
            List<ReviewEvidence> evidences,
            boolean legacy,
            boolean stale,
            Map<String, DecisionResponse> decisions) {
        return toReport(candidates, evidences, legacy, stale, decisions, null);
    }

    public ReviewReportResponse toReport(
            Page<ReviewCandidate> candidates,
            List<ReviewEvidence> evidences,
            boolean legacy,
            boolean stale,
            Map<String, DecisionResponse> decisions,
            ScopeSummary summary) {
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
                decisions,
                summary);
    }

    public ReviewResponse toCandidateResponse(
            ReviewCandidate candidate, List<ReviewEvidence> candidateEvidences) {
        List<ReviewResponse.Evidence> mappedEvidences =
                candidateEvidences.stream().map(this::toEvidence).toList();
        String groupKey = resolveGroupKey(candidate, candidateEvidences);
        String groupTitle = resolveGroupTitle(candidate, candidateEvidences);
        String confirmedFact = resolveConfirmedFact(candidate, candidateEvidences);
        String interpretation = resolveInterpretation(candidate);
        String missingInfo = resolveMissingInfo(candidate);
        List<RevisionOption> options = resolveOptions(candidate, candidateEvidences);

        return new ReviewResponse(
                candidate.getId(),
                candidate.getType(),
                candidate.getQuote(),
                candidate.getTargetSource().getId(),
                candidate.getStartOffset(),
                candidate.getEndOffset(),
                candidate.getReason(),
                candidate.getSuggestion(),
                mappedEvidences,
                groupKey,
                groupTitle,
                confirmedFact,
                interpretation,
                missingInfo,
                options);
    }

    /**
     * 선택된 분석 범위의 출처·후보·근거만을 바탕으로 범위 종합 요약을 계산한다. 계산 중 오류가 발생해도 개별 후보 조회가 중단되지 않도록 null을 반환한다.
     */
    public ScopeSummary toScopeSummary(
            String scopeNotice, int settingCount, int previousChapterCount, int totalCandidates,
            int conflictCount, int supplementCount, int unreviewedCount, int issueGroupCount,
            List<PriorityIssue> priorityItems) {
        return new ScopeSummary(scopeNotice, settingCount, previousChapterCount, totalCandidates,
                conflictCount, supplementCount, unreviewedCount, issueGroupCount, priorityItems);
    }

    public PriorityIssue toPriorityIssue(
            String key, String title, String type, String summary, List<String> candidateIds,
            List<String> sourceTitles) {
        return new PriorityIssue(key, title, type, summary, candidateIds, sourceTitles);
    }

    public String resolveGroupKey(
            ReviewCandidate candidate, List<ReviewEvidence> candidateEvidences) {
        String providerKey = candidate.getIssueGroupKey();
        if (providerKey == null || providerKey.isBlank() || candidateEvidences.isEmpty()) {
            return "candidate:" + candidate.getId();
        }
        // 공급자 그룹 키가 같아도 원고 줄과 근거 인용 범위가 다르면 별개의 쟁점이다.
        String target = candidate.getTargetSource().getContent();
        int lineStart = target.lastIndexOf('\n', candidate.getStartOffset() - 1) + 1;
        String evidenceSignature = candidateEvidences.stream()
                .map(evidence -> evidence.getSource().getId()
                        + ":" + evidence.getStartOffset() + ":" + evidence.getEndOffset())
                .distinct().sorted().collect(Collectors.joining(","));
        return candidate.getType() + ":" + lineStart + ":" + evidenceSignature + ":" + providerKey;
    }

    public String resolveGroupTitle(
            ReviewCandidate candidate, List<ReviewEvidence> candidateEvidences) {
        if (candidate.getIssueGroupTitle() != null && !candidate.getIssueGroupTitle().isBlank()) {
            return candidate.getIssueGroupTitle();
        }
        List<String> titles =
                candidateEvidences.stream()
                        .map(evidence -> evidence.getSource().getTitle())
                        .distinct()
                        .toList();
        if (titles.isEmpty()) {
            return "[" + candidate.getType() + "] 원고 구간 검토";
        }
        return "[" + candidate.getType() + "] " + String.join(" · ", titles) + " 관련 쟁점";
    }

    private String resolveConfirmedFact(
            ReviewCandidate candidate, List<ReviewEvidence> candidateEvidences) {
        String claimedFact = candidate.getConfirmedFact();
        if (claimedFact != null && (claimedFact.equals(candidate.getQuote())
                || candidateEvidences.stream().anyMatch(evidence -> claimedFact.equals(evidence.getQuote())))) {
            return claimedFact;
        }
        String evidenceSummary =
                candidateEvidences.stream()
                        .map(
                                evidence ->
                                        evidence.getSource().getTitle()
                                                + "(\""
                                                + shorten(evidence.getQuote(), 40)
                                                + "\")")
                        .collect(Collectors.joining(", "));
        if (evidenceSummary.isEmpty()) {
            return "현재 회차 원고 인용(\"" + shorten(candidate.getQuote(), 50) + "\")에서 확인된 서술입니다.";
        }
        return "현재 회차 원고(\""
                + shorten(candidate.getQuote(), 45)
                + "\")와 선택 근거 자료 "
                + evidenceSummary
                + "에 기록된 사실입니다.";
    }

    private String resolveInterpretation(ReviewCandidate candidate) {
        if (candidate.getInterpretation() != null && !candidate.getInterpretation().isBlank()) {
            return candidate.getInterpretation();
        }
        return candidate.getReason();
    }

    private String resolveMissingInfo(ReviewCandidate candidate) {
        if (candidate.getMissingInfo() != null && !candidate.getMissingInfo().isBlank()) {
            return candidate.getMissingInfo();
        }
        if (candidate.getType().equals("설정 충돌 후보")) {
            return "선택한 분석 범위 안에서 기존 설정·이전 회차 제약이 해제되거나 예외가 적용되는 조건 설명이 확인되지 않았습니다.";
        }
        return "선택한 분석 범위 안에서 인물의 행동 변화나 상황 전개를 뒷받침하는 계기·맥락 서술이 추가로 확인되지 않았습니다.";
    }

    private List<RevisionOption> resolveOptions(
            ReviewCandidate candidate, List<ReviewEvidence> candidateEvidences) {
        if (candidate.getOptionsJson() != null && !candidate.getOptionsJson().isBlank()) {
            try {
                List<RevisionOption> parsed =
                        objectMapper.readValue(
                                candidate.getOptionsJson(),
                                new TypeReference<List<RevisionOption>>() {});
                if (parsed != null && !parsed.isEmpty()) {
                    return parsed;
                }
            } catch (RuntimeException ignored) {
                // 과거 형식이거나 역직렬화 실패 시 기본 선택지로 안전하게 대체한다.
            }
        }
        String sourceNames =
                candidateEvidences.stream()
                        .map(evidence -> evidence.getSource().getTitle())
                        .distinct()
                        .collect(Collectors.joining(", "));
        String targetReference = sourceNames.isEmpty() ? "선택한 근거 자료" : sourceNames;
        return List.of(
                new RevisionOption(
                        "선택지 1 · 원고 서술 보완",
                        candidate.getSuggestion(),
                        targetReference + "의 기존 설정·서술 기준을 그대로 유지하려는 경우",
                        "현재 회차 원고 구간에 변화 계기나 조건 설명을 보강해 근거 자료와의 정합성을 맞춥니다."),
                new RevisionOption(
                        "선택지 2 · 의도된 전개·예외 조건 기록",
                        "현재 원고 본문은 유지하고, 작가 판단('의도된 설정' 또는 '보류')에 적용 회차·장면 범위와 조건을 기록합니다.",
                        "이번 장면의 서술이 의도된 반전·각색이거나 특정 상황의 예외인 경우",
                        "원고를 자동 수정하지 않고 작가의 의도를 판단 이력으로 남겨 다음 재분석 시 참고 자료로 활용합니다."));
    }

    private String shorten(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r\n", " ").replace('\n', ' ').strip();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "…";
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
