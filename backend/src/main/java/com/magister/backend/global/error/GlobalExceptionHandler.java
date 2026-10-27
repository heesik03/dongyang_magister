package com.magister.backend.global.error;

import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.code.ErrorCode;
import com.magister.backend.global.error.code.FrameworkErrorCode;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.response.ApiResult;
import com.magister.backend.global.response.FieldErrorDetail;

import lombok.extern.slf4j.Slf4j;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.sql.SQLException;
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
        ApiResult<?> responseBody;
        if (body instanceof ApiResult<?> apiResponse) {
            responseBody = apiResponse;
        } else {
            ErrorCode code =
                    statusCode.value() == 400
                            ? CommonErrorCode.INVALID_REQUEST
                            : new FrameworkErrorCode(statusCode);
            responseBody = ApiResult.failure(code);
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
        ApiResult<Void> body = ApiResult.failure(CommonErrorCode.VALIDATION_ERROR, errors);
        return handleExceptionInternal(exception, body, headers, statusCode, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        List<FieldErrorDetail> errors =
                exception.getParameterValidationResults().stream()
                        .flatMap(
                                result -> {
                                    String parameterName =
                                            result.getMethodParameter().getParameterName();
                                    String fallbackField =
                                            parameterName == null ? "parameter" : parameterName;
                                    return result.getResolvableErrors().stream()
                                            .map(error -> toFieldErrorDetail(fallbackField, error));
                                })
                        .toList();
        ApiResult<Void> body = ApiResult.failure(CommonErrorCode.VALIDATION_ERROR, errors);
        return handleExceptionInternal(exception, body, headers, statusCode, request);
    }

    private FieldErrorDetail toFieldErrorDetail(
            String fallbackField, MessageSourceResolvable error) {
        String field =
                error instanceof FieldError fieldError ? fieldError.getField() : fallbackField;
        String message =
                error.getDefaultMessage() == null ? "올바르지 않은 값입니다." : error.getDefaultMessage();
        return new FieldErrorDetail(field, message);
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Object> handleCustomException(
            CustomException exception, WebRequest request) {
        ErrorCode error = exception.getErrorCode();
        return createResponseEntity(
                ApiResult.failure(error), new HttpHeaders(), error.getHttpStatus(), request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleVersionConflict(WebRequest request) {
        ErrorCode error = WorkErrorCode.VERSION_CONFLICT;
        return createResponseEntity(
                ApiResult.failure(error), new HttpHeaders(), error.getHttpStatus(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(
            Exception exception, WebRequest request) {
        // 공급자 예외 메시지/요청 본문은 기록하지 않고 타입과 코드 위치만 남긴다.
        SqlFailure sqlFailure = sqlFailure(exception);
        log.error(
                "Unexpected API exception: type={}, location={}, sqlState={}, vendorCode={}",
                exception.getClass().getName(),
                exception.getStackTrace().length == 0 ? "unknown" : exception.getStackTrace()[0],
                sqlFailure.state(),
                sqlFailure.vendorCode());
        ErrorCode error = CommonErrorCode.INTERNAL_SERVER_ERROR;
        return createResponseEntity(
                ApiResult.failure(error), new HttpHeaders(), error.getHttpStatus(), request);
    }

    private record SqlFailure(String state, int vendorCode) {}

    /** DB 오류 전문 대신 드라이버의 안전한 식별 코드만 진단 정보로 추출한다. */
    private SqlFailure sqlFailure(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                String state = sqlException.getSQLState();
                return new SqlFailure(
                        state == null ? "unknown" : state, sqlException.getErrorCode());
            }
        }
        return new SqlFailure("none", 0);
    }
}
