package com.magister.backend.domain.user.converter;

import com.magister.backend.domain.user.dto.UserResponse;
import com.magister.backend.domain.user.entity.User;
import com.magister.backend.domain.user.dto.CsrfResponse;
import com.magister.backend.domain.user.dto.EmailCheckResponse;
import com.magister.backend.domain.user.security.LoginUser;
import org.springframework.security.web.csrf.CsrfToken;

public final class UserConverter {
    private UserConverter() {}

    public static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName());
    }

    public static UserResponse toResponse(LoginUser user) {
        return new UserResponse(user.id(), user.email(), user.name());
    }

    public static EmailCheckResponse toEmailCheckResponse(boolean available) {
        return new EmailCheckResponse(available);
    }

    public static CsrfResponse toCsrfResponse(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }
}
