package com.magister.backend.domain.aiquota;

import com.magister.backend.domain.aiquota.AiQuotaPolicy.Feature;
import com.magister.backend.domain.aiquota.AiQuotaService.Usage;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.global.response.ApiResult;

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
    public ApiResult<List<Usage>> getUsage(@AuthenticationPrincipal LoginUser user) {
        Instant now = Instant.now();
        return ApiResult.success(List.of(
                aiQuotaService.usage(user.id(), Feature.SCENE_ANALYSIS, now),
                aiQuotaService.usage(user.id(), Feature.SCENE_IMAGE, now)));
    }
}
