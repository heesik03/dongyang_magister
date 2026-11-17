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
    private static final String PERMIT_HOLD_ATTR = AuthRateLimitFilter.class.getName() + ".PERMIT";

    private final AuthRequestLimiter limiter;
    private final ObjectMapper objectMapper;

    public static boolean hasActivePermit(HttpServletRequest request) {
        return request != null && Boolean.TRUE.equals(request.getAttribute(PERMIT_HOLD_ATTR));
    }

    /** CSRF 검증을 통과한 인증 요청에 대해 본문 해석·비밀번호 해싱 전에 빈도와 동시 작업 수를 제한한다. */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!limiter.appliesTo(request)) {
            chain.doFilter(request, response);
            return;
        }

        Permit permit;
        try {
            permit = limiter.reserve(request);
        } catch (CustomException error) {
            HttpResponseWriter.failure(response, objectMapper, error.getErrorCode());
            return;
        }

        request.setAttribute(PERMIT_HOLD_ATTR, Boolean.TRUE);
        try (permit) {
            chain.doFilter(request, response);
        } finally {
            request.removeAttribute(PERMIT_HOLD_ATTR);
        }
    }
}
