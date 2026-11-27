package com.j2ee.HaircutSchedule.Util;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.j2ee.HaircutSchedule.util.PageUtils;

import org.junit.jupiter.api.Test;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@ExtendWith(MockitoExtension.class)
public class PageUtilsTest {
    @Test
    void pageable_defaultValues() {
        // Execute
        Pageable pageable = assertDoesNotThrow(() ->
                PageUtils.pageable(
                        null,
                        null,
                        Sort.unsorted()
                )
        );

        // Assert
        assertThat(pageable.getPageNumber())
                .isZero();

        assertThat(pageable.getPageSize())
                .isEqualTo(10);

        assertThat(pageable.getSort().isUnsorted())
                .isTrue();
    }

    @Test
    void pageable_convertsOneBasedPage() {
        // Arrange
        int page = 3;
        int limit = 20;

        // Execute
        Pageable pageable = PageUtils.pageable(
                page,
                limit,
                Sort.unsorted()
        );

        // Assert
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(20);
    }

    @Test
    void pageable_invalidPageRejected() {
        // Page = 0
        assertThatThrownBy(() ->
                PageUtils.pageable(
                        0,
                        10,
                        Sort.unsorted()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Page must be greater than or equal to 1");

        // Negative page
        assertThatThrownBy(() ->
                PageUtils.pageable(
                        -1,
                        10,
                        Sort.unsorted()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Page must be greater than or equal to 1");
    }

    @Test
    void pageable_invalidLimitRejected() {
        // Limit = 0
        assertThatThrownBy(() ->
                PageUtils.pageable(
                        1,
                        0,
                        Sort.unsorted()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Limit must be between 1 and 100");

        // Limit > 100
        assertThatThrownBy(() ->
                PageUtils.pageable(
                        1,
                        101,
                        Sort.unsorted()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Limit must be between 1 and 100");
    }

    @Test
    void containsIgnoreCase_returnsLikePattern() {
        // Arrange
        String input = " Cut ";

        // Execute
        String result = PageUtils.containsIgnoreCase(input);

        // Assert
        assertThat(result).isEqualTo("%cut%");
    }

    @Test
    void containsIgnoreCase_nullOrBlankReturnsNull() {
        // Null
        String nullResult = PageUtils.containsIgnoreCase(null);

        // Empty
        String emptyResult = PageUtils.containsIgnoreCase("");

        // Blank
        String blankResult = PageUtils.containsIgnoreCase("   ");

        // Assert
        assertThat(nullResult).isNull();
        assertThat(emptyResult).isNull();
        assertThat(blankResult).isNull();
    }

    @Test
    void normalizeExactFilter_lowercasesAndTrims() {
        // Arrange
        String input = "  Customer@Example.COM  ";

        // Execute
        String result = PageUtils.normalizeExactFilter(input);

        // Assert
        assertThat(result).isEqualTo("customer@example.com");
    }
}
