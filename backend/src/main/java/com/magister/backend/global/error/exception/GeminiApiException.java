package com.magister.backend.global.error.exception;

import com.magister.backend.global.error.code.GeminiErrorCode;

public class GeminiApiException extends CustomException {

    public GeminiApiException(GeminiErrorCode errorCode) {
        // 공급자 오류 본문에는 입력이나 비밀 값이 포함될 수 있으므로 보존하지 않는다.
        super(errorCode);
    }
}
