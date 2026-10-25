package com.magister.backend.domain.analysis.dto;

import com.magister.backend.domain.workspace.dto.WorkspaceData.*;

import java.util.List;

public record AnalysisInput(
        ChapterData chapter,
        List<SettingReference> settings,
        List<ChapterData> previous,
        List<ReviewData> decisions,
        boolean consistency,
        boolean motivation) {
    public record SettingReference(
            String id, String title, String content, String kind, String status, long version) {}
}
