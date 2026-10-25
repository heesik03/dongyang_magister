package com.magister.backend.domain.user.controller;

import com.magister.backend.domain.user.docs.UserControllerDocs;
import com.magister.backend.domain.user.dto.CsrfResponse;
import com.magister.backend.domain.user.dto.EmailCheckRequest;
import com.magister.backend.domain.user.dto.EmailCheckResponse;
import com.magister.backend.domain.user.dto.LoginRequest;
import com.magister.backend.domain.user.dto.SignupRequest;
import com.magister.backend.domain.user.dto.UserResponse;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.domain.user.security.SessionAuthenticationService;
import com.magister.backend.domain.user.service.UserService;
import com.magister.backend.global.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

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
    public ApiResponse<EmailCheckResponse> checkEmail(
            @Valid @RequestBody EmailCheckRequest request) {
        return ApiResponse.success(new EmailCheckResponse(users.isEmailAvailable(request.email())));
    }

    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> signup(
            @Valid @RequestBody SignupRequest request) {
        return ResponseEntity.created(URI.create("/api/user/me"))
                .body(ApiResponse.success(users.signup(request)));
    }

    @Override
    @GetMapping("/csrf")
    public ApiResponse<CsrfResponse> csrf(CsrfToken token) {
        return ApiResponse.success(new CsrfResponse(token.getHeaderName(), token.getToken()));
    }

    @Override
    @PostMapping("/login")
    public ApiResponse<UserResponse> login(
            @Valid @RequestBody LoginRequest input,
            HttpServletRequest request,
            HttpServletResponse response) {
        return ApiResponse.success(sessions.login(input, request, response));
    }

    @Override
    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal LoginUser user) {
        return ApiResponse.success(new UserResponse(user.id(), user.email(), user.name()));
    }
}
