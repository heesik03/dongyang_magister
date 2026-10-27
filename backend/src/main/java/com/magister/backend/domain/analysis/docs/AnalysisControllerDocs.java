package com.magister.backend.domain.analysis.docs;

import com.magister.backend.domain.analysis.dto.AnalysisJobResponse;
import com.magister.backend.domain.analysis.dto.AnalysisJobSummary;
import com.magister.backend.domain.analysis.dto.AnalysisPreviewResponse;
import com.magister.backend.domain.analysis.dto.AnalysisRequest;
import com.magister.backend.domain.analysis.dto.AnalysisSnapshotResponse;
import com.magister.backend.domain.analysis.dto.AnalysisSourceResponse;
import com.magister.backend.domain.analysis.dto.ReviewReportResponse;
import com.magister.backend.domain.analysis.entity.AnalysisJobStatus;
import com.magister.backend.domain.user.security.LoginUser;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;

@Tag(name = "Analysis", description = "저장된 회차 원고와 확정 설정을 비교하는 비동기 AI 검토")
@SecurityRequirement(name = "session")
public interface AnalysisControllerDocs {
    @Operation(
            summary = "근거가 연결된 검토 후보",
            description =
                    "본인 작업만 조회. 후보·다중 근거·UTF-16 위치와 legacy/stale 반환. page=0,size=20(최대100)."
                        + " 200/400/401/404/409. 과거 결과의 없는 위치는 추정하지 않습니다.")
    ApiResult<ReviewReportResponse> reviews(
            LoginUser user,
            @Positive Long workId,
            @NotBlank @Size(max = 36) String id,
            @Min(0) int page,
            @Min(1) @Max(100) int size);

    @Operation(
            summary = "분석 당시 근거 원문",
            description =
                    "본인 작업에 속한 출처의 고정 원문·제목·버전 반환. 인용 위치는 UTF-16 [startOffset,endOffset)."
                        + " 200/400/401/404.")
    ApiResult<AnalysisSourceResponse> source(
            LoginUser user,
            @Positive Long workId,
            @NotBlank @Size(max = 36) String id,
            @Positive Long sourceId);

    @Operation(
            summary = "AI 전송 자료 미리보기",
            description =
                    "AI 호출·작업 생성 없이 실제 전송할 원고·확정 설정·이전 회차·같은 회차의 저장 판단과 정책을 반환합니다."
                        + " requestId·chapterId·workspaceVersion·settingIds·previousIds·consistency·motivation을"
                        + " 보내며 chapterRevision을 권장합니다. 반환한 previewToken은 사용자·작품·자료·정책·모델에 묶여 10분"
                        + " 동안 유효합니다. CSRF 필요. 200/400/401/403/404/409.")
    ApiResult<AnalysisPreviewResponse> preview(
            LoginUser user, @Positive Long workId, @Valid AnalysisRequest request);

    @Operation(
            summary = "분석 작업 이력",
            description =
                    "원고·결과 본문을 제외한 생성 시각·ID 내림차순 목록입니다. chapterId·status 선택 필터, page=0·size=10(상한"
                            + " 100). 본인 작품만 조회. 200/400/401/404.")
    ApiResult<OffsetPageResponse<AnalysisJobSummary>> list(
            LoginUser user,
            @Positive Long workId,
            @Size(max = 64) String chapterId,
            AnalysisJobStatus status,
            @Min(0) int page,
            @Min(1) @Max(100) int size);

    @Operation(
            summary = "분석 당시 전송 자료",
            description =
                    "입력·개별 원고 및 설정 스냅샷·현재 자료 변경 여부를 반환합니다. 기존 작업은 legacy=true로 기존 input을 보존합니다. 복원"
                            + " 불가능한 기존 입력은 409. 본인 작품의 작업만 조회. 200/400/401/404/409.")
    ApiResult<AnalysisSnapshotResponse> snapshot(
            LoginUser user, @Positive Long workId, @NotBlank @Size(max = 36) String id);

    @Operation(
            summary = "비동기 AI 검토 접수",
            description =
                    """
                    **목적**

                    저장한 회차 원고를 작품의 확정 설정 및 선택한 참고 회차와 비교하여 설정 충돌 가능성과 설명을 보완할 부분을 찾습니다.

                    **제공 기능**

                    설정 충돌 검토(consistency)와 인물 개연성 검토(motivation) 중 하나 이상을 선택하여 비동기 작업을 요청합니다.

                    **도메인 정책**

                    - 본인 작품의 저장된 원고를 사용합니다. workspaceVersion이 현재 작업공간 버전과 일치해야 하며, 분석할 회차 원고가 비어 있으면 요청할 수 없습니다.
                    - settingIds에는 같은 작품의 확정(CONFIRMED) 설정을 1~1,000개 지정합니다. 아이디어와 AI 후보는 사용할 수 없습니다.
                    - previousIds에는 같은 작업공간의 참고 회차를 최대 100개 지정합니다. 분석 대상 회차 자체는 참고 회차로 지정할 수 없습니다.
                    - 대상 원고·선택한 설정·참고 회차·해당 회차의 기존 판단을 접수 시점의 자료로 묶어 외부 Gemini API에 전송합니다. 직렬화된 분석 자료는 최대 200,000자입니다.
                    - 먼저 preview를 조회하고 확인·동의한 previewToken과 consentPolicyVersion을 함께 전송해야 합니다. 자료 변경·만료는 409, 동의 누락은 400입니다.
                    - AI 결과는 작가가 판단할 검토 후보입니다. 원고와 설정을 자동으로 수정하지 않습니다. 작업 결과는 서버에 저장되어 화면 이동 후에도 조회할 수 있습니다. 작가 판단은 workspace 저장으로 보관합니다.
                    - 같은 작품에서 동일한 requestId와 요청 내용을 재전송하면 기존 작업을 반환합니다.
                      같은 ID로 내용을 바꾸면 400입니다.
                      새 검토에는 새 requestId를 사용합니다.
                    - 대기·실행 중인 작업은 사용자당 최대 1건, 서버 전체 최대 10건입니다. 공급자 호출 제한이나 대기열 상황에 따라 접수 후에도 실패할 수 있습니다.

                    **응답**

                    202와 작업 ID·현재 상태를 반환하고 Location 헤더에 조회 주소를 제공합니다. 접수 성공은 분석 완료를 의미하지 않습니다.

                    **인증·요청 조건**

                    로그인 세션과 CSRF 헤더가 필요합니다. 외부 AI 호출에 따른 비용이 발생할 수 있습니다.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "202",
                description = "비동기 검토 접수 또는 동일 요청의 기존 작업 반환",
                useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "분석 옵션·원고·참고 범위·입력 크기 오류 또는 requestId 재사용 내용 불일치",
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
                description = "작업공간 버전 또는 동시 수정 충돌",
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
                                schema = @Schema(implementation = ApiResult.class))),
        @ApiResponse(
                responseCode = "429",
                description = "대기·실행 중인 작업 수 제한 초과",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class)))
    })
    ResponseEntity<ApiResult<AnalysisJobResponse>> create(
            LoginUser user, @Positive Long workId, @Valid AnalysisRequest request);

    @Operation(
            summary = "AI 작업 상태·결과 조회",
            description =
                    """
                    **목적**

                    접수한 검토의 진행 상황을 확인하고 완료된 검토 후보와 근거를 가져옵니다.

                    **제공 기능**

                    작업 ID로 상태(status), 실패 안내(error), 검토 결과(result)를 조회합니다.

                    **도메인 정책**

                    - 본인 작품에 속한 작업만 조회할 수 있습니다. 다른 작품의 작업 ID를 전달하면 404입니다.
                    - 상태는 QUEUED(대기), RUNNING(실행), SUCCEEDED(완료), FAILED(실패), CANCELLED(취소)입니다.
                    - 성공 결과에는 분석 당시의 원고·회차 버전·설정 자료와 검토 항목이 포함됩니다. 현재 편집 내용과 달라질 수 있으므로 분석 당시 자료를 기준으로 확인합니다.
                    - 서버는 후보의 인용문이 대상 원고에 포함되는지, 설정 ID가 선택한 근거인지, 요청한 검토 유형인지 검증합니다. 문제가 없는 결과는 빈 검토 목록일 수 있습니다.
                    - 기존 판단은 같은 회차의 같은 설정 ID·인용문·설정 내용이 일치하는 후보에 이어집니다. 의도된 설정을 작품 전체의 예외로 확대하지 않습니다.
                    - AI 처리 실패도 조회 자체는 200이며 status=FAILED와 error로 확인합니다. 서버 재시작 시 남은 대기·실행 작업은 실패로 처리됩니다.

                    **응답**

                    200과 현재 작업 정보를 반환합니다. 결과는 작업공간에 자동 저장되지 않습니다.

                    **인증·요청 조건**

                    로그인 세션이 필요합니다.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "작업 상태·결과 또는 실패 안내 조회 성공",
                useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "작품 ID 또는 작업 ID 형식·길이 오류",
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
                description = "작품·작업 없음 또는 접근 불가",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResult.class)))
    })
    ApiResult<AnalysisJobResponse> get(
            LoginUser user, @Positive Long workId, @NotBlank @Size(max = 36) String id);

    @Operation(
            summary = "AI 작업 취소",
            description =
                    """
                    **목적**

                    더 이상 필요하지 않은 검토 작업의 실행 또는 결과 반영을 중단합니다.

                    **제공 기능**

                    대기·실행 중인 작업을 취소 상태(CANCELLED)로 변경합니다.

                    **도메인 정책**

                    - 본인 작품에 속한 작업만 취소할 수 있습니다.
                    - 이미 완료·실패·취소된 작업은 기존 상태를 그대로 반환합니다.
                    - 대기 중 취소된 작업은 외부 AI 호출을 시작하지 않습니다. 실행 중 취소된 작업은 이후 도착한 결과를 작업 결과로 반영하지 않습니다.
                    - 외부 AI에 이미 전송된 요청의 처리나 비용까지 취소하는 것은 보장하지 않습니다.
                    - 취소는 작업공간에 저장된 원고·기존 검토 결과·작가 판단을 삭제하지 않습니다.

                    **응답**

                    200과 작업 상태를 반환합니다. 취소 요청과 완료 처리가 겹친 경우 응답 상태를 확인합니다.

                    **인증·요청 조건**

                    로그인 세션과 CSRF 헤더가 필요합니다.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "작업 취소 또는 이미 종료된 작업의 기존 상태 반환",
                useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "작품 ID 또는 작업 ID 형식·길이 오류",
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
                description = "작품·작업 없음 또는 접근 불가",
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
    ApiResult<AnalysisJobResponse> cancel(
            LoginUser user, @Positive Long workId, @NotBlank @Size(max = 36) String id);
}
