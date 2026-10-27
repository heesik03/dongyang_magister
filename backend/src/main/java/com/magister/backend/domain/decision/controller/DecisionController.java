package com.magister.backend.domain.decision.controller;

import com.magister.backend.domain.decision.dto.DecisionRequest;
import com.magister.backend.domain.decision.dto.DecisionResponse;
import com.magister.backend.domain.decision.service.DecisionService;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.global.pagination.OffsetPageRequest;
import com.magister.backend.global.pagination.OffsetPageResponse;
import com.magister.backend.global.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/work/{workId}")
@Tag(name = "Decision", description = "작가 판단 버전·범위·이력")
@SecurityRequirement(name = "session")
public class DecisionController {
    private final DecisionService decisionService;

    @PutMapping("/review/{reviewId}/decision")
    @Operation(
            summary = "작가 판단 기록",
            description =
                    "본인 후보만 저장. status·reason·expectedVersion·mutationId·basisHash·reconfirm·scopes"
                            + " 필요. 의도된 설정은 사유·범위·조건 필수. 미검토는 철회이며 scopes=[]. 원고·설정 자동 수정 없음. 동일"
                            + " mutation·내용 재전송은 중복 저장하지 않음. 200/400/401/403(CSRF)/404/409(다른 탭·근거"
                            + " 변경).")
    public ApiResult<DecisionResponse> save(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable @Positive Long workId,
            @PathVariable @Size(max = 36) String reviewId,
            @Valid @RequestBody DecisionRequest request) {
        return ApiResult.success(decisionService.save(user.id(), workId, reviewId, request));
    }

    @GetMapping("/review/{reviewId}/decision")
    @Operation(
            summary = "최신 판단 조회",
            description =
                    "미저장 후보는 version=0. basisHash는 현재 근거의 지문. stale은 저장 판단 이후 근거 변경, canReconfirm은"
                            + " 현재 원문에도 인용 위치가 일치하는지 표시. 200/400/401/404.")
    public ApiResult<DecisionResponse> get(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable @Positive Long workId,
            @PathVariable @Size(max = 36) String reviewId) {
        return ApiResult.success(decisionService.get(user.id(), workId, reviewId));
    }

    @GetMapping("/review/{reviewId}/decision/history")
    @Operation(
            summary = "판단 변경 이력",
            description = "버전 내림차순. page=0,size=20(최대100). 철회도 이력에 보존. 200/400/401/404.")
    public ApiResult<OffsetPageResponse<DecisionResponse>> history(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable @Positive Long workId,
            @PathVariable @Size(max = 36) String reviewId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResult.success(
                decisionService.history(
                        user.id(), workId, reviewId, OffsetPageRequest.of(page, size)));
    }

    @GetMapping("/decision")
    @Operation(
            summary = "작품의 누적 판단",
            description =
                    "최신 판단만 ID 내림차순. chapterId(원래 판단 회차)·status 필터 선택. page=0,size=20(최대100)."
                            + " 200/400/401/404.")
    public ApiResult<OffsetPageResponse<DecisionResponse>> list(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable @Positive Long workId,
            @RequestParam(required = false) @Size(max = 64) String chapterId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResult.success(
                decisionService.list(
                        user.id(), workId, chapterId, status, OffsetPageRequest.of(page, size)));
    }
}
