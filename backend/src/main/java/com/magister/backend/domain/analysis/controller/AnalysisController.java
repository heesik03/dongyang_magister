package com.magister.backend.domain.analysis.controller;

import com.magister.backend.domain.analysis.docs.AnalysisControllerDocs;
import com.magister.backend.domain.analysis.dto.*;
import com.magister.backend.domain.analysis.service.AnalysisJobService;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.global.response.ApiResult;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/work/{workId}/analysis")
public class AnalysisController implements AnalysisControllerDocs {
    private final AnalysisJobService jobs;

    @PostMapping
    public ResponseEntity<ApiResult<AnalysisJobResponse>> create(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @RequestBody AnalysisRequest request) {
        AnalysisJobResponse job = jobs.create(user.id(), workId, request);
        return ResponseEntity.accepted()
                .location(URI.create("/api/work/" + workId + "/analysis/" + job.id()))
                .body(ApiResult.success(job));
    }

    @GetMapping("/{id}")
    public ApiResult<AnalysisJobResponse> get(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @PathVariable String id) {
        return ApiResult.success(jobs.get(user.id(), workId, id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResult<AnalysisJobResponse> cancel(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @PathVariable String id) {
        return ApiResult.success(jobs.cancel(user.id(), workId, id));
    }
}
