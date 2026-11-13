package com.magister.backend.domain.setting.image;

import com.magister.backend.domain.setting.image.CharacterImageData.CreateRequest;
import com.magister.backend.domain.setting.image.CharacterImageData.Gallery;
import com.magister.backend.domain.setting.image.CharacterImageData.Input;
import com.magister.backend.domain.setting.image.CharacterImageData.Job;
import com.magister.backend.domain.setting.image.CharacterImageData.Preview;
import com.magister.backend.domain.setting.image.CharacterImageRepository.StoredImage;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/work/{workId}/setting/{settingId}/character-image")
public class CharacterImageController {
    private final CharacterImageService service;

    @GetMapping
    @Operation(summary = "본인 인물의 시안과 생성 한도 조회")
    public ApiResult<Gallery> list(@AuthenticationPrincipal LoginUser user,
            @PathVariable long workId, @PathVariable long settingId) {
        return ApiResult.success(service.list(user.id(), workId, settingId));
    }

    @PostMapping("/preview")
    @Operation(summary = "인물 이미지 전송 문구 미리보기",
            description = "appearance는 외형 설명, direction은 의상·표정·화풍 등 선택 연출입니다. 토큰은 설정 버전과 문구에 묶입니다.")
    public ApiResult<Preview> preview(@AuthenticationPrincipal LoginUser user,
            @PathVariable long workId, @PathVariable long settingId, @Valid @RequestBody Input input) {
        return ApiResult.success(service.preview(user.id(), workId, settingId, input));
    }

    @PostMapping
    @Operation(summary = "인물 이미지 생성 접수",
            description = "미리보기와 동일한 input, token, requestId, consent:true를 보냅니다. 202 작업 접수, 409 입력 변경 또는 보관 한도, 429 일일 한도.")
    public ResponseEntity<ApiResult<Job>> create(@AuthenticationPrincipal LoginUser user,
            @PathVariable long workId, @PathVariable long settingId, @Valid @RequestBody CreateRequest request) {
        return ResponseEntity.accepted().body(ApiResult.success(service.create(user.id(), workId, settingId, request)));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(@AuthenticationPrincipal LoginUser user,
            @PathVariable long workId, @PathVariable long settingId, @PathVariable String id) {
        StoredImage image = service.content(user.id(), workId, settingId, id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(image.mimeType())).body(image.bytes());
    }

    @PutMapping("/{id}/primary")
    public ResponseEntity<Void> selectPrimary(@AuthenticationPrincipal LoginUser user,
            @PathVariable long workId, @PathVariable long settingId, @PathVariable String id) {
        service.selectPrimary(user.id(), workId, settingId, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/primary")
    public ResponseEntity<Void> clearPrimary(@AuthenticationPrincipal LoginUser user,
            @PathVariable long workId, @PathVariable long settingId) {
        service.selectPrimary(user.id(), workId, settingId, null);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal LoginUser user,
            @PathVariable long workId, @PathVariable long settingId, @PathVariable String id) {
        service.delete(user.id(), workId, settingId, id);
        return ResponseEntity.noContent().build();
    }
}
