package com.magister.backend.domain.analysis.controller;

import com.magister.backend.domain.analysis.docs.AnalysisControllerDocs;
import com.magister.backend.domain.analysis.dto.AnalysisJobResponse;
import com.magister.backend.domain.analysis.dto.AnalysisJobSummary;
import com.magister.backend.domain.analysis.dto.AnalysisRequest;
import com.magister.backend.domain.analysis.dto.ReviewReportResponse;
import com.magister.backend.domain.analysis.entity.AnalysisJobStatus;
import com.magister.backend.domain.analysis.service.AnalysisJobService;
import com.magister.backend.domain.analysis.service.ReviewResultService;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.global.pagination.OffsetPageRequest;
import com.magister.backend.global.pagination.OffsetPageResponse;
import com.magister.backend.global.response.ApiResult;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/work/{workId}/analysis")
public class AnalysisController implements AnalysisControllerDocs {
    private final AnalysisJobService analysisJobService;
    private final ReviewResultService reviewResultService;

    @Override
    @GetMapping("/{id}/review")
    public ApiResult<ReviewReportResponse> reviews(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResult.success(
                reviewResultService.list(user.id(), workId, id, OffsetPageRequest.of(page, size)));
    }

    @Override
    @GetMapping
    public ApiResult<OffsetPageResponse<AnalysisJobSummary>> list(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @RequestParam(required = false) String chapterId,
            @RequestParam(required = false) AnalysisJobStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResult.success(
                analysisJobService.list(
                        user.id(), workId, chapterId, status, OffsetPageRequest.of(page, size)));
    }

    @Override
    @PostMapping
    public ResponseEntity<ApiResult<AnalysisJobResponse>> create(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @RequestBody AnalysisRequest request) {
        AnalysisJobResponse job = analysisJobService.create(user.id(), workId, request);
        return ResponseEntity.accepted()
                .location(URI.create("/api/work/" + workId + "/analysis/" + job.id()))
                .body(ApiResult.success(job));
    }

    @Override
    @GetMapping("/{id}")
    public ApiResult<AnalysisJobResponse> get(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @PathVariable String id) {
        return ApiResult.success(analysisJobService.get(user.id(), workId, id));
    }

    @Override
    @PostMapping("/{id}/cancel")
    public ApiResult<AnalysisJobResponse> cancel(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @PathVariable String id) {
        return ApiResult.success(analysisJobService.cancel(user.id(), workId, id));
    }
}
