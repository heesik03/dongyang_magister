package com.magister.backend.domain.work.docs;

import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.domain.work.dto.CreateWorkRequest;
import com.magister.backend.domain.work.dto.UpdateWorkRequest;
import com.magister.backend.domain.work.dto.WorkResponse;
import com.magister.backend.global.pagination.OffsetPageResponse;
import com.magister.backend.global.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;

@Tag(name = "Work", description = "작품별 설정집·원고·AI 검토를 묶는 창작 작업 단위 관리")
@SecurityRequirement(name = "session")
public interface WorkControllerDocs {

    @Operation(
            summary = "작품 생성",
            description =
                    """
                    **목적**

                    설정집과 회차 원고를 작품별로 분리하여 관리할 창작 작업 공간을 만듭니다.

                    **제공 기능**

                    제목·장르·시대 배경·작품 소개를 등록하고 생성된 작품 정보를 반환합니다.

                    **도메인 정책**

                    - 작품 소유자는 현재 로그인한 사용자로 지정됩니다. 요청 본문으로 소유자를 지정할 수 없습니다.
                    - 작품과 하위 자료의 API 접근은 소유자로 제한됩니다.
                    - 제목은 100자, 장르는 30자까지 필수 입력입니다. 시대 배경은 100자, 소개는 500자까지이며 빈 문자열을 허용합니다.

                    **응답**

                    201과 작품 정보를 반환하며, Location 헤더에 작품 상세 조회 주소를 제공합니다.

                    **인증·요청 조건**

                    로그인 세션과 CSRF 헤더가 필요합니다. CSRF 토큰은 GET /api/user/csrf에서 조회합니다.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "작품 생성 성공; Location 헤더에 상세 조회 주소 제공",
                useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "필수값 누락, 길이 제한 초과 또는 JSON 형식 오류",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "401",
                description = "로그인 필요 또는 세션 만료",
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
    ResponseEntity<ApiResult<WorkResponse>> create(
            LoginUser user, @Valid CreateWorkRequest request);

    @Operation(
            summary = "내 작품 목록",
            description =
                    """
                    **목적**

                    작업을 이어갈 작품을 찾고 최근 편집한 작품부터 확인합니다.

                    **제공 기능**

                    로그인한 사용자의 작품을 제목 검색과 장르 필터로 조회합니다.

                    **도메인 정책**

                    - 다른 사용자의 작품은 목록에 포함되지 않습니다.
                    - 검색어와 장르는 앞뒤 공백을 제거하여 조회하며, 빈 문자열이면 해당 조건을 적용하지 않습니다.
                    - page는 0부터 시작하고 기본값은 0입니다. size는 1~100이며 기본값은 20입니다.
                    - 최근 수정 시각 내림차순, 같은 시각이면 ID 내림차순으로 정렬합니다.
                    - 조건에 맞는 작품이 없으면 빈 목록을 반환합니다.

                    **응답**

                    200과 작품 목록·페이지 정보를 반환합니다.

                    **인증·요청 조건**

                    로그인 세션이 필요합니다.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "본인 작품 목록과 페이지 정보 조회 성공",
                useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "검색어 100자·장르 30자 초과 또는 페이지 범위 오류",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "401",
                description = "로그인 필요 또는 세션 만료",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class)))
    })
    ApiResult<OffsetPageResponse<WorkResponse>> list(
            LoginUser user,
            @Size(max = 100) String keyword,
            @Size(max = 30) String genre,
            @Min(0) int page,
            @Min(1) @Max(100) int size);

    @Operation(
            summary = "작품 상세",
            description =
                    """
                    **목적**

                    선택한 작품의 기본 정보를 확인하고 편집에 사용할 현재 버전을 가져옵니다.

                    **제공 기능**

                    작품 제목·장르·시대 배경·소개와 버전 정보를 조회합니다.

                    **도메인 정책**

                    - 본인이 소유한 작품만 조회할 수 있습니다.
                    - 작품이 없거나 다른 사용자의 작품이면 모두 404를 반환합니다.
                    - 응답의 version은 작품 기본 정보를 수정할 때 전달할 버전입니다.

                    **응답**

                    200과 작품 정보를 반환합니다. 설정집과 원고 문서는 각각의 조회 API로 확인합니다.

                    **인증·요청 조건**

                    로그인 세션이 필요합니다.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "작품 기본 정보와 버전 조회 성공",
                useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "작품 ID 형식 또는 양수 조건 오류",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "401",
                description = "로그인 필요 또는 세션 만료",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "404",
                description = "작품 없음 또는 접근 불가",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class)))
    })
    ApiResult<WorkResponse> get(LoginUser user, @Positive Long workId);

    @Operation(
            summary = "작품 수정",
            description =
                    """
                    **목적**

                    작품의 기본 정보를 변경하면서 다른 편집에서 저장한 내용을 덮어쓰지 않도록 보호합니다.

                    **제공 기능**

                    제목·장르·시대 배경·소개 전체와 조회 당시의 version을 전달하여 수정합니다.

                    **도메인 정책**

                    - 본인이 소유한 작품만 수정할 수 있습니다.
                    - 일부 필드만 보내는 부분 수정이 아닙니다. 시대 배경과 소개를 비우려면 빈 문자열을 전달합니다.
                    - version이 현재 작품 버전과 다르거나 동시 수정 충돌이 발생하면 409를 반환합니다.
                    - 설정집·작업공간 저장으로도 작품 버전이 바뀔 수 있습니다. 충돌 시 작품을 다시 조회해 변경 내용을 확인해야 합니다.

                    **응답**

                    200과 수정된 작품 정보·최신 버전을 반환합니다.

                    **인증·요청 조건**

                    로그인 세션과 CSRF 헤더가 필요합니다.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "작품 기본 정보 수정 성공",
                useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "필수값·길이·작품 ID·version 또는 JSON 형식 오류",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "401",
                description = "로그인 필요 또는 세션 만료",
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
                responseCode = "404",
                description = "작품 없음 또는 접근 불가",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "409",
                description = "작품 수정 버전 충돌",
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
    ApiResult<WorkResponse> update(
            LoginUser user, @Positive Long workId, @Valid UpdateWorkRequest request);
}
