package com.magister.backend.domain.workspace.docs;

import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.domain.workspace.dto.SaveWorkspaceRequest;
import com.magister.backend.domain.workspace.dto.WorkspaceResponse;
import com.magister.backend.global.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

@Tag(name = "Workspace", description = "작품별 회차 원고·검토 근거·작가 판단·사건 타임라인의 편집 문서 관리")
@SecurityRequirement(name = "session")
public interface WorkspaceControllerDocs {

    @Operation(
            summary = "작품 원고·검토·타임라인 조회",
            description =
                    """
                    **목적**

                    작품 편집을 이어갈 수 있도록 저장해 둔 원고와 검토 기록을 한 번에 불러옵니다.

                    **제공 기능**

                    회차 원고(chapters), 검토 결과와 작가 판단(analyses), 사건 타임라인(events)을 함께 조회합니다.

                    **도메인 정책**

                    - 본인 작품만 조회할 수 있습니다. 작품이 없거나 다른 사용자의 작품이면 404입니다.
                    - 작품은 있으나 저장한 문서가 없으면 version=0이며 각 목록이 비어 있는 문서를 반환합니다.
                    - version은 작업공간 문서를 저장할 때 사용하는 버전입니다. workVersion은 작품 기본 정보의 버전이며 서로 구분해야 합니다.
                    - 검토 기록에는 분석 당시의 원고와 근거가 포함될 수 있습니다. 현재 원고가 수정되었다고 기존 기록을 자동으로 재분석하지 않습니다.
                    - 설정집 항목은 별도의 설정 API로 조회합니다.

                    **응답**

                    200과 작업공간 문서·버전 정보를 반환합니다. 빈 문서의 updatedAt은 최초 기준 시각(1970-01-01T00:00:00Z)입니다.

                    **인증·요청 조건**

                    로그인 세션이 필요합니다.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "저장된 작업공간 또는 최초 빈 문서 조회 성공",
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
    ApiResult<WorkspaceResponse> get(LoginUser user, @Positive Long workId);

    @Operation(
            summary = "작품 편집 문서 저장",
            description =
                    """
                    **목적**

                    회차 원고, 검토 근거, 작가의 판단과 사건 정보를 함께 보관하여 작품 편집을 이어갈 수 있도록 합니다.

                    **제공 기능**

                    작업공간 문서 전체를 저장합니다. 검토 항목에는 미검토·수용·보류·의도된 설정 상태와 메모를 기록할 수 있습니다.

                    **도메인 정책**

                    - 본인 작품만 저장할 수 있습니다. content의 chapters·analyses·events 전체를 전달하며, 목록에서 제외한 항목은 저장된 문서에서도 제외됩니다.
                    - 의도된 설정을 선택하면 판단 사유(note)를 반드시 입력해야 합니다. 판단은 해당 검토 항목의 맥락으로 보관하며 설정집을 자동 변경하지 않습니다.
                    - 회차 ID는 중복될 수 없고 검토 기록은 회차당 최대 1개입니다.
                      검토 대상·참고 회차와 사건의 연결 회차는 문서 안에 존재해야 합니다.
                      사건의 연결 회차는 빈 문자열을 허용합니다.
                    - 검토 항목의 인용문은 해당 분석에 보관된 원문(source)에 포함되어야 하며, settingId는 해당 분석의 settingIds에 포함되어야 합니다.
                    - 조회한 작업공간 version을 전달해야 합니다. 현재 버전과 다르면 409이며, 최초 저장은 version=0으로 요청합니다.
                    - 현재 저장된 mutationId와 내용이 같은 재요청은 버전을 추가하지 않습니다.
                      같은 ID로 다른 내용을 보내면 400입니다.
                      새 저장에는 새 mutationId를 사용합니다.
                    - 회차·검토 기록은 각각 최대 100개, 사건은 최대 1,000개입니다. 회차 원고는 각각 최대 200,000자, 직렬화된 content 전체는 최대 2,000,000자입니다.
                    - 저장 시 작품의 수정 정보도 갱신합니다. 반환된 version과 workVersion을 이후 편집에 사용합니다.

                    **응답**

                    200과 저장된 문서·버전 정보를 반환합니다. 저장은 AI 검토 실행을 포함하지 않습니다.

                    **인증·요청 조건**

                    로그인 세션과 CSRF 헤더가 필요합니다.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "작업공간 저장 또는 동일 저장 재요청 처리 성공",
                useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "문서 구조·길이·참조 관계·판단 사유 오류 또는 mutationId 재사용 내용 불일치",
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
                                schema = @Schema(implementation = ApiResult.class)))
    })
    ApiResult<WorkspaceResponse> save(
            LoginUser user, @Positive Long workId, @Valid SaveWorkspaceRequest request);
}
