package com.magister.backend.domain.user.repository;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.regex.Pattern;

/** 공급자별 무결성 예외의 알려진 제약 이름만 업무 오류로 번역한다. */
public final class UserConstraintTranslator {
    private static final Pattern EMAIL_CONSTRAINT =
            Pattern.compile(
                    "(?i)(?<![a-z0-9_])(?:[a-z0-9_]+\\.)?uk_users_email(?:_index_[a-z0-9]+)?(?![a-z0-9_])");

    private UserConstraintTranslator() {}

    /** 다른 email 관련 오류나 일반 무결성 오류를 가입 중복으로 오인하지 않는다. */
    public static boolean isDuplicateEmail(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && violation.getConstraintName() != null
                    && EMAIL_CONSTRAINT.matcher(violation.getConstraintName()).find()) {
                return true;
            }

            String message = cause.getMessage();
            if (message != null && EMAIL_CONSTRAINT.matcher(message).find()) {
                return true;
            }
        }
        return false;
    }
}
