package com.magister.backend.domain.scene;

import com.magister.backend.global.error.code.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SceneErrorCode implements ErrorCode {
    SCENE_BOARD_NOT_FOUND(HttpStatus.NOT_FOUND, "장면 보드를 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
