package com.magister.backend.domain.analysis.service;

import com.magister.backend.domain.analysis.converter.AnalysisConverter;
import com.magister.backend.domain.analysis.dto.WorkReviewOverviewResponse;
import com.magister.backend.domain.analysis.dto.WorkReviewOverviewResponse.ChapterReviewSummary;
import com.magister.backend.domain.analysis.repository.WorkReviewOverviewRepository;
import com.magister.backend.domain.work.service.WorkAccessService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkReviewOverviewService {
    private final WorkAccessService workAccessService;
    private final WorkReviewOverviewRepository workReviewOverviewRepository;
    private final AnalysisConverter analysisConverter;

    /** 본인 작품의 회차별 최신 완료 분석과 현재 원고 버전을 비교하여 검토 현황을 집계한다. */
    @Transactional(readOnly = true)
    public WorkReviewOverviewResponse getOverview(Long ownerId, Long workId) {
        workAccessService.requireOwned(ownerId, workId);
        List<ChapterReviewSummary> chapters = workReviewOverviewRepository.findChapterCounts(workId)
                .stream().map(analysisConverter::toChapterReviewSummary).toList();
        return analysisConverter.toWorkReviewOverview(workId, chapters);
    }
}
