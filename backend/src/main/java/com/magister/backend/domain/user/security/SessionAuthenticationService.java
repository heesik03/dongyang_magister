package com.magister.backend.domain.user.security;

import com.magister.backend.domain.user.converter.UserConverter;
import com.magister.backend.domain.user.dto.LoginRequest;
import com.magister.backend.domain.user.dto.UserResponse;
import com.magister.backend.domain.user.entity.User;
import com.magister.backend.domain.user.error.UserErrorCode;
import com.magister.backend.domain.user.repository.UserRepository;
import com.magister.backend.domain.user.service.UserService;
import com.magister.backend.global.error.exception.CustomException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SessionAuthenticationService {
    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final HttpSessionSecurityContextRepository contexts;

    public UserResponse login(
            LoginRequest input, HttpServletRequest request, HttpServletResponse response) {
        Authentication authenticated = authenticate(input);
        User user =
                users.findByEmail(authenticated.getName())
                        .orElseThrow(() -> new CustomException(UserErrorCode.INVALID_CREDENTIALS));
        // 비밀번호 해시가 포함된 UserDetails 대신 공개 식별 정보만 세션에 저장한다.
        LoginUser principal = new LoginUser(user.getId(), user.getEmail(), user.getName());
        Authentication sessionAuth =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, authenticated.getAuthorities());
        // JSON Controller 로그인은 필터의 자동 처리 대상이 아니므로 세션 고정 방지를 직접 적용한다.
        new ChangeSessionIdAuthenticationStrategy()
                .onAuthentication(sessionAuth, request, response);
        // 로그인 전 토큰을 폐기해 인증 경계가 바뀐 이후의 요청에는 새 토큰을 요구한다.
        new CsrfAuthenticationStrategy(new HttpSessionCsrfTokenRepository())
                .onAuthentication(sessionAuth, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(sessionAuth);
        SecurityContextHolder.setContext(context);
        // 현재 요청의 ThreadLocal 설정만으로는 다음 요청에 인증이 복원되지 않는다.
        contexts.saveContext(context, request, response);
        return UserConverter.toResponse(user);
    }

    private Authentication authenticate(LoginRequest input) {
        try {
            return authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            UserService.normalizeEmail(input.email()), input.password()));
        } catch (AuthenticationException exception) {
            // 계정 존재 여부가 로그인 실패 메시지로 드러나지 않게 통일한다.
            throw new CustomException(UserErrorCode.INVALID_CREDENTIALS);
        }
    }
}
