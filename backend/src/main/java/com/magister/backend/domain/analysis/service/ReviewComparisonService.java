package com.magister.backend.domain.analysis.service;

import com.magister.backend.domain.analysis.converter.AnalysisConverter;
import com.magister.backend.domain.analysis.dto.ReviewComparisonResponse;
import com.magister.backend.domain.analysis.dto.ReviewComparisonResponse.ComparedCandidate;
import com.magister.backend.domain.analysis.entity.AnalysisJob;
import com.magister.backend.domain.analysis.entity.AnalysisJobStatus;
import com.magister.backend.domain.analysis.entity.AnalysisSource;
import com.magister.backend.domain.analysis.entity.ReviewCandidate;
import com.magister.backend.domain.analysis.entity.ReviewEvidence;
import com.magister.backend.domain.analysis.error.AnalysisErrorCode;
import com.magister.backend.domain.analysis.repository.AnalysisJobRepository;
import com.magister.backend.domain.analysis.repository.AnalysisSourceRepository;
import com.magister.backend.domain.analysis.repository.ReviewCandidateRepository;
import com.magister.backend.domain.analysis.repository.ReviewEvidenceRepository;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.global.error.exception.CustomException;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewComparisonService {
    private final AnalysisJobRepository jobRepository;
    private final AnalysisSourceRepository sourceRepository;
    private final ReviewCandidateRepository candidateRepository;
    private final ReviewEvidenceRepository evidenceRepository;
    private final AnalysisConverter analysisConverter;
    private final ObjectMapper objectMapper;

    /**
     * 같은 회차의 이전 완료(SUCCEEDED) 분석 결과와 현재 분석 결과를 비교하여 유지된 쟁점·새 후보·이번 결과에서 다시 나오지 않은 후보·비교 불가 항목으로
     * 분류한다. 다시 나오지 않은 후보를 자동 해결로 단정하지 않으며, 범위 변경이나 원고 이동으로 단순 매칭이 불확실한 항목은 비교 불가로 분리한다.
     */
    @Transactional(readOnly = true)
    public ReviewComparisonResponse compare(
            Long ownerId, Long workId, String jobId, String baseJobId) {
        AnalysisJob currentJob =
                jobRepository
                        .findOwned(jobId, workId, ownerId)
                        .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND));

        if (currentJob.getStatus() != AnalysisJobStatus.SUCCEEDED) {
            throw new CustomException(AnalysisErrorCode.COMPARISON_NOT_READY);
        }

        Optional<AnalysisJob> baseJobOptional =
                resolveBaseJob(ownerId, workId, currentJob, baseJobId);
        List<AnalysisSource> currentSources =
                sourceRepository.findByJob_IdOrderByIdAsc(currentJob.getId());
        AnalysisSource currentTarget = findTargetSource(currentSources);
        long currentRevision = currentTarget == null ? 1L : currentTarget.getRevision();

        if (baseJobOptional.isEmpty()) {
            return analysisConverter.toEmptyComparison(currentJob, currentRevision);
        }

        AnalysisJob baseJob = baseJobOptional.get();
        List<AnalysisSource> baseSources =
                sourceRepository.findByJob_IdOrderByIdAsc(baseJob.getId());
        AnalysisSource baseTarget = findTargetSource(baseSources);
        Long baseRevision = baseTarget == null ? null : baseTarget.getRevision();

        if (currentTarget == null || baseTarget == null) {
            throw new CustomException(AnalysisErrorCode.SNAPSHOT_UNAVAILABLE);
        }
        Map<String, SourceVersion> currentScope = collectScopeSources(currentSources);
        Map<String, SourceVersion> baseScope = collectScopeSources(baseSources);
        boolean currentConsistency = readBooleanFlag(currentJob.getInput(), "consistency", true);
        boolean currentMotivation = readBooleanFlag(currentJob.getInput(), "motivation", true);
        boolean baseConsistency = readBooleanFlag(baseJob.getInput(), "consistency", true);
        boolean baseMotivation = readBooleanFlag(baseJob.getInput(), "motivation", true);

        boolean scopeChanged =
                !currentScope.equals(baseScope)
                        || currentConsistency != baseConsistency
                        || currentMotivation != baseMotivation;
        boolean manuscriptModified =
                !Objects.equals(currentTarget.getContentHash(), baseTarget.getContentHash());

        List<ReviewCandidate> currentCandidates =
                candidateRepository.findAllByJob_IdOrderByIdAsc(currentJob.getId());
        List<ReviewCandidate> baseCandidates =
                candidateRepository.findAllByJob_IdOrderByIdAsc(baseJob.getId());
        Map<String, List<ReviewEvidence>> currentEvidencesByReview =
                loadEvidencesByReview(currentCandidates);
        Map<String, List<ReviewEvidence>> baseEvidencesByReview =
                loadEvidencesByReview(baseCandidates);

        List<ComparedCandidate> retained = new ArrayList<>();
        List<ComparedCandidate> added = new ArrayList<>();
        List<ComparedCandidate> notReappeared = new ArrayList<>();
        List<ComparedCandidate> incomparable = new ArrayList<>();

        Set<String> incomparableBaseIds = new HashSet<>();
        for (ReviewCandidate baseCandidate : baseCandidates) {
            List<ReviewEvidence> evidences =
                    baseEvidencesByReview.getOrDefault(baseCandidate.getId(), List.of());
            boolean typeDisabledNow =
                    (baseCandidate.getType().equals("설정 충돌 후보") && !currentConsistency)
                            || (baseCandidate.getType().equals("설명 보완") && !currentMotivation);
            boolean ambiguousQuote = manuscriptModified
                    && (hasRepeatedQuote(baseTarget.getContent(), baseCandidate.getQuote())
                            || hasRepeatedQuote(currentTarget.getContent(), baseCandidate.getQuote()));
            if (typeDisabledNow || !hasUnchangedEvidence(evidences, currentScope) || ambiguousQuote) {
                incomparableBaseIds.add(baseCandidate.getId());
                incomparable.add(
                        analysisConverter.toComparedCandidate(
                                "INCOMPARABLE",
                                baseCandidate,
                                null,
                                sourceTitles(evidences),
                                "근거 자료·검토 유형이 달라졌거나 같은 원고 인용이 여러 위치에 있어 비교할 수 없습니다."));
            }
        }

        Set<String> incomparableCurrentIds = new HashSet<>();
        for (ReviewCandidate currentCandidate : currentCandidates) {
            List<ReviewEvidence> evidences =
                    currentEvidencesByReview.getOrDefault(currentCandidate.getId(), List.of());
            boolean typeDisabledBefore =
                    (currentCandidate.getType().equals("설정 충돌 후보") && !baseConsistency)
                            || (currentCandidate.getType().equals("설명 보완") && !baseMotivation);
            boolean ambiguousQuote = manuscriptModified
                    && (hasRepeatedQuote(baseTarget.getContent(), currentCandidate.getQuote())
                            || hasRepeatedQuote(currentTarget.getContent(), currentCandidate.getQuote()));
            if (typeDisabledBefore || !hasUnchangedEvidence(evidences, baseScope) || ambiguousQuote) {
                incomparableCurrentIds.add(currentCandidate.getId());
                incomparable.add(
                        analysisConverter.toComparedCandidate(
                                "INCOMPARABLE",
                                currentCandidate,
                                null,
                                sourceTitles(evidences),
                                "근거 자료·검토 유형이 달라졌거나 같은 원고 인용이 여러 위치에 있어 비교할 수 없습니다."));
            }
        }

        Set<String> matchedBaseIds = new HashSet<>();

        for (ReviewCandidate currentCandidate : currentCandidates) {
            if (incomparableCurrentIds.contains(currentCandidate.getId())) {
                continue;
            }
            List<ReviewEvidence> currentEvidences =
                    currentEvidencesByReview.getOrDefault(currentCandidate.getId(), List.of());
            Set<EvidenceSpan> currentEvidenceSpans = evidenceSpans(currentEvidences);

            ReviewCandidate matchedBase = null;
            for (ReviewCandidate baseCandidate : baseCandidates) {
                if (incomparableBaseIds.contains(baseCandidate.getId())
                        || matchedBaseIds.contains(baseCandidate.getId())) {
                    continue;
                }
                List<ReviewEvidence> baseEvidences =
                        baseEvidencesByReview.getOrDefault(baseCandidate.getId(), List.of());
                boolean sameLocation = manuscriptModified
                        || currentCandidate.getStartOffset() == baseCandidate.getStartOffset();
                if (sameLocation && currentCandidate.getType().equals(baseCandidate.getType())
                        && currentCandidate.getQuote().equals(baseCandidate.getQuote())
                        && currentEvidenceSpans.equals(evidenceSpans(baseEvidences))) {
                    matchedBase = baseCandidate;
                    break;
                }
            }

            if (matchedBase != null) {
                matchedBaseIds.add(matchedBase.getId());
                retained.add(
                        analysisConverter.toComparedCandidate(
                                "RETAINED",
                                currentCandidate,
                                matchedBase,
                                sourceTitles(currentEvidences),
                                matchedBase.getStartOffset() == currentCandidate.getStartOffset()
                                        ? "이전 분석과 동일한 원고 위치·근거 맥락에서 유지된 쟁점입니다."
                                        : "원고 편집 후 위치가 이동했으나 동일한 근거 맥락에서 계속 검출된 쟁점입니다."));
            } else {
                added.add(
                        analysisConverter.toComparedCandidate(
                                "ADDED",
                                currentCandidate,
                                null,
                                sourceTitles(currentEvidences),
                                "이번 원고 분석에서 새로 검출된 후보입니다."));
            }
        }

        for (ReviewCandidate baseCandidate : baseCandidates) {
            if (incomparableBaseIds.contains(baseCandidate.getId())
                    || matchedBaseIds.contains(baseCandidate.getId())) {
                continue;
            }
            List<ReviewEvidence> baseEvidences =
                    baseEvidencesByReview.getOrDefault(baseCandidate.getId(), List.of());
            notReappeared.add(
                    analysisConverter.toComparedCandidate(
                            "NOT_REAPPEARED",
                            baseCandidate,
                            null,
                            sourceTitles(baseEvidences),
                            "이번 분석 결과에서 다시 나오지 않은 후보입니다. 원고 수정 내용과 근거를 직접 확인해 주세요."));
        }

        return analysisConverter.toComparisonResponse(
                currentJob,
                baseJob,
                currentRevision,
                baseRevision,
                scopeChanged,
                manuscriptModified,
                retained,
                added,
                notReappeared,
                incomparable);
    }

    private Optional<AnalysisJob> resolveBaseJob(
            Long ownerId, Long workId, AnalysisJob currentJob, String baseJobId) {
        if (baseJobId != null && !baseJobId.isBlank()) {
            return Optional.of(jobRepository.findOwned(baseJobId, workId, ownerId)
                    .filter(job -> job.getStatus() == AnalysisJobStatus.SUCCEEDED)
                    .filter(job -> currentJob.getChapterKey() != null
                            && currentJob.getChapterKey().equals(job.getChapterKey()))
                    .filter(job -> job.getCreatedAt().isBefore(currentJob.getCreatedAt())
                            || (job.getCreatedAt().equals(currentJob.getCreatedAt())
                                    && job.getId().compareTo(currentJob.getId()) < 0))
                    .orElseThrow(() -> new CustomException(AnalysisErrorCode.COMPARISON_INVALID_BASE)));
        }
        if (currentJob.getChapterKey() == null) {
            return Optional.empty();
        }
        return jobRepository.findEarlierCompleted(
                        workId, currentJob.getChapterKey(), AnalysisJobStatus.SUCCEEDED,
                        currentJob.getCreatedAt(), currentJob.getId(), PageRequest.of(0, 1))
                .stream().findFirst();
    }

    private AnalysisSource findTargetSource(List<AnalysisSource> sources) {
        for (AnalysisSource source : sources) {
            if (source.getType().equals("TARGET")) {
                return source;
            }
        }
        return null;
    }

    private Map<String, SourceVersion> collectScopeSources(List<AnalysisSource> sources) {
        return sources.stream()
                .filter(source -> !source.getType().equals("TARGET"))
                .collect(Collectors.toMap(this::sourceKey, this::sourceVersion));
    }

    private String sourceKey(AnalysisSource source) {
        return source.getType() + ":" + source.getSourceKey();
    }

    private SourceVersion sourceVersion(AnalysisSource source) {
        return new SourceVersion(source.getRevision(), source.getContentHash());
    }

    private boolean hasUnchangedEvidence(
            List<ReviewEvidence> evidences, Map<String, SourceVersion> otherScope) {
        return !evidences.isEmpty() && evidences.stream().allMatch(evidence -> {
            AnalysisSource source = evidence.getSource();
            return sourceVersion(source).equals(otherScope.get(sourceKey(source)));
        });
    }

    private Map<String, List<ReviewEvidence>> loadEvidencesByReview(
            List<ReviewCandidate> candidates) {
        if (candidates.isEmpty()) {
            return Map.of();
        }
        List<String> ids = candidates.stream().map(ReviewCandidate::getId).toList();
        return evidenceRepository.findForReviews(ids).stream()
                .collect(Collectors.groupingBy(item -> item.getReview().getId()));
    }

    private Set<EvidenceSpan> evidenceSpans(List<ReviewEvidence> evidences) {
        return evidences.stream()
                .map(evidence -> new EvidenceSpan(sourceKey(evidence.getSource()),
                        evidence.getStartOffset(), evidence.getEndOffset(), evidence.getQuote()))
                .collect(Collectors.toSet());
    }

    private record SourceVersion(long revision, String contentHash) {}

    private record EvidenceSpan(String sourceKey, int startOffset, int endOffset, String quote) {}

    private List<String> sourceTitles(List<ReviewEvidence> evidences) {
        return evidences.stream()
                .map(evidence -> evidence.getSource().getTitle())
                .distinct()
                .toList();
    }

    private boolean hasRepeatedQuote(String content, String quote) {
        int first = content.indexOf(quote);
        return first >= 0 && content.indexOf(quote, first + 1) >= 0;
    }

    private boolean readBooleanFlag(String rawInput, String fieldName, boolean defaultValue) {
        if (rawInput == null || rawInput.isBlank()) {
            return defaultValue;
        }
        try {
            JsonNode node = objectMapper.readTree(rawInput).path(fieldName);
            if (node.isBoolean()) {
                return node.asBoolean();
            }
            return defaultValue;
        } catch (RuntimeException ignored) {
            return defaultValue;
        }
    }
}
