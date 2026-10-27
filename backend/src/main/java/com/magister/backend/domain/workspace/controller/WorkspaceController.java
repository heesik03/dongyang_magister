package com.magister.backend.domain.workspace.controller;

import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.domain.workspace.docs.WorkspaceControllerDocs;
import com.magister.backend.domain.workspace.dto.SaveWorkspaceRequest;
import com.magister.backend.domain.workspace.dto.WorkspaceResponse;
import com.magister.backend.domain.workspace.service.WorkspaceService;
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
@RequestMapping("/api/work/{workId}/workspace")
public class WorkspaceController implements WorkspaceControllerDocs {
    private final WorkspaceService workspaceService;

    @Override
    @GetMapping
    public ApiResult<WorkspaceResponse> get(
            @AuthenticationPrincipal LoginUser user, @PathVariable Long workId) {
        return ApiResult.success(workspaceService.get(user.id(), workId));
    }

    @Override
    @PutMapping
    public ApiResult<WorkspaceResponse> save(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long workId,
            @RequestBody SaveWorkspaceRequest request) {
        return ApiResult.success(workspaceService.save(user.id(), workId, request));
    }
}
