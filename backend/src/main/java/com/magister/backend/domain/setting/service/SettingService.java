package com.magister.backend.domain.setting.service;

import com.magister.backend.global.pagination.OffsetPageRequest;
import com.magister.backend.domain.setting.converter.SettingConverter;
import com.magister.backend.domain.setting.dto.*;
import com.magister.backend.domain.setting.entity.*;
import com.magister.backend.domain.setting.error.SettingErrorCode;
import com.magister.backend.domain.setting.repository.WorkSettingRepository;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.work.service.WorkAccessService;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.pagination.OffsetPageResponse;
import com.magister.backend.global.pagination.CursorPageRequest;
import com.magister.backend.global.pagination.CursorPageResponse;
import com.magister.backend.global.pagination.CursorCodec;
import com.magister.backend.global.error.code.CommonErrorCode;
import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettingService {
    private final WorkAccessService workAccessService;
    private final WorkSettingRepository settings;
    private final CursorCodec cursorCodec;

    @Transactional
    public SettingResponse create(Long ownerId, Long workId, CreateSettingRequest request) {
        Work work = workAccessService.lockOwned(ownerId, workId);
        WorkSetting setting =
                new WorkSetting(
                        work, request.title(), request.kind(), request.status(), request.content());
        work.touch();
        return SettingConverter.toResponse(settings.saveAndFlush(setting));
    }

    @Transactional(readOnly = true)
    public OffsetPageResponse<SettingResponse> list(
            Long ownerId, Long workId, SettingKind kind, SettingStatus status, int page, int size) {
        workAccessService.requireOwned(ownerId, workId);
        return OffsetPageResponse.from(
                settings.search(
                                workId,
                                kind,
                                status,
                                OffsetPageRequest.of(page, size).toPageable(Sort.by("id").descending()))
                        .map(SettingConverter::toResponse));
    }

    @Transactional(readOnly = true)
    public SettingResponse get(Long ownerId, Long workId, Long settingId) {
        workAccessService.requireOwned(ownerId, workId);
        return SettingConverter.toResponse(findSetting(workId, settingId));
    }

    /** 빈 목록에도 소유권을 검사하고 동일 작품·필터의 ID 커서로 다음 묶음을 조회한다. */
    @Transactional(readOnly = true)
    public CursorPageResponse<SettingResponse> scroll(Long ownerId, Long workId, SettingKind kind,
            SettingStatus status, CursorPageRequest request) {
        workAccessService.requireOwned(ownerId, workId);

        String scope = cursorCodec.scope("setting", ownerId.toString(), workId.toString(),
                kind == null ? "" : kind.name(), status == null ? "" : status.name());
        Long beforeId = cursorCodec.decode(request.cursor(), scope, Long.class);
        if (beforeId != null && beforeId <= 0) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }

        List<WorkSetting> rows = settings.searchAfter(workId, kind, status, beforeId, request.size());
        return CursorPageResponse.from(rows, request, SettingConverter::toResponse,
                setting -> cursorCodec.encode(scope, setting.getId()));
    }

    @Transactional
    public SettingResponse update(
            Long ownerId, Long workId, Long settingId, UpdateSettingRequest request) {
        Work work = workAccessService.lockOwned(ownerId, workId);
        WorkSetting setting = findSetting(workId, settingId);
        if (!setting.getVersion().equals(request.version()))
            throw new CustomException(WorkErrorCode.VERSION_CONFLICT);
        setting.update(request.title(), request.kind(), request.status(), request.content());
        work.touch();
        settings.flush();
        return SettingConverter.toResponse(setting);
    }

    private WorkSetting findSetting(Long workId, Long settingId) {
        // 다른 작품의 설정 ID를 끼워 넣는 요청도 차단한다.
        return settings.findByIdAndWork_Id(settingId, workId)
                .orElseThrow(() -> new CustomException(SettingErrorCode.SETTING_NOT_FOUND));
    }
}
