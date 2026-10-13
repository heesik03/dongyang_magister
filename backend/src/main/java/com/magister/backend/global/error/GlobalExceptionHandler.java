package com.magister.backend.global.error;

import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.code.ErrorCode;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.response.ApiResponse;
import com.magister.backend.global.response.FieldErrorDetail;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@Slf4j
@NullMarked
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> createResponseEntity(@Nullable Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        Object responseBody = body;
        if (!(body instanceof ApiResponse<?>)) {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            String code = statusCode.value() == 400 ? CommonErrorCode.INVALID_REQUEST.getCode()
                    : status == null ? "HTTP_ERROR" : status.name();
            String message = statusCode.is5xxServerError()
                    ? CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage() : "요청을 처리할 수 없습니다.";
            responseBody = ApiResponse.failure(code, message);
        }
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        return super.createResponseEntity(responseBody, responseHeaders, statusCode, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        List<FieldErrorDetail> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(),
                        error.getDefaultMessage() == null ? "올바르지 않은 값입니다." : error.getDefaultMessage()))
                .toList();
        ApiResponse<Void> body = ApiResponse.failure(CommonErrorCode.VALIDATION_ERROR.getCode(),
                CommonErrorCode.VALIDATION_ERROR.getMessage(), errors);
        return handleExceptionInternal(exception, body, headers, statusCode, request);
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException exception) {
        ErrorCode error = exception.getErrorCode();
        return ResponseEntity.status(error.getHttpStatus())
                .body(ApiResponse.failure(error.getCode(), error.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        log.error("Unexpected request failure: exceptionType={}", exception.getClass().getName());
        ErrorCode error = CommonErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(error.getHttpStatus())
                .body(ApiResponse.failure(error.getCode(), error.getMessage()));
    }
}
