package com.magister.backend.domain.chapter.docs;

import com.magister.backend.domain.chapter.dto.ChapterResponse;
import com.magister.backend.domain.chapter.dto.CreateChapterRequest;
import com.magister.backend.domain.chapter.dto.ManuscriptImportResponse;
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
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Chapter", description = "본인 작품의 회차별 원고 저장 및 내보내기·가져오기")
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

    @Operation(
            summary = "회차 원고 내보내기 (Export)",
            description =
                    "본인 작품의 특정 회차 원고를 요청한 파일 포맷(txt, md, pdf, docx)으로 내보냅니다. 작품 제목·장르·회차 제목을"
                        + " 포함하며, txt·md는 마크다운 원문을 그대로 유지하고 pdf·docx는 마크다운을 제목 크기·서식으로 변환합니다."
                        + " 200/400/401/404.")
    ResponseEntity<byte[]> exportChapter(
            LoginUser user,
            @Positive Long workId,
            @NotBlank @Size(max = 64) String chapterId,
            String format);

    @Operation(
            summary = "원고 파일 텍스트 추출 (Import 미리보기)",
            description =
                    "CSRF 필요. 업로드한 원고 파일(txt, md, pdf, docx)에서 본문 텍스트와 상단 메타데이터를 추출하여 반환합니다. DB를"
                        + " 변경하지 않으며 본문 최대 길이는 20만 자입니다. 200/400/401/403/404/413.")
    ApiResult<ManuscriptImportResponse> parseImportFile(
            LoginUser user, @Positive Long workId, MultipartFile file);

    @Operation(
            summary = "원고 파일로 새 회차 생성 (Import)",
            description =
                    "CSRF 필요. 업로드한 원고 파일(txt, md, pdf, docx)에서 내용을 추출하여 즉시 새 회차로 저장합니다. title을 생략하면"
                        + " 파일 내 회차 헤더 또는 파일명을 제목으로 사용합니다. 201/400/401/403/404/409/413.")
    ResponseEntity<ApiResult<ChapterResponse>> importAsNewChapter(
            LoginUser user,
            @Positive Long workId,
            MultipartFile file,
            String id,
            String title,
            String mutationId);

    @Operation(
            summary = "원고 파일을 기존 회차에 반영 (Import 덮어쓰기)",
            description =
                    "CSRF 필요. 업로드한 원고 파일(txt, md, pdf, docx)에서 내용을 추출하여 기존 회차 원고로 저장합니다. version과"
                        + " mutationId로 동시 편집 충돌을 검사합니다. 200/400/401/403/404/409/413.")
    ApiResult<ChapterResponse> importIntoChapter(
            LoginUser user,
            @Positive Long workId,
            @NotBlank @Size(max = 64) String chapterId,
            MultipartFile file,
            @Min(0) long version,
            @NotBlank @Size(max = 64) String mutationId,
            String title);
}
