package com.magister.backend.global.response;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record ApiResponse<T>(boolean success, String code, String message,
        @Nullable T data, List<FieldErrorDetail> errors) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "SUCCESS", "요청이 성공했습니다.", data, List.of());
    }

    public static ApiResponse<Void> failure(String code, String message) {
        return failure(code, message, List.of());
    }

    public static ApiResponse<Void> failure(String code, String message, List<FieldErrorDetail> errors) {
        return new ApiResponse<>(false, code, message, null, List.copyOf(errors));
    }
}
