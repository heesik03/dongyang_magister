package com.magister.backend.domain.analysis.dto;

import com.magister.backend.domain.decision.dto.DecisionReference;
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
        boolean motivation,
        List<DecisionReference> decisionReferences) {
    /** 검증 뒤 다른 스레드에서 사용할 스냅샷이므로 원본 List의 후속 변경을 허용하지 않는다. */
    public AnalysisInput {
        Objects.requireNonNull(chapter);
        settings = List.copyOf(settings);
        previous = List.copyOf(previous);
        decisions = List.copyOf(decisions);
        decisionReferences =
                decisionReferences == null ? List.of() : List.copyOf(decisionReferences);
    }

    public AnalysisInput(
            ChapterData chapter,
            List<SettingReference> settings,
            List<ChapterData> previous,
            List<ReviewData> decisions,
            boolean consistency,
            boolean motivation) {
        this(chapter, settings, previous, decisions, consistency, motivation, List.of());
    }

    public record SettingReference(
            String id, String title, String content, String kind, String status, long version) {}
}
