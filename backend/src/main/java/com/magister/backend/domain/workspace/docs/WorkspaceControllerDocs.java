package com.magister.backend.domain.workspace.docs;
import com.magister.backend.domain.workspace.dto.*;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
@Tag(name="Workspace") @SecurityRequirement(name="session")
public interface WorkspaceControllerDocs {
 @Operation(summary="작품 원고·검토·타임라인 조회",description="본인 작품만 조회. 없는 문서는 version=0인 빈 문서입니다.")
 ApiResult<WorkspaceResponse> get(LoginUser user,@Positive Long workId);
 @Operation(summary="작품 편집 문서 저장",description="CSRF 필요. version 불일치는 409. 동일 mutationId/내용 재시도는 중복 저장하지 않습니다. 문서 최대 200만 자.")
 ApiResult<WorkspaceResponse> save(LoginUser user,@Positive Long workId,@Valid SaveWorkspaceRequest request);
}
