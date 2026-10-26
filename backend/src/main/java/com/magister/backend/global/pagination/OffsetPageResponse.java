package com.magister.backend.global.pagination;

import org.springframework.data.domain.Page;

import java.util.List;

public record OffsetPageResponse<T>(
        List<T> items, int page, int size, long totalElements, int totalPages) {
    public static <T> OffsetPageResponse<T> from(Page<T> result) {
        return new OffsetPageResponse<>(
                List.copyOf(result.getContent()),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }
}
