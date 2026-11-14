package com.magister.backend.domain.gemini.controller;

import com.magister.backend.domain.gemini.dto.GeminiResponse;
import com.magister.backend.domain.gemini.dto.WritingAssistRequest;
import com.magister.backend.domain.gemini.service.WritingAssistService;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.global.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/work/{workId}/writing-assist")
public class WritingAssistController {
    private final WritingAssistService writingAssistService;

    @Operation(summary = "작품의 원고 문맥과 선택한 확정 설정으로 집필 제안 생성",
            description = "작품 소유자만 호출할 수 있습니다. 본문 최대 1,800자, 앞뒤 각각 최대 300자, 설정 최대 5개입니다.")
    @PostMapping
    public ApiResult<GeminiResponse> suggest(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @Valid @RequestBody WritingAssistRequest request) {
        return ApiResult.success(writingAssistService.suggest(user.id(), workId, request));
    }
}
