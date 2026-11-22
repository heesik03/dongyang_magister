package com.magister.backend.domain.history.docs;

import com.magister.backend.domain.history.dto.HistoryCheck.Finding;
import com.magister.backend.domain.history.dto.HistoryCheck.Request;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.List;

@Tag(name = "역사 고증 자료")
@SecurityRequirement(name = "session")
public interface HistoryCheckControllerDocs {
    @Operation(summary = "원고의 역사 주장 대조", description = """
            **목적**

            현재 장면의 역사적 서술과 공식 자료를 대조합니다.

            **제공 기능**

            원문 인용과 UTF-16 위치(시작 포함·끝 제외), 장면 시점, 대상, 판단 이유와 공식 출처를 반환합니다.

            **도메인 정책**

            입력한 원고 최대 6,000자를 Gemini에 전송하여 최대 3개 주장을 검토합니다.
            역사 기관에는 대상명만 전송합니다. Gemini 텍스트 호출 한도를 요청당 1~2회 사용합니다.

            **응답**

            POSSIBLE_CONFLICT는 충돌 후보, NO_CONFLICT_FOUND는 해당 주장에 대한 충돌 미확인입니다.
            근거 부족은 INSUFFICIENT_EVIDENCE, 기관 조회 실패는 LOOKUP_FAILED입니다. 검토할 주장이 없으면 빈 배열입니다.

            **인증·요청 조건**

            로그인 세션과 CSRF 헤더가 필요합니다.
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "주장별 대조 결과", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "입력값 또는 JSON 형식 오류",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(responseCode = "401", description = "로그인 필요 또는 세션 만료",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(responseCode = "403", description = "CSRF 검증 실패",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(responseCode = "413", description = "요청 본문 크기 초과",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(responseCode = "429", description = "사용자 또는 전체 AI 호출 한도 초과",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(responseCode = "502", description = "Gemini 응답 또는 원문 인용 검증 실패",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(responseCode = "503", description = "Gemini 사용 불가",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(responseCode = "504", description = "Gemini 응답 시간 초과",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResult.class)))
    })
    ApiResult<List<Finding>> check(LoginUser user, @Valid Request request);
}
