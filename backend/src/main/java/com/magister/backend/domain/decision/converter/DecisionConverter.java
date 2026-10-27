package com.magister.backend.domain.decision.converter;

import com.magister.backend.domain.analysis.entity.ReviewCandidate;
import com.magister.backend.domain.analysis.entity.ReviewEvidence;
import com.magister.backend.domain.decision.dto.DecisionReference;
import com.magister.backend.domain.decision.dto.DecisionResponse;
import com.magister.backend.domain.decision.entity.ReviewDecision;
import com.magister.backend.domain.decision.entity.ReviewDecisionScope;
import com.magister.backend.domain.decision.service.DecisionBasis.State;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DecisionConverter {
    public DecisionResponse toResponse(
            ReviewCandidate review,
            ReviewDecision decision,
            List<ReviewDecisionScope> scopes,
            State basis) {
        return new DecisionResponse(
                decision == null ? null : decision.getId(),
                review.getId(),
                review.getQuote(),
                decision == null ? 0 : decision.getDecisionVersion(),
                decision == null ? "미검토" : decision.getStatus(),
                decision == null ? "" : decision.getReason(),
                basis.hash(),
                decision == null
                        ? basis.originalChanged()
                        : !decision.getBasisHash().equals(basis.hash()),
                basis.quotesMatch(),
                decision == null ? null : decision.getCreatedAt(),
                scopes.stream().map(this::toScope).toList());
    }

    private DecisionResponse.Scope toScope(ReviewDecisionScope scope) {
        return new DecisionResponse.Scope(
                scope.getId(),
                scope.getScopeType(),
                scope.getFromChapter() == null ? null : scope.getFromChapter().getChapterKey(),
                scope.getToChapter() == null ? null : scope.getToChapter().getChapterKey(),
                scope.getSceneSource() == null ? null : scope.getSceneSource().getId(),
                scope.getStartOffset(),
                scope.getEndOffset(),
                scope.getConditionText(),
                scope.getFromChapter() == null ? null : scope.getFromChapter().getTitle(),
                scope.getToChapter() == null ? null : scope.getToChapter().getTitle());
    }

    public DecisionReference toReference(
            ReviewDecision decision, ReviewDecisionScope scope, List<ReviewEvidence> evidences) {
        DecisionResponse.Scope range = toScope(scope);
        return new DecisionReference(
                scope.getId(),
                decision.getId(),
                decision.getReview().getId(),
                decision.getDecisionVersion(),
                decision.getStatus(),
                decision.getReason(),
                decision.getBasisHash(),
                decision.getReview().getQuote(),
                range.scopeType(),
                range.fromChapterId(),
                range.toChapterId(),
                range.sceneSourceId(),
                range.startOffset(),
                range.endOffset(),
                range.conditionText(),
                evidences.stream()
                        .map(
                                evidence ->
                                        evidence.getSource().getTitle()
                                                + " · "
                                                + evidence.getQuote())
                        .toList(),
                range.fromChapterTitle(),
                range.toChapterTitle());
    }
}
