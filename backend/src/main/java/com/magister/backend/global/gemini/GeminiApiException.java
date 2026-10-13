package com.magister.backend.global.gemini;

public class GeminiApiException extends RuntimeException {

    public enum ErrorCode {
        AUTHENTICATION_FAILED,
        RATE_LIMITED,
        TIMEOUT,
        REQUEST_REJECTED,
        UNAVAILABLE,
        INVALID_RESPONSE
    }

    private final ErrorCode errorCode;

    public GeminiApiException(ErrorCode errorCode) {
        // 공급자 오류 본문에는 입력이나 비밀 값이 포함될 수 있으므로 보존하지 않는다.
        super("Gemini request failed: " + errorCode);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
