package com.magister.backend.global.security;

import com.magister.backend.domain.user.error.UserErrorCode;
import com.magister.backend.global.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class SecurityResponseHandler
        implements AuthenticationEntryPoint, AccessDeniedHandler, LogoutSuccessHandler {
    private final ObjectMapper json;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException {
        writeError(response, UserErrorCode.UNAUTHENTICATED);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception)
            throws IOException {
        writeError(response, UserErrorCode.ACCESS_DENIED);
    }

    @Override
    public void onLogoutSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        write(response, HttpServletResponse.SC_OK, ApiResponse.success(null));
    }

    private void writeError(HttpServletResponse response, UserErrorCode code) throws IOException {
        write(response, code.getHttpStatus().value(), ApiResponse.failure(code));
    }

    private void write(HttpServletResponse response, int status, ApiResponse<?> body)
            throws IOException {
        // 보안 필터의 예외는 ControllerAdvice에 도달하지 않으므로 같은 응답 계약으로 직접 직렬화한다.
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(json.writeValueAsString(body));
    }
}
