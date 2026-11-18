package com.magister.backend.domain.analysis.controller;

import com.magister.backend.domain.analysis.docs.RevisionSuggestionControllerDocs;
import com.magister.backend.domain.analysis.dto.RevisionSuggestion;
import com.magister.backend.domain.analysis.service.RevisionSuggestionContextReader;
import com.magister.backend.domain.analysis.service.RevisionSuggestionService;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.global.response.ApiResult;
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
public class RevisionSuggestionController implements RevisionSuggestionControllerDocs {
    private final RevisionSuggestionContextReader contextReader;
    private final RevisionSuggestionService suggestionService;

    @Override
    @GetMapping("/context")
    public ApiResult<RevisionSuggestion.Context> context(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @PathVariable String reviewId) {
        return ApiResult.success(contextReader.read(user.id(), workId, reviewId));
    }

    @Override
    @PostMapping
    public ApiResult<RevisionSuggestion.Result> generate(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @PathVariable String reviewId,
            @RequestBody RevisionSuggestion.Request request) {
        return ApiResult.success(
                suggestionService.generate(user.id(), workId, reviewId, request.contextHash()));
    }
}
