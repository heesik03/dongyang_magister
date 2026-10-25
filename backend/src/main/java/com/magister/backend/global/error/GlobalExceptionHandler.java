package com.magister.backend.global.error;

import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.code.ErrorCode;
import com.magister.backend.global.error.code.FrameworkErrorCode;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.response.ApiResponse;
import com.magister.backend.global.response.FieldErrorDetail;

import lombok.extern.slf4j.Slf4j;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

@RestControllerAdvice
@Slf4j
@NullMarked
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        ApiResponse<?> responseBody;
        if (body instanceof ApiResponse<?> apiResponse) {
            responseBody = apiResponse;
        } else {
            ErrorCode code =
                    statusCode.value() == 400
                            ? CommonErrorCode.INVALID_REQUEST
                            : new FrameworkErrorCode(statusCode);
            responseBody = ApiResponse.failure(code);
        }
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        // 예외 메시지와 원인에는 요청 본문이나 공급자 비밀 값이 포함될 수 있다.
        if (statusCode.is5xxServerError()) {
            log.error(
                    "API failure: code={}, status={}",
                    responseBody.code().getCode(),
                    statusCode.value());
        } else {
            log.warn(
                    "API failure: code={}, status={}",
                    responseBody.code().getCode(),
                    statusCode.value());
        }
        return super.createResponseEntity(responseBody, responseHeaders, statusCode, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        List<FieldErrorDetail> errors =
                exception.getBindingResult().getFieldErrors().stream()
                        .map(
                                error ->
                                        new FieldErrorDetail(
                                                error.getField(),
                                                error.getDefaultMessage() == null
                                                        ? "올바르지 않은 값입니다."
                                                        : error.getDefaultMessage()))
                        .toList();
        ApiResponse<Void> body = ApiResponse.failure(CommonErrorCode.VALIDATION_ERROR, errors);
        return handleExceptionInternal(exception, body, headers, statusCode, request);
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Object> handleCustomException(
            CustomException exception, WebRequest request) {
        ErrorCode error = exception.getErrorCode();
        return createResponseEntity(
                ApiResponse.failure(error), new HttpHeaders(), error.getHttpStatus(), request);
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleVersionConflict(WebRequest request) {
        ErrorCode error = com.magister.backend.domain.work.error.WorkErrorCode.VERSION_CONFLICT;
        return createResponseEntity(
                ApiResponse.failure(error), new HttpHeaders(), error.getHttpStatus(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(
            Exception exception, WebRequest request) {
        ErrorCode error = CommonErrorCode.INTERNAL_SERVER_ERROR;
        return createResponseEntity(
                ApiResponse.failure(error), new HttpHeaders(), error.getHttpStatus(), request);
    }
}
