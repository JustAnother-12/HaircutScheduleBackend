package com.j2ee.HaircutSchedule.Util;

import com.j2ee.HaircutSchedule.util.Helpers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class HelpersTest {
    @Test
    void parseId_validPositiveInteger() {
        // Execute
        Integer result = Helpers.parseId("123");

        // Assert
        assertThat(result).isEqualTo(123);
    }

    @Test
    void parseId_zeroRejected() {
        assertThatThrownBy(() ->
                Helpers.parseId("0")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ID must be greater than zero");
    }

    @Test
    void parseId_negativeRejected() {
        assertThatThrownBy(() ->
                Helpers.parseId("-1")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ID must be greater than zero");
    }

    @Test
    void parseId_nonNumericRejected() {
        assertThatThrownBy(() ->
                Helpers.parseId("abc")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid ID: abc");
    }

    @Test
    void parseId_nullRejected() {
        assertThatThrownBy(() ->
                Helpers.parseId(null)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid ID: null");
    }
}
