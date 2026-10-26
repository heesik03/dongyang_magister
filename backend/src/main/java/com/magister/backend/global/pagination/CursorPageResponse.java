package com.magister.backend.global.pagination;

import java.util.List;
import java.util.function.Function;

public record CursorPageResponse<T>(List<T> items, boolean hasNext, String nextCursor) {
    /** size+1 조회에서 초과 행은 숨기고 마지막 표시 행으로 다음 위치를 만든다. */
    public static <S, T> CursorPageResponse<T> from(List<S> rows, CursorPageRequest request,
            Function<S, T> converter, Function<S, String> encodeCursor) {
        boolean hasNext = rows.size() > request.size();
        List<S> visible = rows.subList(0, Math.min(rows.size(), request.size()));
        String nextCursor = hasNext ? encodeCursor.apply(visible.getLast()) : null;

        return new CursorPageResponse<>(visible.stream().map(converter).toList(), hasNext, nextCursor);
    }
}
