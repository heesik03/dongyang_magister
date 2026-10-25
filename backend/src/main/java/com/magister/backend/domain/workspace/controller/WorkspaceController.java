package com.magister.backend.domain.workspace.controller;
import com.magister.backend.domain.workspace.docs.WorkspaceControllerDocs;

import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.domain.workspace.dto.*;
import com.magister.backend.domain.workspace.service.WorkspaceService;
import com.magister.backend.global.response.ApiResult;



import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/work/{workId}/workspace")
public class WorkspaceController implements WorkspaceControllerDocs {
    private final WorkspaceService service;

    @GetMapping
    public ApiResult<WorkspaceResponse> get(
            @AuthenticationPrincipal LoginUser user, @PathVariable Long workId) {
        return ApiResult.success(service.get(user.id(), workId));
    }

    @PutMapping
    public ApiResult<WorkspaceResponse> save(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @RequestBody SaveWorkspaceRequest request) {
        return ApiResult.success(service.save(user.id(), workId, request));
    }
}
