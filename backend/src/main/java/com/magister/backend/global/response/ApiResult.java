package com.magister.backend.global.response;

import com.magister.backend.global.error.code.ErrorCode;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record ApiResult<T>(
        boolean success,
        @Schema(type = "string", example = "VALIDATION_ERROR") ResponseCode code,
        String message,
        @Nullable T data,
        List<FieldErrorDetail> errors) {

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(true, SuccessCode.SUCCESS, "요청이 성공했습니다.", data, List.of());
    }

    public static ApiResult<Void> failure(ErrorCode code) {
        return failure(code, List.of());
    }

    public static ApiResult<Void> failure(ErrorCode code, List<FieldErrorDetail> errors) {
        return new ApiResult<>(false, code, code.getMessage(), null, List.copyOf(errors));
    }
}
