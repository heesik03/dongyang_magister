package com.magister.backend.domain.analysis.converter;

import com.magister.backend.domain.analysis.dto.AnalysisInput;
import com.magister.backend.domain.analysis.dto.AnalysisInput.SettingReference;
import com.magister.backend.domain.analysis.dto.AnalysisJobResponse;
import com.magister.backend.domain.analysis.dto.AnalysisOutput.Candidate;
import com.magister.backend.domain.analysis.dto.AnalysisRequest;
import com.magister.backend.domain.analysis.entity.AnalysisJob;
import com.magister.backend.domain.analysis.repository.AnalysisJobView;
import com.magister.backend.domain.setting.entity.WorkSetting;
import com.magister.backend.domain.workspace.dto.WorkspaceData.AnalysisData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ChapterData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ReviewData;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AnalysisConverter {
    private final ObjectMapper objectMapper;

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
