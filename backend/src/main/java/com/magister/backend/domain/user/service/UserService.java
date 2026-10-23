package com.magister.backend.domain.user.service;

import com.magister.backend.domain.user.UserErrorCode;
import com.magister.backend.domain.user.dto.SignupRequest;
import com.magister.backend.domain.user.dto.UserResponse;
import com.magister.backend.domain.user.entity.User;
import com.magister.backend.domain.user.repository.UserRepository;
import com.magister.backend.global.error.exception.CustomException;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder passwords;

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        // 사전 확인은 화면 안내용이며 가입 시 유일 제약 검증도 반드시 수행한다.
        return !users.existsByEmail(normalizeEmail(email));
    }

    public UserResponse signup(SignupRequest request) {
        // 이메일 대소문자 차이로 계정이 중복되지 않게 저장·조회 기준을 통일한다.
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new CustomException(UserErrorCode.EMAIL_ALREADY_EXISTS);
        }
        // 고비용 해시 연산 동안 DB 트랜잭션과 커넥션을 점유하지 않도록 저장 시점에만 트랜잭션을 사용한다.
        // 평문 비밀번호는 저장하지 않으며 응답 DTO에도 포함하지 않는다.
        String passwordHash = passwords.encode(request.password());
        try {
            User user = new User(email, request.name().strip(), passwordHash);
            User saved = users.saveAndFlush(user);
            return UserResponse.from(saved);
        } catch (DataIntegrityViolationException exception) {
            if (isEmailConstraintViolation(exception, email)) {
                // 사전 중복 검사 이후 동시에 가입하는 경우도 DB 유일 제약으로 막는다.
                throw new CustomException(UserErrorCode.EMAIL_ALREADY_EXISTS);
            }
            throw exception;
        }
    }

    private boolean isEmailConstraintViolation(
            DataIntegrityViolationException exception, String email) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("uk_users_email")) {
                return true;
            }
        }
        return users.existsByEmail(email);
    }
}
