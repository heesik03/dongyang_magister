package com.magister.backend.global.security;

import com.magister.backend.domain.user.security.AuthRequestLimiter;
import com.magister.backend.domain.user.security.AuthRequestLimiter.Permit;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.response.HttpResponseWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private final AuthRequestLimiter limiter;
    private final ObjectMapper objectMapper;

    /** 본문 해석·비밀번호 해싱 전에 인증 요청의 빈도와 실제 동시 작업 수를 제한한다. */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!limiter.appliesTo(request.getMethod(), request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        Permit permit;
        try {
            permit = limiter.reserve(request.getRequestURI(), request.getRemoteAddr());
        } catch (CustomException error) {
            HttpResponseWriter.failure(response, objectMapper, error.getErrorCode());
            return;
        }

        try (permit) {
            chain.doFilter(request, response);
        }
    }
}
