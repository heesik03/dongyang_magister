package com.magister.backend.domain.user.controller;

import com.magister.backend.domain.user.docs.UserControllerDocs;
import com.magister.backend.domain.user.converter.UserConverter;
import com.magister.backend.domain.user.dto.CsrfResponse;
import com.magister.backend.domain.user.dto.EmailCheckRequest;
import com.magister.backend.domain.user.dto.EmailCheckResponse;
import com.magister.backend.domain.user.dto.LoginRequest;
import com.magister.backend.domain.user.dto.SignupRequest;
import com.magister.backend.domain.user.dto.UserResponse;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.domain.user.security.SessionAuthenticationService;
import com.magister.backend.domain.user.service.UserService;
import com.magister.backend.global.response.ApiResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController implements UserControllerDocs {
    private final UserService users;
    private final SessionAuthenticationService sessions;

    @Override
    @PostMapping("/email/check")
    public ApiResult<EmailCheckResponse> checkEmail(@RequestBody EmailCheckRequest request) {
        return ApiResult.success(UserConverter.toEmailCheckResponse(users.isEmailAvailable(request.email())));
    }

    @Override
    @PostMapping
    public ResponseEntity<ApiResult<UserResponse>> signup(@RequestBody SignupRequest request) {
        return ResponseEntity.created(URI.create("/api/user/me"))
                .body(ApiResult.success(users.signup(request)));
    }

    @Override
    @GetMapping("/csrf")
    public ApiResult<CsrfResponse> csrf(CsrfToken token) {
        return ApiResult.success(UserConverter.toCsrfResponse(token));
    }

    @Override
    @PostMapping("/login")
    public ApiResult<UserResponse> login(
            @RequestBody LoginRequest input,
            HttpServletRequest request,
            HttpServletResponse response) {
        return ApiResult.success(sessions.login(input, request, response));
    }

    @Override
    @GetMapping("/me")
    public ApiResult<UserResponse> me(@AuthenticationPrincipal LoginUser user) {
        return ApiResult.success(UserConverter.toResponse(user));
    }
}
