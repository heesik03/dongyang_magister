package com.magister.backend.global.error.code;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

// 프레임워크 오류는 HTTP 상태와 코드를 연결하고 기존 상태 이름 기반 계약을 유지한다.
public record FrameworkErrorCode(HttpStatusCode httpStatus) implements ErrorCode {
    @Override
    public HttpStatusCode getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getCode() {
        HttpStatus status = HttpStatus.resolve(httpStatus.value());
        return status == null ? "HTTP_ERROR" : status.name();
    }

    @Override
    public String getMessage() {
        return httpStatus.is5xxServerError()
                ? CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage()
                : "요청을 처리할 수 없습니다.";
    }
}
