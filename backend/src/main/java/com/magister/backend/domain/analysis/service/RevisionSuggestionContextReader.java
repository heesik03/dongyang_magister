package com.magister.backend.domain.analysis.service;

import com.magister.backend.domain.analysis.converter.RevisionSuggestionConverter;
import com.magister.backend.domain.analysis.dto.RevisionSuggestion;
import com.magister.backend.domain.analysis.entity.ReviewCandidate;
import com.magister.backend.domain.analysis.error.AnalysisErrorCode;
import com.magister.backend.domain.analysis.repository.ReviewCandidateRepository;
import com.magister.backend.domain.analysis.repository.ReviewEvidenceRepository;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.global.error.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RevisionSuggestionContextReader {
    private final ReviewCandidateRepository candidates;
    private final ReviewEvidenceRepository evidences;
    private final AnalysisSnapshotService snapshots;
    private final RevisionSuggestionConverter converter;

    /** 소유자의 최신 검토 근거만 읽고 외부 전송 전에 조회 트랜잭션을 끝낸다. */
    @Transactional(readOnly = true)
    public RevisionSuggestion.Context read(Long ownerId, Long workId, String reviewId) {
        ReviewCandidate candidate = candidates.findOwned(reviewId, workId, ownerId)
                .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND));
        if (snapshots.isStale(workId, candidate.getJob().getInput())) {
            throw new CustomException(AnalysisErrorCode.PREVIEW_CHANGED);
        }
        return converter.toContext(candidate, evidences.findForReviews(List.of(reviewId)));
    }
}
