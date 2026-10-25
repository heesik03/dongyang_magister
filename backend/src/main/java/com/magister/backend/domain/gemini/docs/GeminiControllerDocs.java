package com.magister.backend.domain.gemini.docs;

import com.magister.backend.domain.gemini.dto.GeminiRequest;
import com.magister.backend.domain.gemini.dto.GeminiResponse;
import com.magister.backend.global.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

@Tag(name = "Gemini", description = "로컬 개발용 Gemini 연결 확인 API")
@SecurityRequirement(name = "session")
public interface GeminiControllerDocs {

    @Operation(
            summary = "Gemini 텍스트 생성",
            description = "입력 최대 2,000자, 출력 최대 1,024토큰. 동시 1건, 호출 간격 최소 5초. 실제 Gemini API를 호출합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "텍스트 생성 성공", useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "입력 검증 또는 JSON 형식 오류",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "429",
                description = "로컬 호출 빈도 또는 동시 호출 제한 초과",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "502",
                description = "Gemini 인증 실패, 요청 거절 또는 빈 응답",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "503",
                description = "Gemini 호출 제한 또는 서버 오류",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "504",
                description = "Gemini 타임아웃",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "500",
                description = "예상하지 못한 서버 오류",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class)))
    })
    ApiResult<GeminiResponse> generate(@Valid GeminiRequest request);
}
