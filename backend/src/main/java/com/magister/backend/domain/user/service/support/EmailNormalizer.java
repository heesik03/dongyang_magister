package com.magister.backend.domain.user.service.support;

import java.util.Locale;

/** 저장·중복 검사·인증 조회에서 동일한 이메일 비교 기준을 사용한다. */
public final class EmailNormalizer {
    private EmailNormalizer() {}

    public static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
