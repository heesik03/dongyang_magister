package com.magister.backend.domain.analysis.service.input;

import com.magister.backend.domain.analysis.dto.AnalysisInput;
import com.magister.backend.domain.analysis.converter.AnalysisInputConverter;
import com.magister.backend.domain.analysis.dto.AnalysisInput.SettingReference;
import com.magister.backend.domain.analysis.dto.AnalysisRequest;
import com.magister.backend.domain.setting.entity.WorkSetting;
import com.magister.backend.domain.setting.repository.WorkSettingRepository;
import com.magister.backend.domain.workspace.dto.WorkspaceData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ChapterData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ReviewData;
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
    private final AnalysisRequestValidator requestValidator;

    /** 호출자의 접수 트랜잭션에서 선택 자료를 읽어 불변 분석 스냅샷을 구성한다. */
    public AnalysisInput create(Long workId, AnalysisRequest request, WorkspaceResponse workspace) {
        requestValidator.validate(request, workspace);
        WorkspaceData content = workspace.content();
        ChapterData chapter = content.chapters().stream()
                .filter(item -> item.id().equals(request.chapterId())).findFirst()
                .orElseThrow(() -> new CustomException(CommonErrorCode.INVALID_REQUEST));
        if (chapter.content().isBlank()) throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        List<Long> settingIds = parseSettingIds(request.settingIds());
        List<WorkSetting> settings = workSettingRepository.findAllByWork_IdAndIdIn(workId, settingIds);
        requestValidator.validateSettings(settings, settingIds.size());
        List<SettingReference> references = settings.stream().map(AnalysisInputConverter::toReference).toList();
        Set<String> previousIds = new HashSet<>(request.previousIds());
        List<ChapterData> previous = content.chapters().stream()
                .filter(item -> previousIds.contains(item.id()) && !item.id().equals(chapter.id()))
                .toList();
        if (previous.size() != previousIds.size()) throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        // 이전 판단은 같은 회차의 원문·설정 근거가 일치하는 경우에만 결과 검증기가 재사용한다.
        List<ReviewData> decisions = content.analyses().stream()
                .filter(item -> item.chapterId().equals(chapter.id()))
                .flatMap(item -> item.reviews().stream()).toList();
        return AnalysisInputConverter.toInput(chapter, references, previous, decisions, request);
    }

    private List<Long> parseSettingIds(List<String> values) {
        try {
            return values.stream().map(Long::valueOf).distinct().toList();
        } catch (NumberFormatException error) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
    }

}
