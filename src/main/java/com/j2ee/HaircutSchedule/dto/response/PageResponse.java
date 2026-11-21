package com.j2ee.HaircutSchedule.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(
        List<T> items,
        PageInfo pageInfo
) {
    public record PageInfo(
            long total,
            int page,
            int limit,
            int pages
    ) {}

    public static <S, T> PageResponse<T> from(
            Page<S> source,
            Function<? super S, ? extends T> mapper
    ) {
        Page<T> mapped = source.map(mapper);

        return new PageResponse<>(
                mapped.getContent(),
                new PageInfo(
                        mapped.getTotalElements(),
                        mapped.getNumber() + 1,
                        mapped.getSize(),
                        mapped.getTotalPages()
                )
        );
    }
}
