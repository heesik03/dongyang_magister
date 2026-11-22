package com.magister.backend.domain.history.docs;

import com.magister.backend.domain.history.dto.HistoryCandidate;
import com.magister.backend.domain.history.dto.HistoryEvidence;
import com.magister.backend.domain.history.dto.HistoryProvider;
import com.magister.backend.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

@Tag(name = "역사 고증 자료")
@SecurityRequirement(name = "session")
public interface HistoryEvidenceControllerDocs {
    @Operation(summary = "공식 자료 후보 검색", description = """
            로그인 후 ENCYCLOPEDIA(백과사전) 또는 HERITAGE(국가유산청)와 검색어를 지정합니다.
            예: HERITAGE, 경복궁. 첫 페이지에서 정확한 이름을 우선해 최대 10건을 반환합니다.
            검색 결과가 없으면 빈 배열입니다.
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "공식 자료 후보 목록", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "입력값 또는 JSON 형식 오류",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(responseCode = "401", description = "로그인 필요 또는 세션 만료",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(responseCode = "502", description = "공식 자료 조회 실패",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(responseCode = "503", description = "백과사전 인증키 미설정",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResult.class)))
    })
    ApiResult<List<HistoryCandidate>> candidates(HistoryProvider provider, @NotBlank @Size(max = 100) String q);

    @Operation(summary = "선택한 자료의 근거 대목 조회", description = """
            로그인 후 검색한 후보의 sourceId와 원문에서 찾을 표현을 입력합니다.
            예: HERITAGE, 13-0001170000000-11, 1395. 첫 일치 문장을 최대 500자로 발췌합니다.
            자료에 해당 표현이 없으면 NO_PASSAGE입니다.
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "공식 출처와 발췌 결과", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "입력값 또는 JSON 형식 오류",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(responseCode = "401", description = "로그인 필요 또는 세션 만료",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(responseCode = "404", description = "자료 없음",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(responseCode = "502", description = "공식 자료 조회 실패",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(responseCode = "503", description = "백과사전 인증키 미설정",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResult.class)))
    })
    ApiResult<HistoryEvidence> evidence(HistoryProvider provider, @Size(max = 64) String sourceId,
            @NotBlank @Size(max = 100) String keyword);
}
