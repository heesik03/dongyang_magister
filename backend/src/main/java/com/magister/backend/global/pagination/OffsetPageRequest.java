package com.magister.backend.global.pagination;

import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** HTTP 경계 외 호출에서도 페이지 크기 상한을 일관되게 보장한다. */
public record OffsetPageRequest(int page, int size) {
    public OffsetPageRequest {
        if (page < 0 || size < 1 || size > 100) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    public static OffsetPageRequest of(int page, int size) {
        return new OffsetPageRequest(page, size);
    }

    public Pageable toPageable(Sort sort) {
        return PageRequest.of(page, size, sort);
    }
}
