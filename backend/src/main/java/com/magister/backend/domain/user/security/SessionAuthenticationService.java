package com.magister.backend.domain.user.security;

import com.magister.backend.domain.user.converter.UserConverter;
import com.magister.backend.domain.user.dto.LoginRequest;
import com.magister.backend.domain.user.dto.UserResponse;
import com.magister.backend.domain.user.error.UserErrorCode;
import com.magister.backend.domain.user.service.support.EmailNormalizer;
import com.magister.backend.global.error.exception.CustomException;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class SessionAuthenticationService {
    private final AuthenticationManager authenticationManager;
    private final HttpSessionSecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final Map<Long, Set<HttpSession>> activeSessions = new ConcurrentHashMap<>();

    /** 인증 결과의 프로필로 세션을 만들고 세션 ID·CSRF 토큰을 함께 회전한다. */
    public UserResponse login(
            LoginRequest input, HttpServletRequest request, HttpServletResponse response) {
        Authentication authenticated = authenticate(input);
        if (!(authenticated.getPrincipal() instanceof AccountPrincipal account)) {
            throw new CustomException(UserErrorCode.INVALID_CREDENTIALS);
        }

        // 비밀번호 해시가 포함된 UserDetails 대신 공개 식별 정보만 세션에 저장한다.
        LoginUser principal = UserConverter.toLoginUser(account);
        Authentication sessionAuth =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, authenticated.getAuthorities());
        // JSON Controller 로그인은 필터의 자동 처리 대상이 아니므로 세션 고정 방지를 직접 적용한다.
        // 필터와 동일한 CSRF 저장소를 사용하는 공유 전략으로 세션 ID와 이전 토큰을 회전한다.
        sessionAuthenticationStrategy.onAuthentication(sessionAuth, request, response);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(sessionAuth);
        SecurityContextHolder.setContext(context);
        // 현재 요청의 ThreadLocal 설정만으로는 다음 요청에 인증이 복원되지 않는다.
        securityContextRepository.saveContext(context, request, response);
        registerSession(principal.id(), request.getSession(false));
        return UserConverter.toResponse(principal);
    }

    /** 필명 변경 시 현재 세션과 동일 계정의 활성 세션 principal을 즉시 갱신한다. */
    public void updateSessionPrincipal(
            LoginUser principal, HttpServletRequest request, HttpServletResponse response) {
        Authentication sessionAuth =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(sessionAuth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        registerSession(principal.id(), request.getSession(false));

        Set<HttpSession> sessions = activeSessions.get(principal.id());
        if (sessions == null) {
            return;
        }
        for (HttpSession session : List.copyOf(sessions)) {
            try {
                session.setAttribute(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            } catch (IllegalStateException expiredSession) {
                sessions.remove(session);
            }
        }
    }

    /** 비밀번호 변경·회원 탈퇴 시 해당 계정의 모든 로그인 세션을 즉시 무효화한다. */
    public void invalidateAllSessions(
            Long userId, HttpServletRequest request, HttpServletResponse response) {
        Set<HttpSession> sessions = activeSessions.remove(userId);
        if (sessions != null) {
            for (HttpSession session : sessions) {
                invalidateQuietly(session);
            }
        }
        if (request != null) {
            invalidateQuietly(request.getSession(false));
        }
        SecurityContextHolder.clearContext();
        if (response != null) {
            Cookie cookie = new Cookie("JSESSIONID", "");
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setMaxAge(0);
            response.addCookie(cookie);
        }
    }

    private void registerSession(Long userId, HttpSession session) {
        if (userId == null || session == null) {
            return;
        }
        activeSessions.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    private void invalidateQuietly(HttpSession session) {
        if (session == null) {
            return;
        }
        try {
            session.invalidate();
        } catch (IllegalStateException alreadyInvalidated) {
            // 이미 만료되거나 무효화된 세션은 무시한다.
        }
    }

    private Authentication authenticate(LoginRequest input) {
        try {
            return authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            EmailNormalizer.normalize(input.email()), input.password()));
        } catch (AuthenticationException exception) {
            // 계정 존재 여부가 로그인 실패 메시지로 드러나지 않게 통일한다.
            throw new CustomException(UserErrorCode.INVALID_CREDENTIALS);
        }
    }
}
