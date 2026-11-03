package com.magister.backend.domain.timeline.controller;

import com.magister.backend.domain.timeline.dto.ExtractionData.ConfirmationRequest;
import com.magister.backend.domain.timeline.dto.ExtractionData.CreateRequest;
import com.magister.backend.domain.timeline.dto.ExtractionData.Input;
import com.magister.backend.domain.timeline.dto.ExtractionData.Job;
import com.magister.backend.domain.timeline.dto.ExtractionData.Preview;
import com.magister.backend.domain.timeline.dto.ExtractionData.PreviewRequest;
import com.magister.backend.domain.timeline.dto.ExtractionData.SaveCandidates;
import com.magister.backend.domain.timeline.service.ExtractionService;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.domain.workspace.dto.WorkspaceResponse;
import com.magister.backend.global.pagination.OffsetPageResponse;
import com.magister.backend.global.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work/{workId}/event-extraction")
@RequiredArgsConstructor
@Validated
@Tag(
        name = "사건 추출",
        description = "본인 작품·세션·CSRF 필수. 최대 10회차/20만 자, 사용자당 미완료 작업 1개. 409 버전 불일치, 429 접수 한도.")
public class ExtractionController {
    private final ExtractionService extractionService;

    @PostMapping("/preview")
    @Operation(
            summary = "외부 AI로 전송할 원고와 모델 확인",
            description = "입력 예: chapters:[{id:chapter-1,revision:1}]. 실제 AI 호출·저장 없음.")
    public ApiResult<Preview> preview(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @Valid @RequestBody PreviewRequest request) {
        return ApiResult.success(extractionService.preview(user.id(), workId, request));
    }

    @PostMapping
    @Operation(
            summary = "동의한 원고의 사건 추출 접수",
            description =
                    "preview의 chapters/token과 consent:true, 재시도에 같은 requestId를 보낸다. 202 및 작업 ID"
                            + " 반환.")
    public ResponseEntity<ApiResult<Job>> create(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @Valid @RequestBody CreateRequest request) {
        return ResponseEntity.accepted()
                .body(ApiResult.success(extractionService.create(user.id(), workId, request)));
    }

    @GetMapping
    @Operation(summary = "작품의 사건 추출 이력", description = "페이지당 5개, page는 0부터 시작. 원고 본문 제외.")
    public ApiResult<OffsetPageResponse<Job>> list(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @RequestParam(defaultValue = "0") @Min(0) int page) {
        return ApiResult.success(extractionService.list(user.id(), workId, page));
    }

    @GetMapping("/{id}")
    @Operation(summary = "추출 상태와 후보 조회")
    public ApiResult<Job> get(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @PathVariable String id) {
        return ApiResult.success(extractionService.get(user.id(), workId, id));
    }

    @GetMapping("/{id}/source")
    @Operation(summary = "추출 당시 원고와 버전 확인")
    public ApiResult<Input> source(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @PathVariable String id) {
        return ApiResult.success(extractionService.source(user.id(), workId, id));
    }

    @PutMapping("/{id}/candidate")
    @Operation(summary = "후보 수정·확정 선택·제외 저장", description = "version과 전체 후보 목록. 근거 위치·원고 버전은 불변.")
    public ApiResult<Job> save(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @PathVariable String id,
            @Valid @RequestBody SaveCandidates request) {
        return ApiResult.success(extractionService.save(user.id(), workId, id, request));
    }

    @PostMapping("/{id}/confirmation")
    @Operation(
            summary = "선택한 후보를 기존 타임라인에 추가",
            description = "작업 version, workspaceVersion, mutationId 필수. 기존 사건 보존 및 같은 후보 중복 확정 방지.")
    public ApiResult<WorkspaceResponse> confirm(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @PathVariable String id,
            @Valid @RequestBody ConfirmationRequest request) {
        return ApiResult.success(
                extractionService.confirm(
                        user.id(),
                        workId,
                        id,
                        request.version(),
                        request.workspaceVersion(),
                        request.mutationId()));
    }

    @PutMapping("/{id}/cancellation")
    @Operation(summary = "미완료 추출 취소", description = "멱등. 이미 완료된 작업과 기존 사건을 보존한다.")
    public ApiResult<Job> cancel(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @PathVariable String id) {
        return ApiResult.success(extractionService.cancel(user.id(), workId, id));
    }
}
