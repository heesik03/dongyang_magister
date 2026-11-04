package com.magister.backend.domain.analysis.service;

import com.magister.backend.domain.analysis.converter.AnalysisConverter;
import com.magister.backend.domain.analysis.dto.WorkReviewOverviewResponse;
import com.magister.backend.domain.analysis.dto.WorkReviewOverviewResponse.ChapterReviewSummary;
import com.magister.backend.domain.analysis.entity.AnalysisJobStatus;
import com.magister.backend.domain.analysis.entity.AnalysisSource;
import com.magister.backend.domain.analysis.entity.ReviewCandidate;
import com.magister.backend.domain.analysis.repository.AnalysisJobRepository;
import com.magister.backend.domain.analysis.repository.AnalysisJobSummaryView;
import com.magister.backend.domain.analysis.repository.AnalysisSourceRepository;
import com.magister.backend.domain.analysis.repository.ReviewCandidateRepository;
import com.magister.backend.domain.chapter.entity.Chapter;
import com.magister.backend.domain.chapter.repository.ChapterRepository;
import com.magister.backend.domain.decision.entity.ReviewDecision;
import com.magister.backend.domain.decision.repository.ReviewDecisionRepository;
import com.magister.backend.domain.work.service.WorkAccessService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkReviewOverviewService {
    private final WorkAccessService workAccessService;
    private final ChapterRepository chapterRepository;
    private final AnalysisJobRepository jobRepository;
    private final AnalysisSourceRepository sourceRepository;
    private final ReviewCandidateRepository candidateRepository;
    private final ReviewDecisionRepository decisionRepository;
    private final AnalysisConverter analysisConverter;

    /**
     * 작품의 회차별 최신 성공(SUCCEEDED) 분석 1건만 기준으로 검토 현황을 집계한다. 미분석 회차·최신 원고 버전 분석·수정 전 원고 버전 분석을 구분하고, 동일
     * 회차의 과거 재분석 이력은 중복 합산하지 않는다.
     */
    @Transactional(readOnly = true)
    public WorkReviewOverviewResponse getOverview(Long ownerId, Long workId) {
        workAccessService.requireOwned(ownerId, workId);
        List<Chapter> chapters = chapterRepository.findByWork_IdOrderByChapterNoAsc(workId);
        List<AnalysisJobSummaryView> succeededJobs =
                jobRepository.findAllByStatusOrderByLatest(
                        workId, ownerId, AnalysisJobStatus.SUCCEEDED);

        Map<String, List<AnalysisJobSummaryView>> jobsByChapterKey = new HashMap<>();
        for (AnalysisJobSummaryView job : succeededJobs) {
            if (job.getChapterKey() != null) {
                jobsByChapterKey
                        .computeIfAbsent(job.getChapterKey(), ignored -> new ArrayList<>())
                        .add(job);
            }
        }

        List<ChapterReviewSummary> chapterSummaries = new ArrayList<>();
        int analyzedChapters = 0;
        int currentRevisionChapters = 0;
        int outdatedRevisionChapters = 0;
        int unanalyzedChapters = 0;
        int totalConflictCount = 0;
        int totalSupplementCount = 0;
        int totalUnreviewedCount = 0;

        for (Chapter chapter : chapters) {
            List<AnalysisJobSummaryView> chapterJobs =
                    jobsByChapterKey.getOrDefault(chapter.getChapterKey(), List.of());
            if (chapterJobs.isEmpty()) {
                unanalyzedChapters++;
                chapterSummaries.add(
                        analysisConverter.toUnanalyzedChapterSummary(chapter));
                continue;
            }

            analyzedChapters++;
            AnalysisJobSummaryView latestJob = chapterJobs.get(0);
            List<AnalysisSource> sources =
                    sourceRepository.findByJob_IdOrderByIdAsc(latestJob.getId());
            Long analyzedRevision = null;
            int settingCount = 0;
            int previousChapterCount = 0;
            for (AnalysisSource source : sources) {
                if (source.getType().equals("TARGET")) {
                    analyzedRevision = source.getRevision();
                } else if (source.getType().equals("SETTING")) {
                    settingCount++;
                } else if (source.getType().equals("PREVIOUS")) {
                    previousChapterCount++;
                }
            }

            boolean isCurrentRevision =
                    analyzedRevision != null && analyzedRevision == chapter.getRevision();
            String analysisStatus =
                    isCurrentRevision ? "CURRENT_REVISION" : "OUTDATED_REVISION";
            if (isCurrentRevision) {
                currentRevisionChapters++;
            } else {
                outdatedRevisionChapters++;
            }

            List<ReviewCandidate> candidates =
                    candidateRepository.findAllByJob_IdOrderByIdAsc(latestJob.getId());
            Map<String, String> decisionStatusByReviewId = new HashMap<>();
            if (!candidates.isEmpty()) {
                List<String> reviewIds =
                        candidates.stream().map(ReviewCandidate::getId).toList();
                for (ReviewDecision decision :
                        decisionRepository.findLatestForReviews(reviewIds)) {
                    decisionStatusByReviewId.put(
                            decision.getReview().getId(), decision.getStatus());
                }
            }

            int conflictCount = 0;
            int supplementCount = 0;
            int unreviewedCount = 0;
            int acceptedCount = 0;
            int holdCount = 0;
            int intendedCount = 0;

            for (ReviewCandidate candidate : candidates) {
                if (candidate.getType().equals("설정 충돌 후보")) {
                    conflictCount++;
                } else {
                    supplementCount++;
                }
                String status =
                        decisionStatusByReviewId.getOrDefault(candidate.getId(), "미검토");
                switch (status) {
                    case "수용" -> acceptedCount++;
                    case "보류" -> holdCount++;
                    case "의도된 설정" -> intendedCount++;
                    default -> unreviewedCount++;
                }
            }

            totalConflictCount += conflictCount;
            totalSupplementCount += supplementCount;
            totalUnreviewedCount += unreviewedCount;

            chapterSummaries.add(
                    analysisConverter.toAnalyzedChapterSummary(
                            chapter,
                            analysisStatus,
                            latestJob,
                            analyzedRevision,
                            settingCount,
                            previousChapterCount,
                            chapterJobs.size(),
                            candidates.size(),
                            conflictCount,
                            supplementCount,
                            unreviewedCount,
                            acceptedCount,
                            holdCount,
                            intendedCount));
        }

        return analysisConverter.toWorkReviewOverview(
                workId,
                chapters.size(),
                analyzedChapters,
                currentRevisionChapters,
                outdatedRevisionChapters,
                unanalyzedChapters,
                totalConflictCount,
                totalSupplementCount,
                totalUnreviewedCount,
                chapterSummaries);
    }
}
