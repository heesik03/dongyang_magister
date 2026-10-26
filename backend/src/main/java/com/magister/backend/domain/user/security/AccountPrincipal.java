package com.magister.backend.domain.user.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;

/** 인증 요청 안에서만 사용한다. ProviderManager가 비밀번호를 지우며 세션에는 LoginUser만 저장한다. */
public final class AccountPrincipal extends User {
    private final Long userId;
    private final String displayName;

    public AccountPrincipal(Long userId, String email, String displayName, String passwordHash) {
        super(email, passwordHash, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        this.userId = userId;
        this.displayName = displayName;
    }

    public Long userId() {
        return userId;
    }

    public String displayName() {
        return displayName;
    }
}
