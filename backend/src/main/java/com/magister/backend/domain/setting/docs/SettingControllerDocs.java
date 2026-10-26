package com.magister.backend.domain.setting.docs;

import com.magister.backend.domain.setting.dto.CreateSettingRequest;
import com.magister.backend.domain.setting.dto.SettingResponse;
import com.magister.backend.domain.setting.dto.UpdateSettingRequest;
import com.magister.backend.domain.setting.entity.SettingKind;
import com.magister.backend.domain.setting.entity.SettingStatus;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.global.response.ApiResult;
import com.magister.backend.global.pagination.OffsetPageResponse;
import com.magister.backend.global.pagination.CursorPageResponse;

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

import org.springframework.http.ResponseEntity;

@Tag(name = "Setting", description = "작품의 인물·세계관·주요 사건·의도한 각색과 확정 여부를 관리하는 설정집")
@SecurityRequirement(name = "session")
public interface SettingControllerDocs {
    @Operation(summary = "설정 커서 목록", description = "소유자 전용 ID 내림차순 조회. size는 1~100이며 다른 작품·필터의 커서는 400으로 거절합니다. 다음 페이지는 nextCursor를 사용합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "설정 목록과 hasNext, nextCursor"),
        @ApiResponse(responseCode = "400", description = "유효하지 않은 커서 또는 페이지 크기"),
        @ApiResponse(responseCode = "401", description = "로그인 필요"),
        @ApiResponse(responseCode = "404", description = "소유한 작품 없음")
    })
    ApiResult<CursorPageResponse<SettingResponse>> scroll(LoginUser user, @Positive Long workId,
            SettingKind kind, SettingStatus status, String cursor, @Min(1) @Max(100) int size);


    @Operation(
            summary = "설정 등록",
            description = """
                    **목적**

                    작품의 인물과 세계관 정보를 설정집에 기록하여 이후 원고 검토에 사용할 기준을 관리합니다.

                    **제공 기능**

                    제목·내용·종류·상태를 지정해 설정을 등록합니다.

                    **도메인 정책**

                    - 본인이 소유한 작품에만 등록할 수 있습니다.
                    - 종류는 인물(CHARACTER), 세계관(WORLD), 주요 사건(MAJOR_EVENT), 의도한 각색(INTENDED_ADAPTATION)입니다.
                    - 상태는 확정(CONFIRMED), 아이디어(IDEA), AI 후보(AI_CANDIDATE)입니다.
                    - AI 검토의 근거로 선택할 수 있는 설정은 확정 상태뿐입니다. 아이디어와 AI 후보는 확정 상태로 변경한 뒤 사용할 수 있습니다.
                    - 제목은 100자, 내용은 10,000자까지이며 둘 다 필수입니다. 설정 등록 시 작품의 수정 정보도 갱신합니다.

                    **응답**

                    201과 등록된 설정을 반환하며, Location 헤더에 설정 상세 조회 주소를 제공합니다.

                    **인증·요청 조건**

                    로그인 세션과 CSRF 헤더가 필요합니다.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "설정 등록 성공; Location 헤더에 상세 조회 주소 제공",
                useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "필수값·종류·상태·길이·작품 ID 또는 JSON 형식 오류",
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
                responseCode = "404",
                description = "작품 없음 또는 접근 불가",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "413",
                description = "요청 본문 8 MiB 초과",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiResult.class)))
    })
    ResponseEntity<ApiResult<SettingResponse>> create(
            LoginUser user, @Positive Long workId, @Valid CreateSettingRequest request);

    @Operation(
            summary = "설정 목록",
            description = """
                    **목적**

                    작품의 설정집을 살펴보고 원고 검토에 참고할 확정 설정을 찾습니다.

                    **제공 기능**

                    종류(kind)와 상태(status)를 선택적으로 지정하여 설정 목록을 조회합니다.

                    **도메인 정책**

                    - 본인 작품의 설정만 조회합니다. 설정이 없어도 작품 소유권을 먼저 확인합니다.
                    - 필터를 생략하면 해당 종류·상태를 제한하지 않습니다. CONFIRMED로 필터하면 확정 설정만 조회합니다.
                    - page는 0부터 시작하고 기본값은 0입니다. size는 1~100이며 기본값은 20입니다.
                    - ID 내림차순으로 정렬하며, 조건에 맞는 설정이 없으면 빈 목록을 반환합니다.

                    **응답**

                    200과 설정 목록·페이지 정보를 반환합니다.

                    **인증·요청 조건**

                    로그인 세션이 필요합니다.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "설정 목록과 페이지 정보 조회 성공",
                useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "작품 ID·종류·상태 또는 페이지 범위 오류",
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
                responseCode = "404",
                description = "작품 없음 또는 접근 불가",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiResult.class)))
    })
    ApiResult<OffsetPageResponse<SettingResponse>> list(
            LoginUser user,
            @Positive Long workId,
            SettingKind kind,
            SettingStatus status,
            @Min(0) int page,
            @Min(1) @Max(100) int size);

    @Operation(
            summary = "설정 상세",
            description = """
                    **목적**

                    설정의 구체적인 내용과 확정 여부를 확인하고 수정에 필요한 버전을 가져옵니다.

                    **제공 기능**

                    설정 제목·내용·종류·상태와 버전 정보를 조회합니다.

                    **도메인 정책**

                    - 본인 작품에 속한 설정만 반환합니다. 다른 작품의 설정 ID를 전달해도 조회할 수 없습니다.
                    - 작품·설정이 없거나 접근할 수 없으면 404를 반환합니다.
                    - 응답의 version은 해당 설정을 수정할 때 전달할 버전입니다.

                    **응답**

                    200과 설정 정보를 반환합니다.

                    **인증·요청 조건**

                    로그인 세션이 필요합니다.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "설정 내용·상태·버전 조회 성공",
                useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "작품·설정 ID 형식 또는 양수 조건 오류",
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
                responseCode = "404",
                description = "작품·설정 없음 또는 접근 불가",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiResult.class)))
    })
    ApiResult<SettingResponse> get(
            LoginUser user, @Positive Long workId, @Positive Long settingId);

    @Operation(
            summary = "설정 수정",
            description = """
                    **목적**

                    작품 설정을 보완하거나 아이디어·AI 후보를 확정하여 이후 원고 검토의 기준을 조정합니다.

                    **제공 기능**

                    제목·내용·종류·상태 전체와 조회 당시의 version을 전달하여 수정합니다.

                    **도메인 정책**

                    - 본인 작품에 속한 설정만 수정할 수 있습니다.
                    - 확정(CONFIRMED)으로 변경하면 이후 검토에서 선택할 수 있습니다. 확정 이외 상태는 검토 근거로 선택할 수 없습니다.
                    - 설정을 변경해도 이미 생성된 검토 결과를 다시 분석하지 않습니다. 바뀐 기준으로 검토하려면 새 분석을 요청해야 합니다.
                    - version이 현재 설정 버전과 다르거나 동시 수정 충돌이 발생하면 409를 반환합니다.
                    - 수정 시 작품의 수정 정보도 갱신합니다.

                    **응답**

                    200과 수정된 설정 정보·최신 버전을 반환합니다.

                    **인증·요청 조건**

                    로그인 세션과 CSRF 헤더가 필요합니다.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "설정 내용·상태 수정 성공",
                useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "필수값·종류·상태·길이·ID·version 또는 JSON 형식 오류",
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
                responseCode = "404",
                description = "작품·설정 없음 또는 접근 불가",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "409",
                description = "설정 또는 작품의 수정 버전 충돌",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "413",
                description = "요청 본문 8 MiB 초과",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiResult.class)))
    })
    ApiResult<SettingResponse> update(
            LoginUser user,
            @Positive Long workId,
            @Positive Long settingId,
            @Valid UpdateSettingRequest request);
}
