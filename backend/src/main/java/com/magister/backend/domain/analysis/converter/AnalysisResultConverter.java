package com.magister.backend.domain.analysis.converter;

import com.magister.backend.domain.analysis.dto.AnalysisInput;
import com.magister.backend.domain.analysis.dto.AnalysisInput.SettingReference;
import com.magister.backend.domain.analysis.dto.AnalysisOutput.Candidate;
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
public class AnalysisResultConverter {
    private final ObjectMapper objectMapper;

    public ReviewData toReview(Candidate candidate, SettingReference setting, Optional<ReviewData> previous) {
        return new ReviewData(UUID.randomUUID().toString(), candidate.type(), candidate.quote(),
                setting.id(), setting.content(), candidate.reason(), candidate.suggestion(),
                previous.map(ReviewData::decision).orElse("미검토"), previous.map(ReviewData::note).orElse(""));
    }

    public AnalysisData toResult(AnalysisInput input, List<ReviewData> reviews) {
        return new AnalysisData(input.chapter().id(), input.chapter().revision(), input.chapter().content(),
                input.settings().stream().map(SettingReference::id).toList(),
                input.previous().stream().map(ChapterData::id).toList(), reviews,
                Instant.now().toString(), objectMapper.writeValueAsString(input.settings()));
    }
}
