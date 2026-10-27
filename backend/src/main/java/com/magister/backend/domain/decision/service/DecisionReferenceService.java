package com.magister.backend.domain.decision.service;

import com.magister.backend.domain.analysis.entity.AnalysisJob;
import com.magister.backend.domain.analysis.entity.ReviewCandidate;
import com.magister.backend.domain.analysis.entity.ReviewEvidence;
import com.magister.backend.domain.analysis.repository.ReviewEvidenceRepository;
import com.magister.backend.domain.chapter.entity.Chapter;
import com.magister.backend.domain.chapter.repository.ChapterRepository;
import com.magister.backend.domain.decision.converter.DecisionConverter;
import com.magister.backend.domain.decision.dto.DecisionReference;
import com.magister.backend.domain.decision.entity.ReviewDecision;
import com.magister.backend.domain.decision.entity.ReviewDecisionScope;
import com.magister.backend.domain.decision.repository.AnalysisDecisionReferenceRepository;
import com.magister.backend.domain.decision.repository.ReviewDecisionRepository;
import com.magister.backend.domain.decision.repository.ReviewDecisionScopeRepository;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DecisionReferenceService {
    private final ReviewDecisionRepository decisionRepository;
    private final ReviewDecisionScopeRepository scopeRepository;
    private final ReviewEvidenceRepository evidenceRepository;
    private final ChapterRepository chapterRepository;
    private final DecisionBasis decisionBasis;
    private final DecisionConverter decisionConverter;
    private final AnalysisDecisionReferenceRepository referenceRepository;

    /** 작품 잠금 아래 최신 판단을 읽는다. 범위 밖·철회·근거 변경·무관한 판단은 전송하지 않는다. */
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<DecisionReference> select(
            Long workId, String chapterKey, List<String> settingKeys, List<String> previousKeys) {
        Chapter chapter =
                chapterRepository.findByWork_IdAndChapterKey(workId, chapterKey).orElseThrow();
        List<ReviewDecision> decisions =
                decisionRepository.findApplicable(
                        workId,
                        chapter.getId(),
                        chapter.getChapterNo(),
                        settingKeys.isEmpty() ? List.of("") : settingKeys,
                        previousKeys.isEmpty() ? List.of("") : previousKeys,
                        PageRequest.of(0, 101));
        if (decisions.size() > 100) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
        if (decisions.isEmpty()) {
            return List.of();
        }
        List<ReviewCandidate> candidates =
                decisions.stream().map(ReviewDecision::getReview).toList();
        List<ReviewEvidence> evidences =
                evidenceRepository.findForReviews(
                        candidates.stream().map(ReviewCandidate::getId).toList());
        Map<String, List<ReviewEvidence>> byReview =
                evidences.stream()
                        .collect(Collectors.groupingBy(evidence -> evidence.getReview().getId()));
        Map<String, DecisionBasis.State> basis =
                decisionBasis.inspect(workId, candidates, evidences);
        Map<Long, ReviewDecision> byId =
                decisions.stream()
                        .collect(Collectors.toMap(ReviewDecision::getId, Function.identity()));
        List<DecisionReference> result = new ArrayList<>();
        for (ReviewDecisionScope scope :
                scopeRepository.findForDecisions(
                        decisions.stream().map(ReviewDecision::getId).toList())) {
            ReviewDecision decision = byId.get(scope.getDecision().getId());
            List<ReviewEvidence> relatedEvidences =
                    byReview.getOrDefault(decision.getReview().getId(), List.of());
            // 판단을 경유해 선택하지 않은 설정·이전 원고의 인용문을 몰래 전송하지 않는다.
            if (relatedEvidences.isEmpty()
                    || relatedEvidences.stream()
                            .anyMatch(
                                    evidence ->
                                            evidence.getSource().getType().equals("SETTING")
                                                    ? !settingKeys.contains(
                                                            evidence.getSource().getSourceKey())
                                                    : !previousKeys.contains(
                                                            evidence.getSource().getSourceKey()))) {
                continue;
            }
            DecisionBasis.State current = basis.get(decision.getReview().getId());
            if (!current.quotesMatch() || !decision.getBasisHash().equals(current.hash())) {
                continue;
            }
            boolean matches =
                    scope.getScopeType().equals("CHAPTER_RANGE")
                            ? scope.getFromChapter().getChapterNo() <= chapter.getChapterNo()
                                    && (scope.getToChapter() == null
                                            || scope.getToChapter().getChapterNo()
                                                    >= chapter.getChapterNo())
                            : scope.getSceneSource().getChapter().getId().equals(chapter.getId())
                                    && scope.getEndOffset() <= chapter.getContent().length()
                                    && chapter.getContent()
                                            .substring(scope.getStartOffset(), scope.getEndOffset())
                                            .equals(decision.getReview().getQuote());
            if (matches) {
                result.add(
                        decisionConverter.toReference(
                                decision,
                                scope,
                                byReview.getOrDefault(decision.getReview().getId(), List.of())));
            }
        }
        if (result.size() > 100) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
        return List.copyOf(result);
    }

    /** 접수 트랜잭션에서 실제 전송한 판단 범위의 FK를 고정한다. 이후 판단 변경으로 이 행을 바꾸지 않는다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(AnalysisJob job, List<DecisionReference> references) {
        referenceRepository.save(
                job.getId(), references.stream().map(DecisionReference::scopeId).toList());
    }

    /** 과거 입력에 포함된 판단이 수정·철회되거나 근거가 바뀌었는지 확인한다. */
    @Transactional(readOnly = true)
    public boolean changed(Long workId, List<DecisionReference> references) {
        if (references.isEmpty()) {
            return false;
        }
        List<ReviewDecision> latest =
                decisionRepository.findLatestForReviews(
                        references.stream().map(DecisionReference::reviewId).distinct().toList());
        Map<String, ReviewDecision> byReview =
                latest.stream()
                        .collect(
                                Collectors.toMap(
                                        item -> item.getReview().getId(), Function.identity()));
        List<ReviewCandidate> candidates = latest.stream().map(ReviewDecision::getReview).toList();
        Map<String, DecisionBasis.State> basis =
                decisionBasis.inspect(
                        workId,
                        candidates,
                        candidates.isEmpty()
                                ? List.of()
                                : evidenceRepository.findForReviews(
                                        candidates.stream().map(ReviewCandidate::getId).toList()));
        return references.stream()
                .anyMatch(
                        reference -> {
                            ReviewDecision decision = byReview.get(reference.reviewId());
                            return decision == null
                                    || decision.getDecisionVersion() != reference.version()
                                    || decision.getStatus().equals("미검토")
                                    || !reference
                                            .basisHash()
                                            .equals(basis.get(reference.reviewId()).hash());
                        });
    }
}
