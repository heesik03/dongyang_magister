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

@Tag(name = "Gemini", description = "작품 검토와 별도로 Gemini 텍스트 생성 연결을 확인하는 개발용 API")
@SecurityRequirement(name = "session")
public interface GeminiControllerDocs {

    @Operation(
            summary = "Gemini 텍스트 생성",
            description = """
                    **목적**

                    서버에서 Gemini API로 텍스트 생성 요청을 보낼 수 있는지 확인합니다.

                    **제공 기능**

                    prompt를 외부 Gemini API에 전달하고 생성된 텍스트를 동기 응답으로 반환합니다.

                    **도메인 정책**

                    - 작품별 설정 충돌 검토와 별도의 연결 확인 기능입니다. 작품·원고·검토 결과를 저장하지 않습니다.
                    - 입력은 공백만으로 구성할 수 없으며 최대 2,000자입니다. 출력은 최대 1,024토큰으로 요청합니다.
                    - 사용자별 동시 호출은 1건, 호출 시작 간격은 최소 5초입니다. 서버 전체 동시 공급자 호출은 최대 2건이며 AI 검토 작업과 제한을 공유합니다.
                    - 실제 Gemini API를 호출하므로 전송한 내용은 외부 공급자에게 전달되며 비용이 발생할 수 있습니다.
                    - 현재 보안 설정에서는 로그인 사용자가 호출할 수 있으며, 개발용이라는 명칭이 별도 실행 환경 제한을 의미하지는 않습니다.

                    **응답**

                    200과 생성된 텍스트를 반환합니다. 외부 공급자 오류는 응답 코드로 구분합니다.

                    **인증·요청 조건**

                    로그인 세션과 CSRF 헤더가 필요합니다.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Gemini 텍스트 생성 성공",
                useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "prompt 누락·공백·길이 또는 JSON 형식 오류",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "401",
                description = "로그인 필요 또는 세션 만료",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "403",
                description = "CSRF 토큰 누락 또는 검증 실패",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "413",
                description = "요청 본문 8 MiB 초과",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "429",
                description = "사용자별 호출 간격·동시 호출 또는 서버 전체 호출 제한 초과",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "502",
                description = "Gemini 인증 실패·요청 거절 또는 유효하지 않은 응답",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "503",
                description = "Gemini 호출 한도 초과·서비스 장애 또는 통신 실패",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "504",
                description = "Gemini 응답 시간 초과",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "500",
                description = "예상하지 못한 서버 오류",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiResult.class)))
    })
    ApiResult<GeminiResponse> generate(@Valid GeminiRequest request);
}
