package com.magister.backend.domain.work.docs;

import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.domain.work.dto.*;
import com.magister.backend.global.response.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.http.ResponseEntity;

@Tag(name = "Work")
@SecurityRequirement(name = "session")
public interface WorkControllerDocs {
    @Operation(summary = "작품 생성", description = "작품 소유자는 현재 로그인 사용자입니다. CSRF 헤더가 필요합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "성공",
                useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "입력값·페이지 범위 오류",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "401",
                description = "로그인 필요",
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
                responseCode = "404",
                description = "리소스 없음 또는 접근 불가",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class)))
    })
    ResponseEntity<ApiResult<WorkResponse>> create(
            LoginUser user, @Valid CreateWorkRequest request);

    @Operation(
            summary = "내 작품 목록",
            description = "제목 검색·장르 필터. page는 0부터, size는 1~100. 최근 수정·ID 내림차순.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "성공",
                useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "입력값·페이지 범위 오류",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "401",
                description = "로그인 필요",
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
                responseCode = "404",
                description = "리소스 없음 또는 접근 불가",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class)))
    })
    ApiResult<PageResponse<WorkResponse>> list(
            LoginUser user,
            @Size(max = 100) String keyword,
            @Size(max = 30) String genre,
            @Min(0) int page,
            @Min(1) @Max(100) int size);

    @Operation(summary = "작품 상세", description = "본인 작품만 조회할 수 있습니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "성공",
                useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "입력값·페이지 범위 오류",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "401",
                description = "로그인 필요",
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
                responseCode = "404",
                description = "리소스 없음 또는 접근 불가",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class)))
    })
    ApiResult<WorkResponse> get(LoginUser user, @Positive Long workId);

    @Operation(summary = "작품 수정", description = "전체 기본 정보와 조회 시 받은 version을 전달합니다. CSRF 헤더가 필요합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "성공",
                useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "입력값·페이지 범위 오류",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "401",
                description = "로그인 필요",
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
                responseCode = "404",
                description = "리소스 없음 또는 접근 불가",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "409",
                description = "수정 버전 충돌",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class)))
    })
    ApiResult<WorkResponse> update(
            LoginUser user, @Positive Long workId, @Valid UpdateWorkRequest request);
}
