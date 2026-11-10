package com.magister.backend.domain.scene;

import com.magister.backend.domain.scene.SceneBoardData.Board;
import com.magister.backend.domain.scene.SceneBoardData.CreateRequest;
import com.magister.backend.domain.scene.SceneBoardData.Preview;
import com.magister.backend.domain.scene.SceneBoardData.Selection;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.global.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/work/{workId}/scene-board")
@RequiredArgsConstructor
public class SceneBoardController {
    private final SceneBoardService sceneBoardService;

    @PostMapping("/preview")
    @Operation(summary = "선택 원고와 확정 설정의 전송 범위 확인",
            description = "본인 작품 전용. start/end는 UTF-16 오프셋이며 end는 미포함, 최대 12,000자.")
    public ApiResult<Preview> preview(@AuthenticationPrincipal LoginUser user, @PathVariable long workId,
            @Valid @RequestBody Selection selection) {
        return ApiResult.success(sceneBoardService.preview(user.id(), workId, selection));
    }

    @PostMapping
    @Operation(summary = "장면 분석 접수",
            description = "세션과 CSRF, preview의 selection/token 및 consent:true가 필요합니다. 202 작업 반환, 409 입력 변경, 429 할당량 소진.")
    public ResponseEntity<ApiResult<Board>> create(@AuthenticationPrincipal LoginUser user,
            @PathVariable long workId, @Valid @RequestBody CreateRequest request) {
        return ResponseEntity.accepted().body(ApiResult.success(sceneBoardService.create(user.id(), workId, request)));
    }

    @GetMapping
    public ApiResult<List<String>> list(@AuthenticationPrincipal LoginUser user, @PathVariable long workId,
            @RequestParam @Size(min = 1, max = 64) String chapterId,
            @RequestParam(defaultValue = "0") @Min(0) int page) {
        return ApiResult.success(sceneBoardService.list(user.id(), workId, chapterId, page));
    }

    @GetMapping("/{id}")
    public ApiResult<Board> get(@AuthenticationPrincipal LoginUser user, @PathVariable long workId,
            @PathVariable String id) {
        return ApiResult.success(sceneBoardService.get(user.id(), workId, id));
    }
}
