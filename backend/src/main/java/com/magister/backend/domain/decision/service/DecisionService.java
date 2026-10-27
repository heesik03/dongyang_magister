package com.magister.backend.domain.decision.service;

import com.magister.backend.domain.analysis.entity.ReviewCandidate;
import com.magister.backend.domain.analysis.entity.ReviewEvidence;
import com.magister.backend.domain.analysis.repository.AnalysisJobRepository;
import com.magister.backend.domain.analysis.repository.ReviewCandidateRepository;
import com.magister.backend.domain.analysis.repository.ReviewEvidenceRepository;
import com.magister.backend.domain.chapter.entity.Chapter;
import com.magister.backend.domain.chapter.repository.ChapterRepository;
import com.magister.backend.domain.decision.converter.DecisionConverter;
import com.magister.backend.domain.decision.dto.DecisionRequest;
import com.magister.backend.domain.decision.dto.DecisionResponse;
import com.magister.backend.domain.decision.entity.ReviewDecision;
import com.magister.backend.domain.decision.entity.ReviewDecisionScope;
import com.magister.backend.domain.decision.error.DecisionErrorCode;
import com.magister.backend.domain.decision.repository.ReviewDecisionRepository;
import com.magister.backend.domain.decision.repository.ReviewDecisionScopeRepository;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.work.service.WorkAccessService;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.pagination.OffsetPageRequest;
import com.magister.backend.global.pagination.OffsetPageResponse;

import jakarta.validation.Validator;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DecisionService {
    private final WorkAccessService workAccessService;
    private final ReviewCandidateRepository candidateRepository;
    private final AnalysisJobRepository jobRepository;
    private final ReviewEvidenceRepository evidenceRepository;
    private final ReviewDecisionRepository decisionRepository;
    private final ReviewDecisionScopeRepository scopeRepository;
    private final ChapterRepository chapterRepository;
    private final DecisionBasis decisionBasis;
    private final DecisionConverter decisionConverter;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    /** 작품 잠금 -> 후보 조회 순서로 저장·분석 접수를 직렬화하고 같은 mutation의 재전송은 한 번만 기록한다. */
    @Transactional
    public DecisionResponse save(
            Long ownerId, Long workId, String reviewId, DecisionRequest request) {
        if (request == null || !validator.validate(request).isEmpty()) {
            invalid();
        }
        workAccessService.lockOwned(ownerId, workId);
        ReviewCandidate candidate = owned(ownerId, workId, reviewId);
        jobRepository.lockById(candidate.getJob().getId()).orElseThrow();
        String body = objectMapper.writeValueAsString(request);
        Optional<ReviewDecision> repeated =
                decisionRepository.findByReview_IdAndMutationId(reviewId, request.mutationId());
        if (repeated.isPresent()) {
            if (!repeated.get().getRequestBody().equals(body)) {
                throw new CustomException(DecisionErrorCode.CONFLICT);
            }
            return response(workId, candidate, repeated.get());
        }
        ReviewDecision latest =
                decisionRepository
                        .findFirstByReview_IdOrderByDecisionVersionDesc(reviewId)
                        .orElse(null);
        if ((latest == null ? 0 : latest.getDecisionVersion()) != request.expectedVersion()) {
            throw new CustomException(DecisionErrorCode.CONFLICT);
        }
        List<ReviewEvidence> evidences = evidenceRepository.findForReviews(List.of(reviewId));
        DecisionBasis.State basis =
                decisionBasis.inspect(workId, List.of(candidate), evidences).get(reviewId);
        boolean changed =
                latest == null
                        ? basis.originalChanged()
                        : !latest.getBasisHash().equals(basis.hash());
        if (!request.status().equals("미검토")
                && (!request.basisHash().equals(basis.hash())
                        || !basis.quotesMatch()
                        || (changed && !request.reconfirm()))) {
            throw new CustomException(DecisionErrorCode.BASIS_CHANGED);
        }
        if (request.status().equals("의도된 설정")
                && (request.reason().isBlank() || request.scopes().isEmpty())) {
            invalid();
        }
        if (request.status().equals("미검토") && !request.scopes().isEmpty()) {
            invalid();
        }
        if (new HashSet<>(request.scopes()).size() != request.scopes().size()) {
            invalid();
        }
        ReviewDecision decision =
                new ReviewDecision(
                        candidate,
                        request.expectedVersion() + 1,
                        request.status(),
                        request.reason().strip(),
                        request.mutationId(),
                        body,
                        basis.hash());
        List<ReviewDecisionScope> scopes = buildScopes(workId, candidate, decision, request);
        decisionRepository.saveAndFlush(decision);
        scopeRepository.saveAll(scopes);
        return decisionConverter.toResponse(candidate, decision, scopes, basis);
    }

    private List<ReviewDecisionScope> buildScopes(
            Long workId,
            ReviewCandidate candidate,
            ReviewDecision decision,
            DecisionRequest request) {
        List<ReviewDecisionScope> scopes = new ArrayList<>();
        List<String> chapterKeys =
                request.scopes().stream()
                        .flatMap(scope -> Stream.of(scope.fromChapterId(), scope.toChapterId()))
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        Map<String, Chapter> chapters =
                chapterKeys.isEmpty()
                        ? Map.of()
                        : chapterRepository
                                .findByWork_IdAndChapterKeyIn(workId, chapterKeys)
                                .stream()
                                .collect(
                                        Collectors.toMap(
                                                Chapter::getChapterKey, Function.identity()));
        for (DecisionRequest.Scope scope : request.scopes()) {
            if (scope.scopeType().equals("CHAPTER_RANGE")) {
                Chapter from = chapters.get(scope.fromChapterId());
                Chapter to =
                        scope.toChapterId() == null ? null : chapters.get(scope.toChapterId());
                if (from == null
                        || (scope.toChapterId() != null && to == null)
                        || (to != null && from.getChapterNo() > to.getChapterNo())
                        || scope.sceneSourceId() != null
                        || scope.startOffset() != null
                        || scope.endOffset() != null) {
                    invalid();
                }
                scopes.add(
                        new ReviewDecisionScope(
                                decision,
                                scope.scopeType(),
                                from,
                                to,
                                null,
                                null,
                                null,
                                scope.conditionText().strip()));
            } else {
                if (!candidate.getTargetSource().getId().equals(scope.sceneSourceId())
                        || scope.fromChapterId() != null
                        || scope.toChapterId() != null
                        || scope.startOffset() == null
                        || scope.endOffset() == null
                        || scope.startOffset() != candidate.getStartOffset()
                        || scope.endOffset() != candidate.getEndOffset()) {
                    invalid();
                }
                scopes.add(
                        new ReviewDecisionScope(
                                decision,
                                scope.scopeType(),
                                null,
                                null,
                                candidate.getTargetSource(),
                                scope.startOffset(),
                                scope.endOffset(),
                                scope.conditionText().strip()));
            }
        }
        // 수용·보류는 범위 미지정 시 해당 장면에만 남기고 미래 회차 전체의 예외가 되지 않게 한다.
        if (scopes.isEmpty() && !request.status().equals("미검토")) {
            scopes.add(
                    new ReviewDecisionScope(
                            decision,
                            "SCENE",
                            null,
                            null,
                            candidate.getTargetSource(),
                            candidate.getStartOffset(),
                            candidate.getEndOffset(),
                            "현재 검토 장면"));
        }
        return scopes;
    }

    /** 최신 판단과 현재 근거 지문을 반환하며 아직 미저장인 후보는 version=0이다. */
    @Transactional(readOnly = true)
    public DecisionResponse get(Long ownerId, Long workId, String reviewId) {
        ReviewCandidate candidate = owned(ownerId, workId, reviewId);
        return response(
                workId,
                candidate,
                decisionRepository
                        .findFirstByReview_IdOrderByDecisionVersionDesc(reviewId)
                        .orElse(null));
    }

    /** 판단 이력을 버전 내림차순으로 제한하고 변경된 근거는 각 버전에 표시한다. */
    @Transactional(readOnly = true)
    public OffsetPageResponse<DecisionResponse> history(
            Long ownerId, Long workId, String reviewId, OffsetPageRequest page) {
        owned(ownerId, workId, reviewId);
        return responses(
                workId,
                decisionRepository.findByReview_Id(
                        reviewId, page.toPageable(Sort.by("decisionVersion").descending())));
    }

    /** 작품의 최신 판단을 필터·페이지로 조회한다. 현재 회차 외의 판단도 다시 확인할 수 있다. */
    @Transactional(readOnly = true)
    public OffsetPageResponse<DecisionResponse> list(
            Long ownerId, Long workId, String chapterKey, String status, OffsetPageRequest page) {
        workAccessService.requireOwned(ownerId, workId);
        if (status != null && !List.of("미검토", "수용", "보류", "의도된 설정").contains(status)) {
            invalid();
        }
        return responses(
                workId,
                decisionRepository.findLatestForWork(
                        workId, chapterKey, status, page.toPageable(Sort.by("id").descending())));
    }

    /** 검토 후보 한 페이지의 최신 상태를 일괄 매핑한다. 호출자가 소유권을 먼저 검사한다. */
    @Transactional(readOnly = true)
    public Map<String, DecisionResponse> summaries(
            Long workId, List<ReviewCandidate> candidates, List<ReviewEvidence> evidences) {
        if (candidates.isEmpty()) {
            return Map.of();
        }
        List<ReviewDecision> decisions =
                decisionRepository.findLatestForReviews(
                        candidates.stream().map(ReviewCandidate::getId).toList());
        Map<String, ReviewDecision> latest =
                decisions.stream()
                        .collect(
                                Collectors.toMap(
                                        item -> item.getReview().getId(), Function.identity()));
        Map<Long, List<ReviewDecisionScope>> scopes = scopes(decisions);
        Map<String, DecisionBasis.State> basis =
                decisionBasis.inspect(workId, candidates, evidences);
        return candidates.stream()
                .collect(
                        Collectors.toMap(
                                ReviewCandidate::getId,
                                candidate -> {
                                    ReviewDecision decision = latest.get(candidate.getId());
                                    return decisionConverter.toResponse(
                                            candidate,
                                            decision,
                                            decision == null
                                                    ? List.of()
                                                    : scopes.getOrDefault(
                                                            decision.getId(), List.of()),
                                            basis.get(candidate.getId()));
                                }));
    }

    private OffsetPageResponse<DecisionResponse> responses(
            Long workId, Page<ReviewDecision> decisions) {
        List<ReviewCandidate> candidates =
                decisions.stream().map(ReviewDecision::getReview).distinct().toList();
        Map<String, DecisionBasis.State> basis =
                decisionBasis.inspect(
                        workId,
                        candidates,
                        candidates.isEmpty()
                                ? List.of()
                                : evidenceRepository.findForReviews(
                                        candidates.stream().map(ReviewCandidate::getId).toList()));
        Map<Long, List<ReviewDecisionScope>> scopes = scopes(decisions.getContent());
        return OffsetPageResponse.from(
                decisions.map(
                        decision ->
                                decisionConverter.toResponse(
                                        decision.getReview(),
                                        decision,
                                        scopes.getOrDefault(decision.getId(), List.of()),
                                        basis.get(decision.getReview().getId()))));
    }

    private Map<Long, List<ReviewDecisionScope>> scopes(List<ReviewDecision> decisions) {
        return decisions.isEmpty()
                ? Map.of()
                : scopeRepository
                        .findForDecisions(decisions.stream().map(ReviewDecision::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(scope -> scope.getDecision().getId()));
    }

    private DecisionResponse response(
            Long workId, ReviewCandidate candidate, ReviewDecision decision) {
        DecisionBasis.State basis =
                decisionBasis
                        .inspect(
                                workId,
                                List.of(candidate),
                                evidenceRepository.findForReviews(List.of(candidate.getId())))
                        .get(candidate.getId());
        return decisionConverter.toResponse(
                candidate,
                decision,
                decision == null
                        ? List.of()
                        : scopeRepository.findForDecisions(List.of(decision.getId())),
                basis);
    }

    private ReviewCandidate owned(Long ownerId, Long workId, String reviewId) {
        return candidateRepository
                .findOwned(reviewId, workId, ownerId)
                .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND));
    }

    private void invalid() {
        throw new CustomException(CommonErrorCode.INVALID_REQUEST);
    }
}
