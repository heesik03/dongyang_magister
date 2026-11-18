package com.magister.backend.domain.analysis.controller;

import com.magister.backend.domain.analysis.dto.RevisionSuggestion;
import com.magister.backend.domain.analysis.service.RevisionSuggestionContextReader;
import com.magister.backend.domain.analysis.service.RevisionSuggestionService;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/work/{workId}/review/{reviewId}/revision-suggestion")
public class RevisionSuggestionController {
    private final RevisionSuggestionContextReader contextReader;
    private final RevisionSuggestionService suggestionService;

    @Operation(summary = "보완 문장 전송 범위 확인", description = "작품 소유자 전용. 인용과 앞뒤 각 300자 이내 문맥, 후보에 연결된 확정 설정 인용을 반환합니다.")
    @GetMapping("/context")
    public ApiResult<RevisionSuggestion.Context> context(@AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId, @PathVariable String reviewId) {
        return ApiResult.success(contextReader.read(user.id(), workId, reviewId));
    }

    @Operation(summary = "검토 후보 보완 문장 생성", description = "미리보기의 contextHash를 제출합니다. 대상 구간과 대체 문장을 반환합니다. 근거 변경 409, 호출 한도 429.")
    @PostMapping
    public ApiResult<RevisionSuggestion.Result> generate(@AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId, @PathVariable String reviewId, @Valid @RequestBody RevisionSuggestion.Request request) {
        return ApiResult.success(suggestionService.generate(user.id(), workId, reviewId, request.contextHash()));
    }
}
