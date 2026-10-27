package com.magister.backend.domain.analysis.service.support;

import com.magister.backend.domain.analysis.error.AnalysisErrorCode;
import com.magister.backend.global.error.exception.CustomException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 사용자·작품·실제 입력·정책·모델에 묶은 10분 유효 확인 토큰. 외부 AI 호출은 없다. */
@Component
public class AnalysisPreviewToken {
    private final byte[] key;

    public AnalysisPreviewToken(@Value("${analysis.preview-signing-key:}") String configuredKey) {
        if (configuredKey.isBlank()) {
            key = new byte[32];
            new SecureRandom().nextBytes(key);
        } else {
            if (configuredKey.length() < 32) {
                throw new IllegalArgumentException(
                        "Analysis preview signing key must be at least 32 characters");
            }
            key = configuredKey.getBytes(StandardCharsets.UTF_8);
        }
    }

    public String issue(String payload, Instant expiresAt) {
        String expires = Long.toString(expiresAt.getEpochSecond());
        return expires + "." + sign(payload + "|" + expires);
    }

    public void verify(String token, String payload) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.", -1);
            if (parts.length != 2
                    || parts[0].length() > 12
                    || parts[1].length() != 64
                    || Long.parseLong(parts[0]) < Instant.now().getEpochSecond()
                    || !MessageDigest.isEqual(
                            HexFormat.of().parseHex(parts[1]),
                            HexFormat.of().parseHex(sign(payload + "|" + parts[0])))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException invalid) {
            throw new CustomException(AnalysisErrorCode.PREVIEW_CHANGED);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Cannot sign analysis preview", error);
        }
    }
}
