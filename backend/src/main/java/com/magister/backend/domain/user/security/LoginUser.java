package com.magister.backend.domain.user.security;

import java.io.Serializable;

// 세션에는 비밀번호 해시를 보관하지 않는다.
public record LoginUser(Long id, String email, String name) implements Serializable {}
