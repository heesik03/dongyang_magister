package com.magister.backend.domain.scene;

import com.magister.backend.global.error.code.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SceneErrorCode implements ErrorCode {
    SCENE_BOARD_NOT_FOUND(HttpStatus.NOT_FOUND, "장면 보드를 찾을 수 없습니다."),
    SCENE_BOARD_NOT_READY(HttpStatus.CONFLICT, "완료된 장면 보드에서만 콘셉트 이미지를 생성할 수 있습니다."),
    SCENE_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "콘셉트 이미지를 찾을 수 없습니다."),
    SCENE_IMAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "이미지 생성 모델이 설정되지 않았습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
