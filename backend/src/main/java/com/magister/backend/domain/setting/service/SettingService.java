package com.magister.backend.domain.setting.service;

import com.magister.backend.domain.setting.converter.SettingConverter;
import com.magister.backend.domain.setting.dto.CreateSettingRequest;
import com.magister.backend.domain.setting.dto.SettingResponse;
import com.magister.backend.domain.setting.dto.UpdateSettingRequest;
import com.magister.backend.domain.setting.entity.SettingKind;
import com.magister.backend.domain.setting.entity.SettingStatus;
import com.magister.backend.domain.setting.entity.WorkSetting;
import com.magister.backend.domain.setting.error.SettingErrorCode;
import com.magister.backend.domain.setting.repository.WorkSettingRepository;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.work.service.WorkAccessService;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.pagination.CursorCodec;
import com.magister.backend.global.pagination.CursorPageRequest;
import com.magister.backend.global.pagination.CursorPageResponse;
import com.magister.backend.global.pagination.OffsetPageRequest;
import com.magister.backend.global.pagination.OffsetPageResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SettingService {
    private final WorkAccessService workAccessService;
    private final WorkSettingRepository workSettingRepository;
    private final CursorCodec cursorCodec;

    /** 작품 잠금 아래 설정과 부모의 수정 버전을 함께 저장한다. */
    @Transactional
    public SettingResponse create(Long ownerId, Long workId, CreateSettingRequest request) {
        Work work = workAccessService.lockOwned(ownerId, workId);
        WorkSetting setting =
                new WorkSetting(
                        work, request.title(), request.kind(), request.status(), request.content());
        work.touch();
        return SettingConverter.toResponse(workSettingRepository.saveAndFlush(setting));
    }

    /** 기존 오프셋 계약을 유지하며 빈 목록도 먼저 작품 소유권을 검사한다. */
    @Transactional(readOnly = true)
    public OffsetPageResponse<SettingResponse> list(
            Long ownerId, Long workId, SettingKind kind, SettingStatus status, int page, int size) {
        workAccessService.requireOwned(ownerId, workId);
        return OffsetPageResponse.from(
                workSettingRepository
                        .search(
                                workId,
                                kind,
                                status,
                                OffsetPageRequest.of(page, size)
                                        .toPageable(Sort.by("id").descending()))
                        .map(SettingConverter::toResponse));
    }

    /** 소유권과 작품별 설정 범위를 모두 확인한 단건 조회다. */
    @Transactional(readOnly = true)
    public SettingResponse get(Long ownerId, Long workId, Long settingId) {
        workAccessService.requireOwned(ownerId, workId);
        return SettingConverter.toResponse(findSetting(workId, settingId));
    }

    /** 빈 목록에도 소유권을 검사하고 동일 작품·필터의 ID 커서로 다음 묶음을 조회한다. */
    @Transactional(readOnly = true)
    public CursorPageResponse<SettingResponse> scroll(
            Long ownerId,
            Long workId,
            SettingKind kind,
            SettingStatus status,
            CursorPageRequest request) {
        workAccessService.requireOwned(ownerId, workId);

        String scope =
                cursorCodec.scope(
                        "setting",
                        ownerId.toString(),
                        workId.toString(),
                        kind == null ? "" : kind.name(),
                        status == null ? "" : status.name());
        Long beforeId = cursorCodec.decode(request.cursor(), scope, Long.class);
        if (beforeId != null && beforeId <= 0) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }

        List<WorkSetting> rows =
                workSettingRepository.searchAfter(workId, kind, status, beforeId, request.size());
        return CursorPageResponse.from(
                rows,
                request,
                SettingConverter::toResponse,
                setting -> cursorCodec.encode(scope, setting.getId()));
    }

    /** 부모 -> 설정 순서로 접근하고 읽었던 설정 버전이 일치할 때만 변경한다. */
    @Transactional
    public SettingResponse update(
            Long ownerId, Long workId, Long settingId, UpdateSettingRequest request) {
        Work work = workAccessService.lockOwned(ownerId, workId);
        WorkSetting setting = findSetting(workId, settingId);
        if (!setting.getVersion().equals(request.version())) {
            throw new CustomException(WorkErrorCode.VERSION_CONFLICT);
        }

        setting.update(request.title(), request.kind(), request.status(), request.content());
        work.touch();
        workSettingRepository.flush();
        return SettingConverter.toResponse(setting);
    }

    /** 설정 ID만 사용하지 않아 다른 작품 ID를 끼워 넣는 접근을 차단한다. */
    private WorkSetting findSetting(Long workId, Long settingId) {
        // 다른 작품의 설정 ID를 끼워 넣는 요청도 차단한다.
        return workSettingRepository
                .findByIdAndWork_Id(settingId, workId)
                .orElseThrow(() -> new CustomException(SettingErrorCode.SETTING_NOT_FOUND));
    }
}
