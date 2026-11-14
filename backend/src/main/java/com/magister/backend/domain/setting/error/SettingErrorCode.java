package com.magister.backend.domain.setting.error;

import com.magister.backend.global.error.code.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SettingErrorCode implements ErrorCode {
    SETTING_NOT_FOUND(HttpStatus.NOT_FOUND, "설정을 찾을 수 없습니다."),
    CHARACTER_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "인물 이미지 시안을 찾을 수 없습니다."),
    CHARACTER_IMAGE_CAPACITY(HttpStatus.CONFLICT, "시안 보관 한도에 도달했습니다. 기존 시안을 삭제해 주세요."),
    CHARACTER_IMAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "인물 이미지 모델을 사용할 수 없습니다.");
    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
