package com.magister.backend.domain.analysis.service.support;

import com.magister.backend.domain.analysis.converter.AnalysisConverter;
import com.magister.backend.domain.analysis.dto.AnalysisInput;
import com.magister.backend.domain.analysis.dto.AnalysisInput.SettingReference;
import com.magister.backend.domain.analysis.dto.AnalysisRequest;
import com.magister.backend.domain.setting.entity.SettingStatus;
import com.magister.backend.domain.setting.entity.WorkSetting;
import com.magister.backend.domain.setting.repository.WorkSettingRepository;
import com.magister.backend.domain.work.error.WorkErrorCode;
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

/** 같은 원고 버전의 선택 범위 검증과 분석 입력 조립을 한곳에 유지한다. */
@Component
@RequiredArgsConstructor
public class AnalysisInputFactory {
    private final WorkSettingRepository workSettingRepository;

    /**
     * 호출자의 작품 잠금 아래에서 현재 회차·확정 설정·선택한 이전 회차·과거 판단을 묶는다. 다른 작품의 설정, 선택하지 못한 회차, 현재 회차의 이전 회차 지정은
     * 거절한다. 공급자 호출 전에 완성된 값이므로 이후 원고 편집이 이번 분석 근거를 바꾸지 않는다.
     */
    public AnalysisInput create(Long workId, AnalysisRequest request, WorkspaceResponse workspace) {
        validateRequest(request, workspace);

        WorkspaceData content = workspace.content();
        ChapterData chapter =
                content.chapters().stream()
                        .filter(item -> item.id().equals(request.chapterId()))
                        .findFirst()
                        .orElseThrow(() -> new CustomException(CommonErrorCode.INVALID_REQUEST));
        if (chapter.content().isBlank()) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
        if (request.chapterRevision() != null && chapter.revision() != request.chapterRevision()) {
            throw new CustomException(WorkErrorCode.VERSION_CONFLICT);
        }

        // 설정별 findById를 반복하지 않는다. 작품 조건을 포함한 IN 질의 후 누락 수까지 검사한다.
        List<Long> settingIds = parseSettingIds(request.settingIds());
        List<WorkSetting> settings =
                workSettingRepository.findAllByWork_IdAndIdIn(workId, settingIds);
        validateSettings(settings, settingIds.size());
        List<SettingReference> references =
                settings.stream().map(AnalysisConverter::toReference).toList();

        // Set으로 중복을 제거하고 현재 회차를 제외한다. 크기 비교로 존재하지 않는 선택도 검출한다.
        Set<String> earlierIds = new HashSet<>();
        for (ChapterData candidate : content.chapters()) {
            if (candidate.id().equals(chapter.id())) {
                break;
            }
            earlierIds.add(candidate.id());
        }
        Set<String> previousIds = new HashSet<>(request.previousIds());
        if (!earlierIds.containsAll(previousIds)) {
            invalid();
        }
        List<ChapterData> previous =
                content.chapters().stream()
                        .filter(
                                item ->
                                        previousIds.contains(item.id())
                                                && !item.id().equals(chapter.id()))
                        .toList();
        if (previous.size() != previousIds.size()) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }

        // 이전 판단은 같은 회차의 원문·설정 근거가 일치하는 경우에만 결과 검증기가 재사용한다.
        List<ReviewData> decisions =
                content.analyses().stream()
                        .filter(item -> item.chapterId().equals(chapter.id()))
                        .flatMap(item -> item.reviews().stream())
                        .toList();

        return AnalysisConverter.toInput(chapter, references, previous, decisions, request);
    }

    /** 외부 문자열 ID를 영속 ID로 변환하며 숫자가 아닌 값은 DB 조회 전에 거절한다. */
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
                || settings.stream()
                        .anyMatch(setting -> setting.getStatus() != SettingStatus.CONFIRMED)) {
            invalid();
        }
    }

    private void invalid() {
        throw new CustomException(CommonErrorCode.INVALID_REQUEST);
    }
}
