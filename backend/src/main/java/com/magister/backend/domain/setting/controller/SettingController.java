package com.magister.backend.domain.setting.controller;

import com.magister.backend.global.pagination.OffsetPageResponse;
import com.magister.backend.global.pagination.CursorPageRequest;
import com.magister.backend.global.pagination.CursorPageResponse;
import com.magister.backend.domain.setting.docs.SettingControllerDocs;
import com.magister.backend.domain.setting.dto.*;
import com.magister.backend.domain.setting.entity.*;
import com.magister.backend.domain.setting.service.SettingService;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.global.response.*;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/work/{workId}/setting")
@RequiredArgsConstructor
public class SettingController implements SettingControllerDocs {
    private final SettingService settings;

    @Override
    @PostMapping
    public ResponseEntity<ApiResult<SettingResponse>> create(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @RequestBody CreateSettingRequest request) {
        SettingResponse result = settings.create(user.id(), workId, request);
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
        return ApiResult.success(settings.list(user.id(), workId, kind, status, page, size));
    }

    @Override
    @GetMapping("/{settingId}")
    public ApiResult<SettingResponse> get(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @PathVariable Long settingId) {
        return ApiResult.success(settings.get(user.id(), workId, settingId));
    }

    @Override
    @GetMapping("/scroll")
    public ApiResult<CursorPageResponse<SettingResponse>> scroll(
            @AuthenticationPrincipal LoginUser user, @PathVariable Long workId,
            @RequestParam(required = false) SettingKind kind,
            @RequestParam(required = false) SettingStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResult.success(settings.scroll(user.id(), workId, kind, status,
                CursorPageRequest.of(cursor, size)));
    }

    @Override
    @PutMapping("/{settingId}")
    public ApiResult<SettingResponse> update(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @PathVariable Long settingId,
            @RequestBody UpdateSettingRequest request) {
        return ApiResult.success(settings.update(user.id(), workId, settingId, request));
    }
}
