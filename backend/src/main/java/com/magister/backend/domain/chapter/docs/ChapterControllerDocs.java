package com.magister.backend.domain.chapter.docs;

import com.magister.backend.domain.chapter.dto.ChapterResponse;
import com.magister.backend.domain.chapter.dto.CreateChapterRequest;
import com.magister.backend.domain.chapter.dto.UpdateChapterRequest;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.global.pagination.OffsetPageResponse;
import com.magister.backend.global.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;

@Tag(name = "Chapter", description = "본인 작품의 회차별 원고 저장")
@SecurityRequirement(name = "session")
public interface ChapterControllerDocs {
    @Operation(
            summary = "회차 생성",
            description =
                    "CSRF 필요. 작품당 최대 100회차, 제목 100자, 본문 20만 자. id와 mutationId는 64자 이하이며 응답 유실 시 동일"
                        + " 값·내용으로 재시도합니다. 201/400/401/403/404/409/413. 예:"
                        + " {\"id\":\"chapter-1\",\"title\":\"1화\",\"content\":\"원고\",\"mutationId\":\"save-1\"}")
    ResponseEntity<ApiResult<ChapterResponse>> create(
            LoginUser user, @Positive Long workId, @Valid CreateChapterRequest request);

    @Operation(
            summary = "회차 목록",
            description = "본인 작품만 회차 번호 오름차순. page는 0부터, size는 1~100. 200/400/401/404.")
    ApiResult<OffsetPageResponse<ChapterResponse>> list(
            LoginUser user, @Positive Long workId, @Min(0) int page, @Min(1) @Max(100) int size);

    @Operation(
            summary = "회차 상세",
            description = "현재 원고·revision·저장용 version 반환. 타 작품 회차도 404. 200/400/401/404.")
    ApiResult<ChapterResponse> get(
            LoginUser user, @Positive Long workId, @NotBlank @Size(max = 64) String chapterId);

    @Operation(
            summary = "회차 저장",
            description =
                    "CSRF 필요. 조회한 version과 새 mutationId를 전송합니다. 동일 mutation·내용 재시도는 버전을 증가시키지 않으며"
                        + " 충돌은 409입니다. 200/400/401/403/404/409/413. 예:"
                        + " {\"title\":\"1화\",\"content\":\"수정"
                        + " 원고\",\"version\":0,\"mutationId\":\"save-2\"}")
    ApiResult<ChapterResponse> update(
            LoginUser user,
            @Positive Long workId,
            @NotBlank @Size(max = 64) String chapterId,
            @Valid UpdateChapterRequest request);
}
