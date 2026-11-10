package com.magister.backend.domain.scene;

import com.magister.backend.global.error.code.ErrorCode;
import org.springframework.http.HttpStatus;

record SceneImageUnavailable() implements ErrorCode {
    @Override
    public HttpStatus getHttpStatus() { return HttpStatus.SERVICE_UNAVAILABLE; }

    @Override
    public String getCode() { return "SCENE_IMAGE_UNAVAILABLE"; }

    @Override
    public String getMessage() { return "이미지 생성 모델이 설정되지 않았습니다."; }
}
