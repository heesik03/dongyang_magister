package com.magister.backend.domain.analysis.converter;

import com.magister.backend.domain.analysis.dto.AnalysisInput;
import com.magister.backend.domain.analysis.dto.AnalysisInput.SettingReference;
import com.magister.backend.domain.analysis.dto.AnalysisJobResponse;
import com.magister.backend.domain.analysis.dto.AnalysisJobSummary;
import com.magister.backend.domain.analysis.dto.AnalysisOutput.Candidate;
import com.magister.backend.domain.analysis.dto.AnalysisOutput.Evidence;
import com.magister.backend.domain.analysis.dto.AnalysisPreviewResponse;
import com.magister.backend.domain.analysis.dto.AnalysisRequest;
import com.magister.backend.domain.analysis.dto.AnalysisSnapshotResponse;
import com.magister.backend.domain.analysis.dto.AnalysisSourceResponse;
import com.magister.backend.domain.analysis.dto.ReviewComparisonResponse;
import com.magister.backend.domain.analysis.dto.ReviewComparisonResponse.ComparedCandidate;
import com.magister.backend.domain.analysis.dto.WorkReviewOverviewResponse;
import com.magister.backend.domain.analysis.dto.WorkReviewOverviewResponse.ChapterReviewSummary;
import com.magister.backend.domain.analysis.entity.AnalysisJob;
import com.magister.backend.domain.analysis.entity.AnalysisSource;
import com.magister.backend.domain.analysis.entity.ReviewCandidate;
import com.magister.backend.domain.analysis.repository.AnalysisJobSummaryView;
import com.magister.backend.domain.analysis.repository.AnalysisJobView;
import com.magister.backend.domain.chapter.entity.Chapter;
import com.magister.backend.domain.decision.dto.DecisionReference;
import com.magister.backend.domain.setting.entity.WorkSetting;
import com.magister.backend.domain.workspace.dto.WorkspaceData.AnalysisData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ChapterData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ReviewData;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AnalysisConverter {
    private final ObjectMapper objectMapper;

    public AnalysisPreviewResponse toPreview(
            String token,
            Instant expiresAt,
            AnalysisInput input,
            String policyVersion,
            String model,
            Duration retention) {
        String notice =
                retention.isZero()
                        ? "분석 입력과 결과는 서버에 저장되며 현재 자동 삭제되지 않습니다."
                        : "판단 이력이 없는 분석 입력과 작업 결과는 작업 종료 후 "
                                + retention.toSeconds()
                                + "초가 지나면 자동 삭제 대상이 됩니다. 별도로 저장한 원고와 판단은 유지됩니다.";
        return new AnalysisPreviewResponse(
                token,
                expiresAt,
                input,
                policyVersion,
                "Google Gemini API",
                model,
                notice,
                "작품 화면의 작품 삭제에서 원고·설정·분석 원본·검토·판단 이력·타임라인을 영구 삭제할 수 있습니다. 작업 취소·작품 삭제로 이미 전송한 자료를"
                    + " 회수하거나 공급자 자료를 삭제할 수는 없습니다. 공급자의 보관·이용 기준은 유료·무료 서비스와 계정 설정에 따라 다릅니다.",
                "https://ai.google.dev/gemini-api/terms");
    }

    public AnalysisSnapshotResponse toSnapshot(
            AnalysisJob job, AnalysisInput input, List<AnalysisSource> sources, boolean stale) {
        return new AnalysisSnapshotResponse(
                job.getId(),
                input,
                sources.stream().map(this::toSource).toList(),
                job.getSourceFormatVersion() == 0,
                stale);
    }

    public AnalysisSourceResponse toSource(AnalysisSource source) {
        return new AnalysisSourceResponse(
                source.getId(),
                source.getType(),
                source.getSourceKey(),
                source.getRevision(),
                source.getTitle(),
                source.getContent(),
                source.getMetadata(),
                source.getContentHash());
    }

    public AnalysisJobSummary toSummary(AnalysisJobSummaryView job) {
        return new AnalysisJobSummary(
                job.getId(),
                job.getChapterKey(),
                job.getChapterTitle(),
                job.getStatus().name(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getPolicyVersion() != null);
    }

    public AnalysisJobResponse toResponse(AnalysisJob job) {
        return new AnalysisJobResponse(
                job.getId(), job.getStatus().name(), job.getError(), readResult(job.getResult()));
    }

    public AnalysisJobResponse toResponse(AnalysisJobView job) {
        return new AnalysisJobResponse(
                job.getId(), job.getStatus().name(), job.getError(), readResult(job.getResult()));
    }

    private AnalysisData readResult(String result) {
        return result == null ? null : objectMapper.readValue(result, AnalysisData.class);
    }

    public static AnalysisInput toInput(
            ChapterData chapter,
            List<SettingReference> settings,
            List<ChapterData> previous,
            List<ReviewData> decisions,
            AnalysisRequest request,
            List<DecisionReference> decisionReferences) {
        return new AnalysisInput(
                chapter,
                settings,
                previous,
                decisions,
                request.consistency(),
                request.motivation(),
                decisionReferences);
    }

    public static SettingReference toReference(WorkSetting setting) {
        String kind =
                switch (setting.getKind()) {
                    case CHARACTER -> "인물";
                    case WORLD -> "세계관";
                    case MAJOR_EVENT -> "주요 사건";
                    case INTENDED_ADAPTATION -> "의도한 각색";
                };
        return new SettingReference(
                setting.getId().toString(),
                setting.getTitle(),
                setting.getContent(),
                kind,
                "확정",
                setting.getVersion());
    }

    public ReviewData toReview(Candidate candidate) {
        Evidence evidence = candidate.evidences().getFirst();
        return new ReviewData(
                UUID.randomUUID().toString(),
                candidate.type(),
                candidate.quote(),
                evidence.sourceType().equals("SETTING")
                        ? evidence.sourceKey()
                        : "previous:" + evidence.sourceKey(),
                evidence.quote(),
                candidate.reason(),
                candidate.suggestion(),
                "미검토",
                "");
    }

    public AnalysisData toResult(AnalysisInput input, List<ReviewData> reviews) {
        return new AnalysisData(
                input.chapter().id(),
                input.chapter().revision(),
                input.chapter().content(),
                input.settings().stream().map(SettingReference::id).toList(),
                input.previous().stream().map(ChapterData::id).toList(),
                reviews,
                Instant.now().toString(),
                objectMapper.writeValueAsString(input.settings()));
    }

    public ChapterReviewSummary toUnanalyzedChapterSummary(Chapter chapter) {
        return new ChapterReviewSummary(
                chapter.getChapterKey(),
                chapter.getChapterNo(),
                chapter.getTitle(),
                chapter.getRevision(),
                "UNANALYZED",
                null,
                null,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0);
    }

    public ChapterReviewSummary toAnalyzedChapterSummary(
            Chapter chapter,
            String analysisStatus,
            AnalysisJobSummaryView latestJob,
            Long analyzedRevision,
            int settingCount,
            int previousChapterCount,
            int analysisRunCount,
            int totalCandidateCount,
            int conflictCount,
            int supplementCount,
            int unreviewedCount,
            int acceptedCount,
            int holdCount,
            int intendedCount) {
        return new ChapterReviewSummary(
                chapter.getChapterKey(),
                chapter.getChapterNo(),
                chapter.getTitle(),
                chapter.getRevision(),
                analysisStatus,
                latestJob.getId(),
                analyzedRevision,
                latestJob.getUpdatedAt(),
                settingCount,
                previousChapterCount,
                analysisRunCount,
                totalCandidateCount,
                conflictCount,
                supplementCount,
                unreviewedCount,
                acceptedCount,
                holdCount,
                intendedCount);
    }

    public WorkReviewOverviewResponse toWorkReviewOverview(
            Long workId,
            int totalChapters,
            int analyzedChapters,
            int currentRevisionChapters,
            int outdatedRevisionChapters,
            int unanalyzedChapters,
            int totalConflictCount,
            int totalSupplementCount,
            int totalUnreviewedCount,
            List<ChapterReviewSummary> chapters) {
        return new WorkReviewOverviewResponse(
                workId,
                totalChapters,
                analyzedChapters,
                currentRevisionChapters,
                outdatedRevisionChapters,
                unanalyzedChapters,
                totalConflictCount,
                totalSupplementCount,
                totalUnreviewedCount,
                "각 회차의 가장 최근에 완료(SUCCEEDED)된 분석 1건만 집계하며, 동일 회차의 이전 재분석 이력은 중복 합산하지 않습니다.",
                chapters);
    }

    public ReviewComparisonResponse toEmptyComparison(
            AnalysisJob currentJob, long currentRevision) {
        return new ReviewComparisonResponse(
                currentJob.getId(),
                null,
                false,
                currentJob.getChapterKey(),
                currentJob.getChapterTitle(),
                currentRevision,
                null,
                currentJob.getUpdatedAt(),
                null,
                false,
                false,
                "같은 회차의 이전 완료 분석 이력이 없어 비교 대상을 찾지 못했습니다.",
                "이번 분석 결과에서 다시 나오지 않은 후보는 자동 해결로 간주되지 않습니다.",
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    public ComparedCandidate toComparedCandidate(
            String category,
            ReviewCandidate primary,
            ReviewCandidate matchedPrevious,
            List<String> sourceTitles,
            String comparisonNote) {
        return new ComparedCandidate(
                category,
                primary.getId(),
                matchedPrevious == null ? null : matchedPrevious.getId(),
                primary.getType(),
                primary.getQuote(),
                primary.getStartOffset(),
                primary.getEndOffset(),
                matchedPrevious == null ? null : matchedPrevious.getStartOffset(),
                matchedPrevious == null ? null : matchedPrevious.getEndOffset(),
                primary.getReason(),
                primary.getSuggestion(),
                sourceTitles,
                comparisonNote);
    }

    public ReviewComparisonResponse toComparisonResponse(
            AnalysisJob currentJob,
            AnalysisJob baseJob,
            long currentRevision,
            Long baseRevision,
            boolean scopeChanged,
            boolean manuscriptModified,
            List<ComparedCandidate> retained,
            List<ComparedCandidate> added,
            List<ComparedCandidate> notReappeared,
            List<ComparedCandidate> incomparable) {
        String scopeNote =
                scopeChanged
                        ? "이전 분석과 이번 분석 사이에 선택된 설정·이전 회차 또는 검토 유형이 변경되었습니다. 범위 변경에 따른 항목은 '비교 불가'로 분리했습니다."
                        : "이전 분석과 동일한 설정·이전 회차·검토 유형 범위에서 비교했습니다.";
        return new ReviewComparisonResponse(
                currentJob.getId(),
                baseJob.getId(),
                true,
                currentJob.getChapterKey(),
                currentJob.getChapterTitle(),
                currentRevision,
                baseRevision,
                currentJob.getUpdatedAt(),
                baseJob.getUpdatedAt(),
                scopeChanged,
                manuscriptModified,
                scopeNote,
                "이번 결과에서 다시 나오지 않은 후보는 자동 해결로 간주하지 않습니다. 실제 원고 수정 내용과 설정 근거를 직접 확인해 주세요.",
                retained,
                added,
                notReappeared,
                incomparable);
    }
}
