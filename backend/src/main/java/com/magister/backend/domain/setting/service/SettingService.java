package com.magister.backend.domain.setting.service;

import com.magister.backend.domain.setting.dto.*;
import com.magister.backend.domain.setting.entity.*;
import com.magister.backend.domain.setting.error.SettingErrorCode;
import com.magister.backend.domain.setting.repository.WorkSettingRepository;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.work.repository.WorkRepository;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.response.PageResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettingService {
    private final WorkRepository works;
    private final WorkSettingRepository settings;

    @Transactional
    public SettingResponse create(Long ownerId, Long workId, CreateSettingRequest request) {
        requireOwned(ownerId, workId);
        WorkSetting setting =
                new WorkSetting(
                        workId,
                        request.title(),
                        request.kind(),
                        request.status(),
                        request.content());
        return SettingResponse.from(settings.saveAndFlush(setting));
    }

    @Transactional(readOnly = true)
    public PageResponse<SettingResponse> list(
            Long ownerId, Long workId, SettingKind kind, SettingStatus status, int page, int size) {
        requireOwned(ownerId, workId);
        return PageResponse.from(
                settings.search(
                                workId,
                                kind,
                                status,
                                PageRequest.of(page, size, Sort.by("id").descending()))
                        .map(SettingResponse::from));
    }

    @Transactional(readOnly = true)
    public SettingResponse get(Long ownerId, Long workId, Long settingId) {
        requireOwned(ownerId, workId);
        return SettingResponse.from(findSetting(workId, settingId));
    }

    @Transactional
    public SettingResponse update(
            Long ownerId, Long workId, Long settingId, UpdateSettingRequest request) {
        requireOwned(ownerId, workId);
        WorkSetting setting = findSetting(workId, settingId);
        if (!setting.getVersion().equals(request.version()))
            throw new CustomException(WorkErrorCode.VERSION_CONFLICT);
        setting.update(request.title(), request.kind(), request.status(), request.content());
        settings.flush();
        return SettingResponse.from(setting);
    }

    private void requireOwned(Long ownerId, Long workId) {
        // 비어 있는 설정 목록도 본인 작품인지 검사한 뒤 반환한다.
        works.findByIdAndOwnerId(workId, ownerId)
                .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND));
    }

    private WorkSetting findSetting(Long workId, Long settingId) {
        // 다른 작품의 설정 ID를 끼워 넣는 요청도 차단한다.
        return settings.findByIdAndWorkId(settingId, workId)
                .orElseThrow(() -> new CustomException(SettingErrorCode.SETTING_NOT_FOUND));
    }
}
