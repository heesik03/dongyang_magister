package com.magister.backend.domain.workspace.service.support;

import com.magister.backend.domain.workspace.dto.WorkspaceData;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class WorkspaceCodec {
    private static final int MAX_CONTENT_CHARS = 2_000_000;
    private final ObjectMapper objectMapper;

    /** 큰 JSON 직렬화와 크기 검사는 부모 잠금을 획득하기 전에 수행한다. */
    public String encode(WorkspaceData content) {
        String encoded = objectMapper.writeValueAsString(content);
        if (encoded.length() > MAX_CONTENT_CHARS) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
        return encoded;
    }

    public WorkspaceData decode(String content) {
        return objectMapper.readValue(content, WorkspaceData.class);
    }
}
