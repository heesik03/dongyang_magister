package com.magister.backend.domain.analysis.converter;

import com.magister.backend.domain.analysis.dto.AnalysisInput;
import com.magister.backend.domain.analysis.dto.AnalysisInput.SettingReference;
import com.magister.backend.domain.analysis.dto.AnalysisJobResponse;
import com.magister.backend.domain.analysis.dto.AnalysisJobSummary;
import com.magister.backend.domain.analysis.dto.AnalysisOutput.Candidate;
import com.magister.backend.domain.analysis.dto.AnalysisPreviewResponse;
import com.magister.backend.domain.analysis.dto.AnalysisRequest;
import com.magister.backend.domain.analysis.dto.AnalysisSnapshotResponse;
import com.magister.backend.domain.analysis.dto.AnalysisSourceResponse;
import com.magister.backend.domain.analysis.entity.AnalysisJob;
import com.magister.backend.domain.analysis.entity.AnalysisSource;
import com.magister.backend.domain.analysis.repository.AnalysisJobSummaryView;
import com.magister.backend.domain.analysis.repository.AnalysisJobView;
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
import java.util.Optional;
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
                        : "분석 입력과 작업 결과는 작업 종료 후 "
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
                "이 화면에서는 저장 자료를 삭제할 수 없습니다. 작업 취소로 이미 전송한 자료를 회수하거나 공급자 자료를 삭제할 수는 없습니다. 공급자의 보관·이용"
                        + " 기준은 유료·무료 서비스와 계정 설정에 따라 다릅니다.",
                "https://ai.google.dev/gemini-api/terms");
    }

    public AnalysisSnapshotResponse toSnapshot(
            AnalysisJob job, AnalysisInput input, List<AnalysisSource> sources, boolean stale) {
        return new AnalysisSnapshotResponse(
                job.getId(),
                input,
                sources.stream()
                        .map(
                                source ->
                                        new AnalysisSourceResponse(
                                                source.getId(),
                                                source.getType(),
                                                source.getSourceKey(),
                                                source.getRevision(),
                                                source.getTitle(),
                                                source.getContent(),
                                                source.getMetadata(),
                                                source.getContentHash()))
                        .toList(),
                job.getSourceFormatVersion() == 0,
                stale);
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
            AnalysisRequest request) {
        return new AnalysisInput(
                chapter,
                settings,
                previous,
                decisions,
                request.consistency(),
                request.motivation());
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

    public ReviewData toReview(
            Candidate candidate, SettingReference setting, Optional<ReviewData> previous) {
        return new ReviewData(
                UUID.randomUUID().toString(),
                candidate.type(),
                candidate.quote(),
                setting.id(),
                setting.content(),
                candidate.reason(),
                candidate.suggestion(),
                previous.map(ReviewData::decision).orElse("미검토"),
                previous.map(ReviewData::note).orElse(""));
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
}
