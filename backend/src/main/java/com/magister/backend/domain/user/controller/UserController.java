package com.magister.backend.domain.user.controller;

import com.magister.backend.domain.user.converter.UserConverter;
import com.magister.backend.domain.user.docs.UserControllerDocs;
import com.magister.backend.domain.user.dto.ChangePasswordRequest;
import com.magister.backend.domain.user.dto.CsrfResponse;
import com.magister.backend.domain.user.dto.EmailCheckRequest;
import com.magister.backend.domain.user.dto.EmailCheckResponse;
import com.magister.backend.domain.user.dto.LoginRequest;
import com.magister.backend.domain.user.dto.SignupRequest;
import com.magister.backend.domain.user.dto.UpdateProfileRequest;
import com.magister.backend.domain.user.dto.UserActivityResponse;
import com.magister.backend.domain.user.dto.UserResponse;
import com.magister.backend.domain.user.dto.WithdrawUserRequest;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.domain.user.security.SessionAuthenticationService;
import com.magister.backend.domain.user.service.UserService;
import com.magister.backend.domain.user.service.UserWithdrawalService;
import com.magister.backend.global.response.ApiResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/user")
public class UserController implements UserControllerDocs {
    private final UserService userService;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final UserWithdrawalService userWithdrawalService;

    @Autowired
    public UserController(
            UserService userService,
            SessionAuthenticationService sessionAuthenticationService,
            UserWithdrawalService userWithdrawalService) {
        this.userService = userService;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.userWithdrawalService = userWithdrawalService;
    }

    public UserController(
            UserService userService, SessionAuthenticationService sessionAuthenticationService) {
        this(userService, sessionAuthenticationService, null);
    }

    @Override
    @PostMapping("/email/check")
    public ApiResult<EmailCheckResponse> checkEmail(@RequestBody EmailCheckRequest request) {
        return ApiResult.success(
                UserConverter.toEmailCheckResponse(userService.isEmailAvailable(request.email())));
    }

    @Override
    @PostMapping
    public ResponseEntity<ApiResult<UserResponse>> signup(@RequestBody SignupRequest request) {
        return ResponseEntity.created(URI.create("/api/user/me"))
                .body(ApiResult.success(userService.signup(request)));
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
        return ApiResult.success(sessionAuthenticationService.login(input, request, response));
    }

    @Override
    @GetMapping("/me")
    public ApiResult<UserResponse> me(@AuthenticationPrincipal LoginUser user) {
        return ApiResult.success(UserConverter.toResponse(user));
    }

    @Override
    @GetMapping("/me/activity")
    public ApiResult<UserActivityResponse> activity(@AuthenticationPrincipal LoginUser user) {
        return ApiResult.success(userService.getActivity(user.id()));
    }

    @Override
    @PatchMapping("/me/profile")
    public ApiResult<UserResponse> updateProfile(
            @AuthenticationPrincipal LoginUser user,
            @RequestBody UpdateProfileRequest input,
            HttpServletRequest request,
            HttpServletResponse response) {
        LoginUser updated = userService.updateProfile(user.id(), input);
        sessionAuthenticationService.updateSessionPrincipal(updated, request, response);
        return ApiResult.success(UserConverter.toResponse(updated));
    }

    @Override
    @PostMapping("/me/password")
    public ApiResult<Void> changePassword(
            @AuthenticationPrincipal LoginUser user,
            @RequestBody ChangePasswordRequest input,
            HttpServletRequest request,
            HttpServletResponse response) {
        userService.changePassword(user.id(), input);
        sessionAuthenticationService.invalidateAllSessions(user.id(), request, response);
        return ApiResult.success(null);
    }

    @Override
    @PostMapping("/me/withdraw")
    public ApiResult<Void> withdraw(
            @AuthenticationPrincipal LoginUser user,
            @RequestBody WithdrawUserRequest input,
            HttpServletRequest request,
            HttpServletResponse response) {
        userWithdrawalService.withdraw(user.id(), input);
        sessionAuthenticationService.invalidateAllSessions(user.id(), request, response);
        return ApiResult.success(null);
    }
}
