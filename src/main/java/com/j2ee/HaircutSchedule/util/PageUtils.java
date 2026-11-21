package com.j2ee.HaircutSchedule.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Locale;

public final class PageUtils {

    private PageUtils() {}

    public static Pageable pageable(
            Integer page,
            Integer limit,
            Sort sort
    ) {
        int validPage = page == null ? 1 : page;
        int validLimit = limit == null ? 10 : limit;

        ValidationUtils.requireValidPagination(
                validPage,
                validLimit
        );

        return PageRequest.of(
                validPage - 1,
                validLimit,
                sort
        );
    }

    public static String containsIgnoreCase(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
    }

    public static String normalizeExactFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
