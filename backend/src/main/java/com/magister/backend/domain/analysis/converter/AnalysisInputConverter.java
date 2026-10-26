package com.magister.backend.domain.analysis.converter;

import com.magister.backend.domain.analysis.dto.AnalysisInput;
import com.magister.backend.domain.analysis.dto.AnalysisInput.SettingReference;
import com.magister.backend.domain.analysis.dto.AnalysisRequest;
import com.magister.backend.domain.setting.entity.WorkSetting;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ChapterData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ReviewData;
import java.util.List;

public final class AnalysisInputConverter {
    private AnalysisInputConverter() {}

    public static AnalysisInput toInput(ChapterData chapter, List<SettingReference> settings,
            List<ChapterData> previous, List<ReviewData> decisions, AnalysisRequest request) {
        return new AnalysisInput(chapter, settings, previous, decisions,
                request.consistency(), request.motivation());
    }

    public static SettingReference toReference(WorkSetting setting) {
        String kind = switch (setting.getKind()) {
            case CHARACTER -> "인물";
            case WORLD -> "세계관";
            case MAJOR_EVENT -> "주요 사건";
            case INTENDED_ADAPTATION -> "의도한 각색";
        };
        return new SettingReference(setting.getId().toString(), setting.getTitle(),
                setting.getContent(), kind, "확정", setting.getVersion());
    }
}
