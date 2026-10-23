package com.magister.backend.domain.user.controller;

import com.magister.backend.domain.user.dto.EmailCheckRequest;
import com.magister.backend.domain.user.dto.LoginRequest;
import com.magister.backend.domain.user.dto.SignupRequest;
import com.magister.backend.domain.user.dto.UserResponse;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.domain.user.security.SessionAuthenticationService;
import com.magister.backend.domain.user.service.UserService;
import com.magister.backend.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;

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
public class UserController {
    private final UserService users;
    private final SessionAuthenticationService sessions;

    @Operation(
            summary = "이메일 중복 확인",
            description = "이메일을 URL에 남기지 않도록 본문으로 전달합니다. 가입 시 중복 여부는 다시 검사합니다.")
    @PostMapping("/email/check")
    public ApiResponse<EmailCheckResponse> checkEmail(
            @Valid @RequestBody EmailCheckRequest request) {
        return ApiResponse.success(new EmailCheckResponse(users.isEmailAvailable(request.email())));
    }

    @Operation(summary = "회원가입")
    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> signup(
            @Valid @RequestBody SignupRequest request) {
        return ResponseEntity.created(URI.create("/api/user/me"))
                .body(ApiResponse.success(users.signup(request)));
    }

    @Operation(
            summary = "CSRF 토큰 조회",
            description = "변경 요청에 headerName의 헤더로 token을 전달합니다. 로그인·로그아웃 후 재조회합니다.")
    @GetMapping("/csrf")
    public ApiResponse<CsrfResponse> csrf(CsrfToken token) {
        return ApiResponse.success(new CsrfResponse(token.getHeaderName(), token.getToken()));
    }

    @Operation(summary = "이메일 로그인", description = "세션 쿠키를 발급합니다. 1시간 미사용 시 만료됩니다.")
    @PostMapping("/login")
    public ApiResponse<UserResponse> login(
            @Valid @RequestBody LoginRequest input,
            HttpServletRequest request,
            HttpServletResponse response) {
        return ApiResponse.success(sessions.login(input, request, response));
    }

    @Operation(summary = "내 정보 조회")
    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal LoginUser user) {
        return ApiResponse.success(new UserResponse(user.id(), user.email(), user.name()));
    }

    public record EmailCheckResponse(boolean available) {}

    public record CsrfResponse(String headerName, String token) {}
}
