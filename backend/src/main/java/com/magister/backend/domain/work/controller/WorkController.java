package com.magister.backend.domain.work.controller;

import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.domain.work.docs.WorkControllerDocs;
import com.magister.backend.domain.work.dto.CreateWorkRequest;
import com.magister.backend.domain.work.dto.UpdateWorkRequest;
import com.magister.backend.domain.work.dto.WorkResponse;
import com.magister.backend.domain.work.service.WorkService;
import com.magister.backend.global.pagination.OffsetPageResponse;
import com.magister.backend.global.response.ApiResult;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/work")
@RequiredArgsConstructor
public class WorkController implements WorkControllerDocs {
    private final WorkService workService;

    @Override
    @PostMapping
    public ResponseEntity<ApiResult<WorkResponse>> create(
            @AuthenticationPrincipal LoginUser user, @RequestBody CreateWorkRequest request) {
        WorkResponse result = workService.create(user.id(), request);
        return ResponseEntity.created(URI.create("/api/work/" + result.id()))
                .body(ApiResult.success(result));
    }

    @Override
    @GetMapping
    public ApiResult<OffsetPageResponse<WorkResponse>> list(
            @AuthenticationPrincipal LoginUser user,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String genre,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResult.success(workService.list(user.id(), keyword, genre, page, size));
    }

    @Override
    @GetMapping("/{workId}")
    public ApiResult<WorkResponse> get(
            @AuthenticationPrincipal LoginUser user, @PathVariable Long workId) {
        return ApiResult.success(workService.get(user.id(), workId));
    }

    @Override
    @PutMapping("/{workId}")
    public ApiResult<WorkResponse> update(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @RequestBody UpdateWorkRequest request) {
        return ApiResult.success(workService.update(user.id(), workId, request));
    }
}
