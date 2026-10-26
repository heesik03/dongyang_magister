package com.magister.backend.domain.analysis.service.input;

import com.magister.backend.domain.analysis.dto.AnalysisRequest;
import com.magister.backend.domain.setting.entity.SettingStatus;
import com.magister.backend.domain.setting.entity.WorkSetting;
import com.magister.backend.domain.workspace.dto.WorkspaceResponse;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class AnalysisRequestValidator {
    /** 스냅샷 버전과 검토 옵션을 확인해 오래된 원고의 묵시적 분석을 방지한다. */
    public void validate(AnalysisRequest request, WorkspaceResponse workspace) {
        if (workspace.version() != request.workspaceVersion()) {
            throw new CustomException(WorkErrorCode.VERSION_CONFLICT);
        }
        if (!request.consistency() && !request.motivation()) invalid();
    }

    /** 요청한 설정이 모두 현재 작품에 존재하고 확정 상태인지 확인한다. */
    public void validateSettings(List<WorkSetting> settings, int expectedCount) {
        if (settings.size() != expectedCount
                || settings.stream().anyMatch(setting -> setting.getStatus() != SettingStatus.CONFIRMED)) {
            invalid();
        }
    }

    private void invalid() {
        throw new CustomException(CommonErrorCode.INVALID_REQUEST);
    }
}
