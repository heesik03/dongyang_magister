package com.magister.backend.global.response;

import com.magister.backend.global.error.code.ErrorCode;

import jakarta.servlet.http.HttpServletResponse;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/** ControllerAdvice 바깥의 필터도 동일한 상태·JSON·문자 인코딩 계약으로 응답한다. */
public final class HttpResponseWriter {
    private HttpResponseWriter() {}

    public static void failure(
            HttpServletResponse response, ObjectMapper objectMapper, ErrorCode code)
            throws IOException {
        write(response, objectMapper, code.getHttpStatus().value(), ApiResult.failure(code));
    }

    public static void write(
            HttpServletResponse response, ObjectMapper objectMapper, int status, ApiResult<?> body)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
