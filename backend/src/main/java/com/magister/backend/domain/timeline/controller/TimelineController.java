package com.magister.backend.domain.timeline.controller;

import com.magister.backend.domain.timeline.dto.TimelineData.ReplaceTimelineRequest;
import com.magister.backend.domain.timeline.dto.TimelineData.SaveEventRequest;
import com.magister.backend.domain.timeline.dto.TimelineData.TimelineResponse;
import com.magister.backend.domain.timeline.service.TimelineService;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.domain.workspace.dto.WorkspaceData.EventData;
import com.magister.backend.global.response.ApiResult;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/work/{workId}/timeline")
public class TimelineController {
    private final TimelineService timelineService;

    @GetMapping
    public ApiResult<List<EventData>> list(
            @AuthenticationPrincipal LoginUser user, @PathVariable Long workId) {
        return ApiResult.success(timelineService.list(user.id(), workId));
    }

    @PostMapping
    public ResponseEntity<ApiResult<EventData>> create(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @Valid @RequestBody SaveEventRequest request) {
        EventData created = timelineService.create(user.id(), workId, request);
        return ResponseEntity.created(
                        URI.create(
                                "/api/work/"
                                        + workId
                                        + "/timeline/"
                                        + UriUtils.encodePathSegment(
                                                created.id(), StandardCharsets.UTF_8)))
                .body(ApiResult.success(created));
    }

    @PutMapping("/{eventId}")
    public ApiResult<EventData> update(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @PathVariable String eventId,
            @Valid @RequestBody SaveEventRequest request) {
        return ApiResult.success(timelineService.update(user.id(), workId, eventId, request));
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @PathVariable String eventId) {
        timelineService.delete(user.id(), workId, eventId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping
    @Operation(summary = "작품의 타임라인 사건 목록 저장",
            description = "현재 workspace 버전과 최대 1,000개 사건을 전송합니다. 성공 시 최신 버전과 사건 목록을 반환하며, 오래된 버전은 409로 거절합니다.")
    public ApiResult<TimelineResponse> replaceAll(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @Valid @RequestBody ReplaceTimelineRequest request) {
        return ApiResult.success(
                timelineService.replaceAll(user.id(), workId, request.version(), request.events()));
    }
}
