package com.magister.backend.domain.history.error;

import com.magister.backend.global.error.code.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum HistoryErrorCode implements ErrorCode {
    INVALID_SOURCE_ID(HttpStatus.BAD_REQUEST, "역사 자료 식별자 형식이 올바르지 않습니다."),
    SOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 공식 역사 자료를 찾을 수 없습니다."),
    PROVIDER_KEY_MISSING(HttpStatus.SERVICE_UNAVAILABLE, "한국민족문화대백과사전 인증키가 설정되지 않았습니다."),
    PROVIDER_LOOKUP_FAILED(HttpStatus.BAD_GATEWAY, "공식 역사 자료 조회를 완료하지 못했습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
