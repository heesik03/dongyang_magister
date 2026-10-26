package com.magister.backend.domain.analysis.converter;

import com.magister.backend.domain.analysis.dto.AnalysisJobResponse;
import com.magister.backend.domain.analysis.entity.AnalysisJob;
import com.magister.backend.domain.analysis.repository.AnalysisJobView;
import com.magister.backend.domain.workspace.dto.WorkspaceData.AnalysisData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class AnalysisJobConverter {
    private final ObjectMapper objectMapper;

    public AnalysisJobResponse toResponse(AnalysisJob job) {
        return new AnalysisJobResponse(job.getId(), job.getStatus().name(), job.getError(),
                readResult(job.getResult()));
    }

    public AnalysisJobResponse toResponse(AnalysisJobView job) {
        return new AnalysisJobResponse(job.getId(), job.getStatus().name(), job.getError(),
                readResult(job.getResult()));
    }

    private AnalysisData readResult(String result) {
        return result == null ? null : objectMapper.readValue(result, AnalysisData.class);
    }
}
