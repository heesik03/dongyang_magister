package com.magister.backend.global.pagination;

import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;

/** cursor는 불투명 위치 토큰이며 인증·권한 증명이 아니다. */
public record CursorPageRequest(String cursor, int size) {
    public CursorPageRequest {
        if (size < 1 || size > 100 || (cursor != null && (cursor.isBlank() || cursor.length() > 2048))) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    public static CursorPageRequest of(String cursor, int size) {
        return new CursorPageRequest(cursor, size);
    }
}
