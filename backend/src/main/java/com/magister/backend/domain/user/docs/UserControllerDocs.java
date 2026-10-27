package com.magister.backend.domain.user.docs;

import com.magister.backend.domain.user.dto.CsrfResponse;
import com.magister.backend.domain.user.dto.EmailCheckRequest;
import com.magister.backend.domain.user.dto.EmailCheckResponse;
import com.magister.backend.domain.user.dto.LoginRequest;
import com.magister.backend.domain.user.dto.SignupRequest;
import com.magister.backend.domain.user.dto.UserResponse;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.global.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;

@Tag(name = "User", description = "작가 계정 생성과 세션 기반 로그인·CSRF 토큰 관리")
public interface UserControllerDocs {

    @Operation(
            summary = "이메일 중복 확인",
            description =
                    """
                    **목적**

                    회원가입 전에 입력한 이메일로 계정을 만들 수 있는지 안내합니다.

                    **제공 기능**

                    이메일을 요청 본문으로 받아 사용 가능 여부(available)를 반환합니다.

                    **도메인 정책**

                    - 이메일은 앞뒤 공백을 제거하고 소문자로 변환하여 비교합니다. 대소문자가 달라도 같은 이메일로 취급합니다.
                    - 사전 확인은 이메일을 예약하지 않습니다. 실제 회원가입 시 중복 여부를 다시 검사하므로 이후 가입 요청이 409로 실패할 수 있습니다.
                    - 이메일은 필수이며 이메일 형식과 최대 254자 제한을 적용합니다.

                    **응답**

                    200과 사용 가능 여부를 반환합니다. 이미 가입된 이메일도 조회 성공이며 available=false입니다.

                    **인증·요청 조건**

                    로그인 없이 사용할 수 있지만 CSRF 헤더가 필요합니다. 먼저 GET /api/user/csrf를 호출하고 동일한 세션을 유지해야 합니다.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "429", description = "인증 시도 또는 해싱 동시 작업 한도 초과"),
        @ApiResponse(
                responseCode = "200",
                description = "이메일 사용 가능 여부 확인 성공",
                useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "이메일 누락·형식·길이 또는 JSON 형식 오류",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "403",
                description = "CSRF 토큰 누락 또는 검증 실패",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "413",
                description = "요청 본문 8 MiB 초과",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class)))
    })
    ApiResult<EmailCheckResponse> checkEmail(@Valid EmailCheckRequest request);

    @Operation(
            summary = "회원가입",
            description =
                    """
                    **목적**

                    작품과 원고를 본인 계정에 보관할 수 있도록 작가 계정을 생성합니다.

                    **제공 기능**

                    이메일·필명(name)·비밀번호를 등록하고 생성된 사용자 정보를 반환합니다.

                    **도메인 정책**

                    - 이메일은 앞뒤 공백을 제거하고 소문자로 저장하며, 동일한 이메일의 중복 가입을 허용하지 않습니다.
                    - 이메일은 최대 254자, 필명은 앞뒤 공백을 제거한 뒤 최대 30자이며 둘 다 필수입니다.
                    - 비밀번호는 공백 없이 영문·숫자·특수문자를 포함한 8~128자입니다. 해시로 저장하며 응답에는 포함하지 않습니다.
                    - 사전 중복 확인을 통과해도 동시 가입으로 중복될 수 있습니다. 최종 저장 단계에서도 이메일 유일성을 확인합니다.
                    - 회원가입은 로그인 세션을 생성하지 않습니다. 작품 기능을 사용하려면 별도로 로그인해야 합니다.

                    **응답**

                    201과 사용자 ID·이메일·필명을 반환합니다. Location은 /api/user/me이며, 해당 조회에는 로그인이 필요합니다.

                    **인증·요청 조건**

                    로그인 없이 사용할 수 있지만 CSRF 헤더가 필요합니다.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "429", description = "인증 시도 또는 해싱 동시 작업 한도 초과"),
        @ApiResponse(
                responseCode = "201",
                description = "회원가입 성공; 사용자 정보 반환",
                useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "이메일·필명·비밀번호 검증 또는 JSON 형식 오류",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "403",
                description = "CSRF 토큰 누락 또는 검증 실패",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "409",
                description = "이미 가입된 이메일",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "413",
                description = "요청 본문 8 MiB 초과",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class)))
    })
    ResponseEntity<ApiResult<UserResponse>> signup(@Valid SignupRequest request);

    @Operation(
            summary = "CSRF 토큰 조회",
            description =
                    """
                    **목적**

                    회원가입·로그인·작품 저장 등 상태를 변경하는 요청을 현재 세션에서 보낼 수 있도록 CSRF 토큰을 제공합니다.

                    **제공 기능**

                    토큰을 전달할 헤더 이름(headerName)과 토큰 값(token)을 반환합니다.

                    **도메인 정책**

                    - 로그인 전에도 조회할 수 있습니다. 응답 이후 같은 세션 쿠키를 유지하여 후속 요청을 보내야 합니다.
                    - 상태 변경 요청에는 headerName에 지정된 헤더로 token을 전달합니다.
                    - 로그인 시 기존 토큰이 폐기되고 로그아웃 시 세션이 무효화되므로 로그인·로그아웃 후 다시 조회해야 합니다.

                    **응답**

                    200과 CSRF 헤더 이름·토큰을 반환합니다.

                    **인증·요청 조건**

                    로그인과 CSRF 헤더 없이 조회할 수 있습니다.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "현재 세션에서 사용할 CSRF 토큰 조회 성공",
                useReturnTypeSchema = true)
    })
    ApiResult<CsrfResponse> csrf(CsrfToken token);

    @Operation(
            summary = "이메일 로그인",
            description =
                    """
                    **목적**

                    본인 계정으로 인증하여 작품·설정집·원고에 접근할 세션을 만듭니다.

                    **제공 기능**

                    이메일과 비밀번호를 확인하고 사용자 정보와 인증된 세션을 제공합니다.

                    **도메인 정책**

                    - 이메일은 앞뒤 공백을 제거하고 소문자로 변환하여 인증합니다.
                    - 성공 시 세션 ID를 변경하고 기존 CSRF 토큰을 폐기합니다. 후속 저장 요청 전에 CSRF 토큰을 다시 조회해야 합니다.
                    - 이후 인증에는 JSESSIONID 세션 쿠키를 사용합니다. 기본 설정에서는 1시간 미사용 시 세션이 만료됩니다.
                    - 계정이 없거나 비밀번호가 틀린 경우 동일한 로그인 실패 응답을 반환합니다.

                    **응답**

                    200과 사용자 ID·이메일·필명을 반환합니다. 클라이언트는 세션 쿠키를 유지해야 합니다.

                    **인증·요청 조건**

                    로그인 전에도 사용할 수 있지만 CSRF 헤더가 필요합니다.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "429", description = "인증 시도 또는 해싱 동시 작업 한도 초과"),
        @ApiResponse(
                responseCode = "200",
                description = "로그인 성공; 사용자 정보와 인증된 세션 제공",
                useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "이메일·비밀번호 검증 또는 JSON 형식 오류",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "401",
                description = "이메일 또는 비밀번호 불일치",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "403",
                description = "CSRF 토큰 누락 또는 검증 실패",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "413",
                description = "요청 본문 8 MiB 초과",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class)))
    })
    ApiResult<UserResponse> login(
            @Valid LoginRequest input, HttpServletRequest request, HttpServletResponse response);

    @SecurityRequirement(name = "session")
    @Operation(
            summary = "내 정보 조회",
            description =
                    """
                    **목적**

                    현재 로그인한 작가를 확인하고 화면의 계정 정보를 표시합니다.

                    **제공 기능**

                    인증된 세션의 사용자 ID·이메일·필명을 조회합니다.

                    **도메인 정책**

                    - 조회 대상은 요청한 사용자 ID가 아니라 현재 세션의 사용자입니다.
                    - 다른 사용자의 정보를 조회하는 기능을 제공하지 않으며 비밀번호는 응답하지 않습니다.
                    - 세션에 보관된 사용자 정보를 반환합니다.

                    **응답**

                    200과 현재 사용자 정보를 반환합니다.

                    **인증·요청 조건**

                    로그인 세션이 필요합니다.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "현재 로그인 사용자 정보 조회 성공",
                useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "401",
                description = "로그인 필요 또는 세션 만료",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class)))
    })
    ApiResult<UserResponse> me(LoginUser user);
}
