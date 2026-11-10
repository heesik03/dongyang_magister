package com.magister.backend.domain.scene;

import com.magister.backend.domain.scene.SceneImageData.CreateRequest;
import com.magister.backend.domain.scene.SceneImageData.Input;
import com.magister.backend.domain.scene.SceneImageData.Job;
import com.magister.backend.domain.scene.SceneImageData.Preview;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.global.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
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
@RequiredArgsConstructor
@RequestMapping("/api/work/{workId}/scene-board/{boardId}/images")
public class SceneImageController {
    private final SceneImageService service;

    @PostMapping("/preview")
    @Operation(summary = "장소·소품 이미지에 전송할 설명 확인",
            description = "본인 작품 전용. kind는 LOCATION/PROP이며 confirmed/direction은 각각 최대 500자입니다.")
    public ApiResult<Preview> preview(@AuthenticationPrincipal LoginUser user,
            @PathVariable long workId, @PathVariable String boardId, @Valid @RequestBody Input input) {
        return ApiResult.success(service.preview(user.id(), workId, boardId, input));
    }

    @PostMapping
    @Operation(summary = "동의한 이미지 생성 작업 접수",
            description = "동일 requestId와 input은 기존 작업을 반환합니다. 202 접수, 409 입력 변경, 429 할당량 소진.")
    public ResponseEntity<ApiResult<Job>> create(@AuthenticationPrincipal LoginUser user,
            @PathVariable long workId, @PathVariable String boardId, @Valid @RequestBody CreateRequest request) {
        return ResponseEntity.accepted().body(ApiResult.success(service.create(user.id(), workId, boardId, request)));
    }

    @GetMapping
    public ApiResult<List<Job>> list(@AuthenticationPrincipal LoginUser user, @PathVariable long workId,
            @PathVariable String boardId, @RequestParam(defaultValue = "0") @Min(0) int page) {
        return ApiResult.success(service.list(user.id(), workId, boardId, page));
    }

    @GetMapping("/{id}")
    public ApiResult<Job> get(@AuthenticationPrincipal LoginUser user,
            @PathVariable long workId, @PathVariable String boardId, @PathVariable String id) {
        return ApiResult.success(service.get(user.id(), workId, boardId, id));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(@AuthenticationPrincipal LoginUser user,
            @PathVariable long workId, @PathVariable String boardId, @PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG).body(service.content(user.id(), workId, boardId, id));
    }
}
