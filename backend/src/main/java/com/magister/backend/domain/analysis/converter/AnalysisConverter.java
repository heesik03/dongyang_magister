package com.magister.backend.domain.analysis.converter;

import com.magister.backend.domain.analysis.dto.AnalysisInput;
import com.magister.backend.domain.analysis.dto.AnalysisInput.SettingReference;
import com.magister.backend.domain.analysis.dto.AnalysisJobResponse;
import com.magister.backend.domain.analysis.dto.AnalysisJobSummary;
import com.magister.backend.domain.analysis.dto.AnalysisOutput.Candidate;
import com.magister.backend.domain.analysis.dto.AnalysisOutput.Evidence;
import com.magister.backend.domain.analysis.dto.AnalysisRequest;
import com.magister.backend.domain.analysis.entity.AnalysisJob;
import com.magister.backend.domain.analysis.repository.AnalysisJobSummaryView;
import com.magister.backend.domain.analysis.repository.AnalysisJobView;
import com.magister.backend.domain.decision.dto.DecisionReference;
import com.magister.backend.domain.setting.entity.WorkSetting;
import com.magister.backend.domain.workspace.dto.WorkspaceData.AnalysisData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ChapterData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ReviewData;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AnalysisConverter {
    private final ObjectMapper objectMapper;

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
}
