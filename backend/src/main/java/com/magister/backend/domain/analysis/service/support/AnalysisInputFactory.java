package com.magister.backend.domain.analysis.service.support;

import com.magister.backend.domain.analysis.converter.AnalysisConverter;
import com.magister.backend.domain.analysis.dto.AnalysisInput.SettingReference;
import com.magister.backend.domain.analysis.dto.AnalysisInput;
import com.magister.backend.domain.analysis.dto.AnalysisRequest;
import com.magister.backend.domain.setting.entity.SettingStatus;
import com.magister.backend.domain.setting.entity.WorkSetting;
import com.magister.backend.domain.setting.repository.WorkSettingRepository;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ChapterData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ReviewData;
import com.magister.backend.domain.workspace.dto.WorkspaceData;
import com.magister.backend.domain.workspace.dto.WorkspaceResponse;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AnalysisInputFactory {
    private final WorkSettingRepository workSettingRepository;

    /** 호출자의 접수 트랜잭션에서 선택 자료를 읽어 불변 분석 스냅샷을 구성한다. */
    public AnalysisInput create(Long workId, AnalysisRequest request, WorkspaceResponse workspace) {
        validateRequest(request, workspace);

        WorkspaceData content = workspace.content();
        ChapterData chapter = content.chapters().stream()
                .filter(item -> item.id().equals(request.chapterId())).findFirst()
                .orElseThrow(() -> new CustomException(CommonErrorCode.INVALID_REQUEST));
        if (chapter.content().isBlank()) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }

        List<Long> settingIds = parseSettingIds(request.settingIds());
        List<WorkSetting> settings = workSettingRepository.findAllByWork_IdAndIdIn(workId, settingIds);
        validateSettings(settings, settingIds.size());
        List<SettingReference> references = settings.stream().map(AnalysisConverter::toReference).toList();

        Set<String> previousIds = new HashSet<>(request.previousIds());
        List<ChapterData> previous = content.chapters().stream()
                .filter(item -> previousIds.contains(item.id()) && !item.id().equals(chapter.id()))
                .toList();
        if (previous.size() != previousIds.size()) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }

        // 이전 판단은 같은 회차의 원문·설정 근거가 일치하는 경우에만 결과 검증기가 재사용한다.
        List<ReviewData> decisions = content.analyses().stream()
                .filter(item -> item.chapterId().equals(chapter.id()))
                .flatMap(item -> item.reviews().stream()).toList();

        return AnalysisConverter.toInput(chapter, references, previous, decisions, request);
    }

    private List<Long> parseSettingIds(List<String> values) {
        try {
            return values.stream().map(Long::valueOf).distinct().toList();
        } catch (NumberFormatException error) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    /** 스냅샷 버전과 검토 옵션을 확인해 오래된 원고의 묵시적 분석을 방지한다. */
    private void validateRequest(AnalysisRequest request, WorkspaceResponse workspace) {
        if (workspace.version() != request.workspaceVersion()) {
            throw new CustomException(WorkErrorCode.VERSION_CONFLICT);
        }
        if (!request.consistency() && !request.motivation()) {
            invalid();
        }
    }

    /** 요청한 설정이 모두 현재 작품에 존재하고 확정 상태인지 확인한다. */
    private void validateSettings(List<WorkSetting> settings, int expectedCount) {
        if (settings.size() != expectedCount
                || settings.stream().anyMatch(setting -> setting.getStatus() != SettingStatus.CONFIRMED)) {
            invalid();
        }
    }

    private void invalid() {
        throw new CustomException(CommonErrorCode.INVALID_REQUEST);
    }
}
