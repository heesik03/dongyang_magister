package com.magister.backend.domain.work.controller;

import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.domain.work.docs.WorkControllerDocs;
import com.magister.backend.domain.work.dto.*;
import com.magister.backend.domain.work.service.WorkService;
import com.magister.backend.global.response.*;

import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/work")
@RequiredArgsConstructor
public class WorkController implements WorkControllerDocs {
    private final WorkService works;

    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<WorkResponse>> create(
            @AuthenticationPrincipal LoginUser user, @RequestBody CreateWorkRequest request) {
        WorkResponse result = works.create(user.id(), request);
        return ResponseEntity.created(URI.create("/api/work/" + result.id()))
                .body(ApiResponse.success(result));
    }

    @Override
    @GetMapping
    public ApiResponse<PageResponse<WorkResponse>> list(
            @AuthenticationPrincipal LoginUser user,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String genre,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(works.list(user.id(), keyword, genre, page, size));
    }

    @Override
    @GetMapping("/{workId}")
    public ApiResponse<WorkResponse> get(
            @AuthenticationPrincipal LoginUser user, @PathVariable Long workId) {
        return ApiResponse.success(works.get(user.id(), workId));
    }

    @Override
    @PutMapping("/{workId}")
    public ApiResponse<WorkResponse> update(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @RequestBody UpdateWorkRequest request) {
        return ApiResponse.success(works.update(user.id(), workId, request));
    }
}
