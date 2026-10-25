package com.magister.backend.domain.user.docs;

import com.magister.backend.domain.user.dto.*;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.global.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;

@Tag(name = "User", description = "회원가입 및 세션 기반 인증 API")
public interface UserControllerDocs {
    @Operation(summary = "이메일 중복 확인", description = "이메일은 본문으로 전달하며 가입 시 다시 검사합니다. CSRF 헤더 필요.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "입력값 또는 JSON 형식 오류",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "403",
                description = "CSRF 토큰 누락 또는 만료",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class)))
    })
    ApiResult<EmailCheckResponse> checkEmail(@Valid EmailCheckRequest request);

    @Operation(
            summary = "회원가입",
            description = "이메일·필명·비밀번호를 등록합니다. 비밀번호는 영문·숫자·특수문자 조합 8~128자. CSRF 헤더 필요.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "회원가입 성공", useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "입력값 또는 JSON 형식 오류",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "403",
                description = "CSRF 토큰 누락 또는 만료",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "409",
                description = "이메일 중복",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class)))
    })
    ResponseEntity<ApiResult<UserResponse>> signup(@Valid SignupRequest request);

    @Operation(
            summary = "CSRF 토큰 조회",
            description = "응답 headerName의 헤더에 token을 전달합니다. 로그인·로그아웃 후 재조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true)
    })
    ApiResult<CsrfResponse> csrf(CsrfToken token);

    @Operation(summary = "이메일 로그인", description = "세션 ID를 변경하며 1시간 미사용 시 만료됩니다. CSRF 헤더 필요.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "입력값 또는 JSON 형식 오류",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "401",
                description = "미인증 또는 로그인 실패",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "403",
                description = "CSRF 토큰 누락 또는 만료",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class)))
    })
    ApiResult<UserResponse> login(
            @Valid LoginRequest input, HttpServletRequest request, HttpServletResponse response);

    @SecurityRequirement(name = "session")
    @Operation(summary = "내 정보 조회", description = "인증된 세션의 사용자 정보만 반환합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "401",
                description = "미인증 또는 로그인 실패",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class)))
    })
    ApiResult<UserResponse> me(LoginUser user);
}
