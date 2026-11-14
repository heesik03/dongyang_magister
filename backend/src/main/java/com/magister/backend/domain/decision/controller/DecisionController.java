package com.magister.backend.domain.decision.controller;

import com.magister.backend.domain.decision.docs.DecisionControllerDocs;
import com.magister.backend.domain.decision.dto.DecisionRequest;
import com.magister.backend.domain.decision.dto.DecisionResponse;
import com.magister.backend.domain.decision.service.DecisionService;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.global.response.ApiResult;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/work/{workId}")
public class DecisionController implements DecisionControllerDocs {
    private final DecisionService decisionService;

    @Override
    @PutMapping("/review/{reviewId}/decision")
    public ApiResult<DecisionResponse> save(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @PathVariable String reviewId,
            @RequestBody DecisionRequest request) {
        return ApiResult.success(decisionService.save(user.id(), workId, reviewId, request));
    }

    @Override
    @GetMapping("/review/{reviewId}/decision")
    public ApiResult<DecisionResponse> get(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @PathVariable String reviewId) {
        return ApiResult.success(decisionService.get(user.id(), workId, reviewId));
    }
}
