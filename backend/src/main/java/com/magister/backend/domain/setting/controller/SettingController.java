package com.magister.backend.domain.setting.controller;

import com.magister.backend.domain.setting.docs.SettingControllerDocs;
import com.magister.backend.domain.setting.dto.CreateSettingRequest;
import com.magister.backend.domain.setting.dto.SettingResponse;
import com.magister.backend.domain.setting.dto.UpdateSettingRequest;
import com.magister.backend.domain.setting.entity.SettingKind;
import com.magister.backend.domain.setting.entity.SettingStatus;
import com.magister.backend.domain.setting.service.SettingService;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.global.pagination.CursorPageRequest;
import com.magister.backend.global.pagination.CursorPageResponse;
import com.magister.backend.global.pagination.OffsetPageResponse;
import com.magister.backend.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Min;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/work/{workId}/setting")
@RequiredArgsConstructor
public class SettingController implements SettingControllerDocs {
    private final SettingService settingService;

    @DeleteMapping("/{settingId}")
    @Operation(summary = "인물 설정과 연결된 이미지 삭제",
            description = "본인 작품의 인물만 삭제합니다. version은 조회한 설정 버전입니다. 204 삭제, 404 접근 불가 또는 인물 없음, 409 버전 충돌.")
    public ResponseEntity<Void> deleteCharacter(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @PathVariable Long settingId,
            @RequestParam @Min(0) long version) {
        settingService.deleteCharacter(user.id(), workId, settingId, version);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping
    public ResponseEntity<ApiResult<SettingResponse>> create(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @RequestBody CreateSettingRequest request) {
        SettingResponse result = settingService.create(user.id(), workId, request);
        return ResponseEntity.created(URI.create("/api/work/" + workId + "/setting/" + result.id()))
                .body(ApiResult.success(result));
    }

    @Override
    @GetMapping
    public ApiResult<OffsetPageResponse<SettingResponse>> list(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @RequestParam(required = false) SettingKind kind,
            @RequestParam(required = false) SettingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResult.success(settingService.list(user.id(), workId, kind, status, page, size));
    }

    @Override
    @GetMapping("/{settingId}")
    public ApiResult<SettingResponse> get(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @PathVariable Long settingId) {
        return ApiResult.success(settingService.get(user.id(), workId, settingId));
    }

    @Override
    @GetMapping("/scroll")
    public ApiResult<CursorPageResponse<SettingResponse>> scroll(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @RequestParam(required = false) SettingKind kind,
            @RequestParam(required = false) SettingStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResult.success(
                settingService.scroll(
                        user.id(), workId, kind, status, CursorPageRequest.of(cursor, size)));
    }

    @Override
    @PutMapping("/{settingId}")
    public ApiResult<SettingResponse> update(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @PathVariable Long settingId,
            @RequestBody UpdateSettingRequest request) {
        return ApiResult.success(settingService.update(user.id(), workId, settingId, request));
    }
}
