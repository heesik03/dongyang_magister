package com.magister.backend.domain.analysis.dto;

import com.magister.backend.domain.workspace.dto.WorkspaceData.ChapterData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ReviewData;

import java.util.List;
import java.util.Objects;

public record AnalysisInput(
        ChapterData chapter,
        List<SettingReference> settings,
        List<ChapterData> previous,
        List<ReviewData> decisions,
        boolean consistency,
        boolean motivation) {
    /** 검증 뒤 다른 스레드에서 사용할 스냅샷이므로 원본 List의 후속 변경을 허용하지 않는다. */
    public AnalysisInput {
        Objects.requireNonNull(chapter);
        settings = List.copyOf(settings);
        previous = List.copyOf(previous);
        decisions = List.copyOf(decisions);
    }

    public record SettingReference(
            String id, String title, String content, String kind, String status, long version) {}
}
