package com.magister.backend.domain.user.service;

import com.magister.backend.domain.user.UserErrorCode;
import com.magister.backend.domain.user.dto.*;
import com.magister.backend.domain.user.entity.User;
import com.magister.backend.domain.user.repository.UserRepository;
import com.magister.backend.global.error.exception.CustomException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder passwords;

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public UserResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) throw new CustomException(UserErrorCode.EMAIL_ALREADY_EXISTS);
        try {
            return UserResponse.from(users.saveAndFlush(new User(
                    email, request.name().strip(), passwords.encode(request.password()))));
        } catch (DataIntegrityViolationException exception) {
            // 사전 중복 검사 이후 동시에 가입하는 경우도 DB 유일 제약으로 막는다.
            throw new CustomException(UserErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }
}
