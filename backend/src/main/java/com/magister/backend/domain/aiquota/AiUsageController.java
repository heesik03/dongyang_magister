package com.magister.backend.domain.aiquota;

import com.magister.backend.domain.aiquota.AiQuotaPolicy.Feature;
import com.magister.backend.domain.aiquota.AiQuotaService.Usage;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.global.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class AiUsageController {
    private final AiQuotaService aiQuotaService;

    @GetMapping("/api/ai-usage")
    @Operation(
            summary = "일별 AI 작업 사용량 조회",
            description = "한국 시간(KST) 자정 기준으로 집계되는 장면 분석 일별 사용량과 초기화 시각을 반환합니다.")
    public ApiResult<List<Usage>> getUsage(@AuthenticationPrincipal LoginUser user) {
        Instant now = Instant.now();
        return ApiResult.success(
                List.of(aiQuotaService.usage(user.id(), Feature.SCENE_ANALYSIS, now)));
    }
}
