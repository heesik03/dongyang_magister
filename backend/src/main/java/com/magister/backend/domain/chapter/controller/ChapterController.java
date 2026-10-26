package com.magister.backend.domain.chapter.controller;

import com.magister.backend.domain.chapter.docs.ChapterControllerDocs;
import com.magister.backend.domain.chapter.dto.ChapterResponse;
import com.magister.backend.domain.chapter.dto.CreateChapterRequest;
import com.magister.backend.domain.chapter.dto.UpdateChapterRequest;
import com.magister.backend.domain.chapter.service.ChapterService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/work/{workId}/chapter")
public class ChapterController implements ChapterControllerDocs {
    private final ChapterService chapterService;

    @Override
    @PostMapping
    public ResponseEntity<ApiResult<ChapterResponse>> create(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @RequestBody CreateChapterRequest request) {
        ChapterResponse result = chapterService.create(user.id(), workId, request);
        return ResponseEntity.created(
                        URI.create(
                                "/api/work/"
                                        + workId
                                        + "/chapter/"
                                        + UriUtils.encodePathSegment(
                                                result.id(), StandardCharsets.UTF_8)))
                .body(ApiResult.success(result));
    }

    @Override
    @GetMapping
    public ApiResult<OffsetPageResponse<ChapterResponse>> list(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResult.success(
                chapterService.list(user.id(), workId, OffsetPageRequest.of(page, size)));
    }

    @Override
    @GetMapping("/{chapterId}")
    public ApiResult<ChapterResponse> get(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @PathVariable String chapterId) {
        return ApiResult.success(chapterService.get(user.id(), workId, chapterId));
    }

    @Override
    @PutMapping("/{chapterId}")
    public ApiResult<ChapterResponse> update(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @PathVariable String chapterId,
            @RequestBody UpdateChapterRequest request) {
        return ApiResult.success(chapterService.update(user.id(), workId, chapterId, request));
    }
}
