package com.magister.backend.global.error.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GeminiErrorCode implements ErrorCode {
    AUTHENTICATION_FAILED(HttpStatus.BAD_GATEWAY, "Gemini 인증에 실패했습니다."),
    RATE_LIMITED(HttpStatus.SERVICE_UNAVAILABLE, "Gemini 호출 한도를 초과했습니다."),
    TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "Gemini 응답 시간이 초과되었습니다."),
    REQUEST_REJECTED(HttpStatus.BAD_GATEWAY, "Gemini가 요청을 거절했습니다."),
    UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Gemini 서비스를 이용할 수 없습니다."),
    INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "Gemini에서 유효한 응답을 받지 못했습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
