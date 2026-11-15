package com.motif.common.response;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Slice;

// 목록 응답 / 전체 개수 없이 다음 페이지 여부만 (무한 스크롤용)
public record SliceResponse<T>(List<T> content, int page, int size, boolean hasNext) {

    public static <E, T> SliceResponse<T> of(Slice<E> slice, Function<E, T> mapper) {
        return new SliceResponse<>(
                slice.getContent().stream().map(mapper).toList(),
                slice.getNumber(),
                slice.getSize(),
                slice.hasNext());
    }
}
