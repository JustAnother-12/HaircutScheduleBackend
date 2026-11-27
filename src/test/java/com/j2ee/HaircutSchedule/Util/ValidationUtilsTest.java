package com.j2ee.HaircutSchedule.Util;

import com.j2ee.HaircutSchedule.util.ValidationUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.graphql.data.ArgumentValue;

import java.math.BigDecimal;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@ExtendWith(MockitoExtension.class)
public class ValidationUtilsTest {

    @Test
    void requireFullName_validBoundaryValues() {
        // Minimum boundary: 3 characters
        String minLength = "Ann";

        // Maximum boundary: 100 characters
        String maxLength = "A".repeat(100);

        // Execute
        String minResult = ValidationUtils.requireFullName(minLength);
        String maxResult = ValidationUtils.requireFullName(maxLength);
        String trimmedResult = ValidationUtils.requireFullName(
                "  Nguyen Van A  "
        );

        // Assert
        assertThat(minResult).isEqualTo("Ann");
        assertThat(minResult).hasSize(3);

        assertThat(maxResult).isEqualTo(maxLength);
        assertThat(maxResult).hasSize(100);

        assertThat(trimmedResult).isEqualTo("Nguyen Van A");
    }

    @Test
    void requireFullName_invalidLengths() {
        String invalidMinLength = "A";
        String invalidMaxLength = "A".repeat(101);

        // Execute
        assertThatThrownBy(() ->
                ValidationUtils.requireFullName(invalidMinLength)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Full name must contain between 3 and 100 characters");

        assertThatThrownBy(() ->
                ValidationUtils.requireFullName(invalidMaxLength)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Full name must contain between 3 and 100 characters");
    }

    @Test
    void requireEmail_normalizesToLowercase() {
        String uppercaseEmail = "TESTEMAIL@GMAIL.COM";

        // Execute & assert
        String emailResult = ValidationUtils.requireEmail(uppercaseEmail);

        assertThat(emailResult)
                .isEqualTo("testemail@gmail.com");
    }

    @Test
    void requireEmail_invalidFormat() {
        String invalidEmail = "invalidEmail";

        // Execute & assert
        assertThatThrownBy(() ->
                ValidationUtils.requireEmail(invalidEmail)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid email format");
    }

    @Test
    void requireEmail_exceeds255() {
        String invalidEmail = "A".repeat(256);

        // Execute & assert
        assertThatThrownBy(() ->
                ValidationUtils.requireEmail(invalidEmail)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email must contain between 1 and 255 characters");
    }

    @Test
    void requirePhone_validPattern() {
        String validPhone = "0912345678";

        // Execute & assert
        String phoneResult = ValidationUtils.requirePhone(validPhone);

        assertThat(phoneResult)
                .isEqualTo("0912345678");
    }

    @Test
    void requirePhone_invalidPattern() {
        String invalidPhone = "0123456789";

        // Execute & assert
        assertThatThrownBy(() ->
                ValidationUtils.requirePhone(invalidPhone)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid phone number");
    }

    @Test
    void requirePassword_validBoundaryValues() {
        // Minimum boundary: 8 characters
        String minLength = "A".repeat(8);

        // Maximum boundary: 32characters
        String maxLength = "A".repeat(32);

        // Execute
        String minResult = ValidationUtils.requirePassword(minLength);
        String maxResult = ValidationUtils.requirePassword(maxLength);

        // Assert
        assertThat(minResult).isEqualTo(minLength);
        assertThat(minResult).hasSize(8);

        assertThat(maxResult).isEqualTo(maxLength);
        assertThat(maxResult).hasSize(32);
    }

    @Test
    void requirePassword_invalidLengths() {
        String invalidMinLength = "A".repeat(7);
        String invalidMaxLength = "A".repeat(33);

        // Execute
        assertThatThrownBy(() ->
                ValidationUtils.requirePassword(invalidMinLength)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password must contain between 8 and 32 characters");

        assertThatThrownBy(() ->
                ValidationUtils.requirePassword(invalidMaxLength)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password must contain between 8 and 32 characters");
    }

    @Test
    void requireNonNegativePrice_zeroAccepted() {
        BigDecimal zeroPrice = BigDecimal.ZERO;

        BigDecimal result = ValidationUtils.requireNonNegativePrice(zeroPrice);

        assertThat(result)
                .isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void requireNonNegativePrice_negativeRejected() {
        BigDecimal negPrice = BigDecimal.valueOf(-1);

        assertThatThrownBy(() ->
                ValidationUtils.requireNonNegativePrice(negPrice)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Price must be greater than or equal to zero");

        assertThatThrownBy(() ->
                ValidationUtils.requireNonNegativePrice(null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Price must be greater than or equal to zero");
    }

    @Test
    void requirePositiveDuration_nullZeroNegativeRejected() {
        assertThatThrownBy(() ->
                ValidationUtils.requirePositiveDuration(0)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duration must be greater than zero");

        assertThatThrownBy(() ->
                ValidationUtils.requirePositiveDuration(-1)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duration must be greater than zero");

        assertThatThrownBy(() ->
                ValidationUtils.requirePositiveDuration(null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duration must be greater than zero");
    }

    @Test
    void requireDateOfWeek_boundaries() {
        assertThat(ValidationUtils.requireDateOfWeek(0))
                .isEqualTo(0);

        assertThat(ValidationUtils.requireDateOfWeek(6))
                .isEqualTo(6);

        assertThatThrownBy(() ->
                ValidationUtils.requireDateOfWeek(-1)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("dateOfWeek must be between 0 and 6");

        assertThatThrownBy(() ->
                ValidationUtils.requireDateOfWeek(7)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("dateOfWeek must be between 0 and 6");

        assertThatThrownBy(() ->
                ValidationUtils.requireDateOfWeek(null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("dateOfWeek must be between 0 and 6");
    }

    @Test
    void requireValidTimeRange_validAndInvalid() {

        // start before end
        assertDoesNotThrow(() ->
                ValidationUtils.requireValidTimeRange(LocalTime.of(9, 0), LocalTime.of(10, 0))
        );

        // start equals end
        assertThatThrownBy(() ->
                ValidationUtils.requireValidTimeRange(LocalTime.of(9, 0), LocalTime.of(9, 0))
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("End time must be after start time");

        // start after end
        assertThatThrownBy(() ->
                ValidationUtils.requireValidTimeRange(LocalTime.of(10, 0), LocalTime.of(9, 0))
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("End time must be after start time");
    }

    @Test
    void requireValidPagination_boundaries() {

        // valid
        assertDoesNotThrow(() ->
                ValidationUtils.requireValidPagination(1, 10)
        );

        // invalid page
        assertThatThrownBy(() ->
                ValidationUtils.requireValidPagination(0, 10)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Page must be greater than or equal to 1");

        // invalid limit
        assertThatThrownBy(() ->
                ValidationUtils.requireValidPagination(1, 0)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Limit must be between 1 and 100");

        assertThatThrownBy(() ->
                ValidationUtils.requireValidPagination(1, 101)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Limit must be between 1 and 100");
    }

    @Test
    void isProvided_omittedVsProvidedNull() {
        // valid
        assertThat(ValidationUtils.isProvided(ArgumentValue.ofNullable("value")))
                .isTrue();

        // null argument
        assertThat(ValidationUtils.isProvided(null))
                .isFalse();

        // omitted argument
        assertThat(ValidationUtils.isProvided(ArgumentValue.omitted()))
                .isFalse();
    }

    @Test
    void optionalText_nullOrBlank() {
        // Null
        String nullResult = ValidationUtils.optionalText(
                null,
                "Description",
                500
        );

        // Empty
        String emptyResult = ValidationUtils.optionalText(
                "",
                "Description",
                500
        );

        // Blank
        String blankResult = ValidationUtils.optionalText(
                "     ",
                "Description",
                500
        );

        // Assert
        assertThat(nullResult).isNull();
        assertThat(emptyResult).isNull();
        assertThat(blankResult).isNull();
    }

    @Test
    void optionalText_tooLong() {
        // Arrange
        String description = "A".repeat(501);

        // Execute & Assert
        assertThatThrownBy(() ->
                ValidationUtils.optionalText(
                        description,
                        "Description",
                        500
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Description cannot exceed 500 characters");
    }

}
