package com.magister.backend.domain.analysis.service;

import com.magister.backend.domain.analysis.converter.ReviewConverter;
import com.magister.backend.domain.analysis.dto.AnalysisOutput.Candidate;
import com.magister.backend.domain.analysis.dto.AnalysisOutput.Evidence;
import com.magister.backend.domain.analysis.dto.ReviewReportResponse;
import com.magister.backend.domain.analysis.dto.ValidatedAnalysis;
import com.magister.backend.domain.analysis.entity.AnalysisJob;
import com.magister.backend.domain.analysis.entity.AnalysisSource;
import com.magister.backend.domain.analysis.entity.ReviewCandidate;
import com.magister.backend.domain.analysis.entity.ReviewEvidence;
import com.magister.backend.domain.analysis.repository.AnalysisJobRepository;
import com.magister.backend.domain.analysis.repository.AnalysisSourceRepository;
import com.magister.backend.domain.analysis.repository.ReviewCandidateRepository;
import com.magister.backend.domain.analysis.repository.ReviewEvidenceRepository;
import com.magister.backend.domain.decision.service.DecisionService;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.pagination.OffsetPageRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewResultService {
    private final AnalysisJobRepository jobRepository;
    private final AnalysisSourceRepository sourceRepository;
    private final ReviewCandidateRepository candidateRepository;
    private final ReviewEvidenceRepository evidenceRepository;
    private final AnalysisSnapshotService snapshotService;
    private final ReviewConverter reviewConverter;
    private final DecisionService decisionService;

    /** 완료 상태와 같은 트랜잭션에서만 저장한다. 출처·후보는 같은 작업의 FK로도 묶는다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(AnalysisJob job, ValidatedAnalysis validated) {
        Map<String, AnalysisSource> sources =
                sourceRepository.findByJob_IdOrderByIdAsc(job.getId()).stream()
                        .collect(
                                Collectors.toMap(
                                        source -> source.getType() + ":" + source.getSourceKey(),
                                        Function.identity()));
        AnalysisSource target =
                sources.values().stream()
                        .filter(source -> source.getType().equals("TARGET"))
                        .findFirst()
                        .orElseThrow();
        for (int index = 0; index < validated.candidates().size(); index++) {
            Candidate candidate = validated.candidates().get(index);
            ReviewCandidate saved =
                    candidateRepository.save(
                            new ReviewCandidate(
                                    validated.result().reviews().get(index).id(),
                                    job,
                                    target,
                                    candidate.type(),
                                    candidate.quote(),
                                    candidate.startOffset(),
                                    candidate.endOffset(),
                                    candidate.reason(),
                                    candidate.suggestion()));
            for (Evidence evidence : candidate.evidences()) {
                AnalysisSource source =
                        sources.get(evidence.sourceType() + ":" + evidence.sourceKey());
                if (source == null) {
                    throw new IllegalStateException("Validated evidence source missing");
                }
                evidenceRepository.save(
                        new ReviewEvidence(
                                saved,
                                source,
                                evidence.quote(),
                                evidence.startOffset(),
                                evidence.endOffset(),
                                evidence.explanation()));
            }
        }
        job.recordEvidenceResult();
    }

    /** 본인 작품의 후보·다중 근거를 한 페이지씩 조회하며 과거 형식과 변경 여부를 명시한다. */
    @Transactional(readOnly = true)
    public ReviewReportResponse list(
            Long ownerId, Long workId, String jobId, OffsetPageRequest page) {
        AnalysisJob job =
                jobRepository
                        .findOwned(jobId, workId, ownerId)
                        .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND));
        Page<ReviewCandidate> candidates =
                candidateRepository.findByJob_Id(jobId, page.toPageable(Sort.by("id")));
        List<ReviewEvidence> evidences =
                candidates.isEmpty()
                        ? List.of()
                        : evidenceRepository.findForReviews(
                                candidates.stream().map(ReviewCandidate::getId).toList());
        return reviewConverter.toReport(
                candidates,
                evidences,
                job.getResultFormatVersion() == 0,
                snapshotService.get(ownerId, workId, jobId).stale(),
                decisionService.summaries(workId, candidates.getContent(), evidences));
    }
}
