package com.magister.backend.global.pagination;

import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class CursorCodec {
    private static final int VERSION = 1;
    private final ObjectMapper objectMapper;

    private record Token(int version, String scope, String position) {}

    /** 종류·소유자·작품·필터를 길이 구분이 가능한 JSON으로 묶어 다른 문맥의 재사용을 방지한다. */
    public String scope(String... values) {
        try {
            byte[] content = objectMapper.writeValueAsString(values).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is required", error);
        }
    }

    public String encode(String scope, Object position) {
        Token token = new Token(VERSION, scope, objectMapper.writeValueAsString(position));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                objectMapper.writeValueAsString(token).getBytes(StandardCharsets.UTF_8));
    }

    /** 잘못된 형식·버전·문맥은 모두 400으로 처리한다. 소유권은 각 서비스가 다시 검사한다. */
    public <T> T decode(String cursor, String expectedScope, Class<T> type) {
        if (cursor == null) {
            return null;
        }

        try {
            if (cursor.isBlank() || cursor.length() > 2048) {
                throw new IllegalArgumentException();
            }
            Token token = objectMapper.readValue(Base64.getUrlDecoder().decode(cursor), Token.class);
            if (token == null || token.version() != VERSION || !expectedScope.equals(token.scope())
                    || token.position() == null) {
                throw new IllegalArgumentException();
            }
            T position = objectMapper.readValue(token.position(), type);
            if (position == null) {
                throw new IllegalArgumentException();
            }
            return position;
        } catch (RuntimeException error) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
    }
}
