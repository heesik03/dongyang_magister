package com.magister.backend.domain.analysis.docs;

import com.magister.backend.domain.analysis.dto.RevisionSuggestion;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.global.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Tag(name = "Analysis", description = "검토 후보 기반 보완 문장 전송 범위 확인 및 생성")
@SecurityRequirement(name = "session")
public interface RevisionSuggestionControllerDocs {
    @Operation(
            summary = "보완 문장 전송 범위 확인",
            description =
                    "작품 소유자 전용. 인용과 앞뒤 각 300자 이내 문맥, 후보에 연결된 확정 설정 인용 및 이전 회차 인용·관련 이유를"
                            + " 반환합니다. 연결되지 않은 회차 전문과 작가 판단 메모는 제외합니다. 200/400/401/404/409.")
    ApiResult<RevisionSuggestion.Context> context(
            LoginUser user,
            @Positive Long workId,
            @NotBlank @Size(max = 36) String reviewId);

    @Operation(
            summary = "검토 후보 보완 문장 생성",
            description =
                    "미리보기의 contextHash를 제출합니다. 소유권·출처 버전·인용 위치를 재검증하고 원문 경계 공백을 보존한 대체"
                            + " 문장을 반환합니다. 근거 변경 409, 호출 한도 429.")
    ApiResult<RevisionSuggestion.Result> generate(
            LoginUser user,
            @Positive Long workId,
            @NotBlank @Size(max = 36) String reviewId,
            @Valid RevisionSuggestion.Request request);
}
