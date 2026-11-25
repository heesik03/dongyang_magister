package com.magister.backend.domain.user.security;

import com.magister.backend.domain.user.converter.UserConverter;
import com.magister.backend.domain.user.dto.LoginRequest;
import com.magister.backend.domain.user.dto.UserResponse;
import com.magister.backend.domain.user.error.UserErrorCode;
import com.magister.backend.domain.user.security.AuthRequestLimiter.Permit;
import com.magister.backend.domain.user.service.support.EmailNormalizer;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.security.AuthRateLimitFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.Locale;

@Service
public class SessionAuthenticationService {
    private static final int AUTHENTICATED_SESSION_TIMEOUT_SECONDS = 3600;

    private final AuthenticationManager authenticationManager;
    private final HttpSessionSecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final AuthRequestLimiter authRequestLimiter;
    private final String cookieSameSite;
    private final boolean cookieSecure;

    @Autowired
    public SessionAuthenticationService(
            AuthenticationManager authenticationManager,
            HttpSessionSecurityContextRepository securityContextRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            AuthRequestLimiter authRequestLimiter,
            @Value("${server.servlet.session.cookie.same-site:lax}") String cookieSameSite,
            @Value("${server.servlet.session.cookie.secure:false}") boolean cookieSecure) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.authRequestLimiter = authRequestLimiter;
        this.cookieSameSite = normalizeSameSite(cookieSameSite);
        this.cookieSecure = cookieSecure;
    }

    public SessionAuthenticationService(
            AuthenticationManager authenticationManager,
            HttpSessionSecurityContextRepository securityContextRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            AuthRequestLimiter authRequestLimiter) {
        this(
                authenticationManager,
                securityContextRepository,
                sessionAuthenticationStrategy,
                authRequestLimiter,
                "lax",
                false);
    }

    public SessionAuthenticationService(
            AuthenticationManager authenticationManager,
            HttpSessionSecurityContextRepository securityContextRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy) {
        this(
                authenticationManager,
                securityContextRepository,
                sessionAuthenticationStrategy,
                null,
                "lax",
                false);
    }

    /** 인증 결과의 프로필로 세션을 만들고 세션 ID·CSRF 토큰을 함께 회전한다. */
    public UserResponse login(
            LoginRequest input, HttpServletRequest request, HttpServletResponse response) {
        Authentication authenticated = authenticate(input, request);
        if (!(authenticated.getPrincipal() instanceof AccountPrincipal account)) {
            throw new CustomException(UserErrorCode.INVALID_CREDENTIALS);
        }

        // 비밀번호 해시가 포함된 UserDetails 대신 공개 식별 정보만 세션에 저장한다.
        LoginUser principal = UserConverter.toLoginUser(account);
        UsernamePasswordAuthenticationToken sessionAuth =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, authenticated.getAuthorities());
        sessionAuth.setDetails(new SessionSecurityVersion(account.securityVersion()));
        // JSON Controller 로그인은 필터의 자동 처리 대상이 아니므로 세션 고정 방지를 직접 적용한다.
        // 필터와 동일한 CSRF 저장소를 사용하는 공유 전략으로 세션 ID와 이전 토큰을 회전한다.
        sessionAuthenticationStrategy.onAuthentication(sessionAuth, request, response);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(sessionAuth);
        SecurityContextHolder.setContext(context);
        // 현재 요청의 ThreadLocal 설정만으로는 다음 요청에 인증이 복원되지 않는다.
        securityContextRepository.saveContext(context, request, response);
        HttpSession authenticatedSession = request.getSession(false);
        if (authenticatedSession != null) {
            authenticatedSession.setMaxInactiveInterval(AUTHENTICATED_SESSION_TIMEOUT_SECONDS);
        }
        return UserConverter.toResponse(principal);
    }

    /** 현재 세션을 종료한다. 다른 세션은 저장된 계정 버전으로 다음 요청에서 차단한다. */
    public void invalidateSession(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException alreadyInvalidated) {
                // 같은 세션에 대한 동시 요청이 먼저 무효화했을 수 있다.
            }
        }
        SecurityContextHolder.clearContext();
        // 교차 사이트(SameSite=None) 환경에서도 브라우저가 삭제 쿠키를 수용하도록 발급 시점과 동일한 속성을 명시한다.
        boolean effectiveSecure =
                cookieSecure || request.isSecure() || "None".equalsIgnoreCase(cookieSameSite);
        ResponseCookie expiredCookie =
                ResponseCookie.from("JSESSIONID", "")
                        .path("/")
                        .httpOnly(true)
                        .secure(effectiveSecure)
                        .sameSite(cookieSameSite)
                        .maxAge(0)
                        .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());
    }

    private static String normalizeSameSite(String rawSameSite) {
        if (rawSameSite == null || rawSameSite.isBlank()) {
            return "Lax";
        }
        return switch (rawSameSite.strip().toLowerCase(Locale.ROOT)) {
            case "none" -> "None";
            case "strict" -> "Strict";
            default -> "Lax";
        };
    }

    record SessionSecurityVersion(long value) implements Serializable {}

    private Authentication authenticate(LoginRequest input, HttpServletRequest request) {
        String normalizedEmail = EmailNormalizer.normalize(input.email());
        if (authRequestLimiter != null) {
            authRequestLimiter.recordLoginAccountAttempt(normalizedEmail);
        }
        Permit directHashingPermit =
                (authRequestLimiter != null && !AuthRateLimitFilter.hasActivePermit(request))
                        ? authRequestLimiter.acquireHashingSlot()
                        : null;
        try (directHashingPermit) {
            return authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            normalizedEmail, input.password()));
        } catch (AuthenticationException exception) {
            // 계정 존재 여부가 로그인 실패 메시지로 드러나지 않게 통일한다.
            throw new CustomException(UserErrorCode.INVALID_CREDENTIALS);
        }
    }
}
