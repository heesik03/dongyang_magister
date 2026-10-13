package com.magister.backend.domain.gemini.controller;

import com.magister.backend.global.gemini.GeminiApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Profile("local")
@RestControllerAdvice(assignableTypes = GeminiController.class)
public class GeminiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(GeminiApiException.class)
    public ProblemDetail handleGeminiException(GeminiApiException exception) {
        HttpStatus status = switch (exception.getErrorCode()) {
            case RATE_LIMITED, UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case AUTHENTICATION_FAILED, REQUEST_REJECTED, INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
                "Gemini 요청을 완료하지 못했습니다.");
        problem.setTitle("Gemini 호출 실패");
        problem.setProperty("code", exception.getErrorCode().name());
        return problem;
    }
}
