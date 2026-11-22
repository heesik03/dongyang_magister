package com.magister.backend.domain.history.controller;

import com.magister.backend.domain.history.dto.HistoryCheck.Finding;
import com.magister.backend.domain.history.dto.HistoryCheck.Request;
import com.magister.backend.domain.history.service.HistoryCheckService;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.domain.history.docs.HistoryCheckControllerDocs;
import com.magister.backend.global.response.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class HistoryCheckController implements HistoryCheckControllerDocs {
    private final HistoryCheckService service;

    @Override
    @PostMapping("/api/history/check")
    public ApiResult<List<Finding>> check(@AuthenticationPrincipal LoginUser user, @RequestBody Request request) {
        return ApiResult.success(service.check(user.id(), request.manuscript()));
    }
}
