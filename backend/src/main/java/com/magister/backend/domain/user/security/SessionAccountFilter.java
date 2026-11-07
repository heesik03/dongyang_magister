package com.magister.backend.domain.user.security;

import com.magister.backend.domain.user.repository.UserRepository;
import com.magister.backend.domain.user.repository.UserRepository.SessionAccount;
import com.magister.backend.domain.user.security.SessionAuthenticationService.SessionSecurityVersion;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public final class SessionAccountFilter extends OncePerRequestFilter {
    private final UserRepository userRepository;
    private final SessionAuthenticationService sessionAuthenticationService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof LoginUser principal
                && authentication.getDetails() instanceof SessionSecurityVersion version) {
            SessionAccount account = userRepository.findSessionAccount(principal.id()).orElse(null);
            if (account == null || account.getSecurityVersion() != version.value()) {
                sessionAuthenticationService.invalidateSession(request, response);
            } else if (!account.getName().equals(principal.name())) {
                // 현재 요청에만 최신 필명을 사용한다. 다른 요청의 세션 인증을 덮어쓰지 않는다.
                LoginUser updated = new LoginUser(principal.id(), principal.email(), account.getName());
                Authentication refreshed = UsernamePasswordAuthenticationToken.authenticated(
                        updated, null, authentication.getAuthorities());
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(refreshed);
                SecurityContextHolder.setContext(context);
            }
        }
        filterChain.doFilter(request, response);
    }
}
