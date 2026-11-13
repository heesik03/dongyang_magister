package com.magister.backend.domain.decision.docs;

import com.magister.backend.domain.decision.dto.DecisionRequest;
import com.magister.backend.domain.decision.dto.DecisionResponse;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.global.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Tag(name = "Decision", description = "작가 판단 기록 및 조회")
@SecurityRequirement(name = "session")
public interface DecisionControllerDocs {
    @Operation(
            summary = "작가 판단 기록",
            description =
                    "본인 후보만 저장. status·reason·expectedVersion·mutationId·basisHash·reconfirm·scopes"
                            + " 필요. 의도된 설정은 사유·범위·조건 필수. 미검토는 철회이며 scopes=[]. 원고·설정 자동 수정 없음. 동일"
                            + " mutation·내용 재전송은 중복 저장하지 않음. 200/400/401/403(CSRF)/404/409(다른 탭·근거"
                            + " 변경).")
    ApiResult<DecisionResponse> save(
            LoginUser user,
            @Positive Long workId,
            @Size(max = 36) String reviewId,
            @Valid DecisionRequest request);

    @Operation(
            summary = "최신 판단 조회",
            description =
                    "미저장 후보는 version=0. basisHash는 현재 근거의 지문. stale은 저장 판단 이후 근거 변경, canReconfirm은"
                            + " 현재 원문에도 인용 위치가 일치하는지 표시. 200/400/401/404.")
    ApiResult<DecisionResponse> get(
            LoginUser user, @Positive Long workId, @Size(max = 36) String reviewId);
}
