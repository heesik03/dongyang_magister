package com.magister.backend.domain.analysis.docs;
import com.magister.backend.domain.analysis.dto.*;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
@Tag(name="Analysis") @SecurityRequirement(name="session")
public interface AnalysisControllerDocs {
 @Operation(summary="비동기 AI 검토 접수",description="CSRF 필요. 저장된 원고·확정 설정만 전송. 동일 requestId는 중복 실행하지 않습니다. 입력 최대 20만 자. 성공은 202·Location, 오류는 400/401/403/404/409/429.")
 ResponseEntity<ApiResult<AnalysisJobResponse>> create(LoginUser user,@Positive Long workId,@Valid AnalysisRequest request);
 @Operation(summary="AI 작업 상태·결과 조회")
 ApiResult<AnalysisJobResponse> get(LoginUser user,@Positive Long workId,@NotBlank @Size(max=36) String id);
 @Operation(summary="AI 작업 취소",description="대기 작업 실행과 실행 중 결과 반영을 취소합니다. 이미 전송된 공급자 요청 비용은 취소되지 않을 수 있습니다.")
 ApiResult<AnalysisJobResponse> cancel(LoginUser user,@Positive Long workId,@NotBlank @Size(max=36) String id);
}
