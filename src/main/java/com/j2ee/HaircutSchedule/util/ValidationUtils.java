package com.j2ee.HaircutSchedule.util;

import org.springframework.graphql.data.ArgumentValue;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.regex.Pattern;

public final class ValidationUtils {

    private ValidationUtils() {}

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    // 03, 05, 07, 08, 09 + 8 chữ số
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^0[35789]\\d{8}$");

    public static boolean isProvided(ArgumentValue<?> value) {
        return value != null && !value.isOmitted();
    }

    public static String requireText(
            String value,
            String field,
            int minLength,
            int maxLength
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " cannot be blank"
            );
        }

        String normalized = value.trim();

        if (normalized.length() < minLength
                || normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " must contain between "
                            + minLength + " and " + maxLength + " characters"
            );
        }

        return normalized;
    }

    public static String optionalText(
            String value,
            String field,
            int maxLength
    ) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " cannot exceed " + maxLength + " characters"
            );
        }

        return normalized.isEmpty() ? null : normalized;
    }

    public static String requireFullName(String value) {
        return requireText(value, "Full name", 3, 100);
    }

    public static String requireServiceName(String value) {
        return requireText(value, "Service name", 1, 100);
    }

    public static String requireEmail(String value) {
        String email = requireText(value, "Email", 1, 320)
                .toLowerCase();

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }

        return email;
    }

    public static String requirePhone(String value) {
        String phone = requireText(value, "Phone", 10, 10);

        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new IllegalArgumentException("Invalid phone number");
        }

        return phone;
    }

    public static String requirePassword(String value) {
        return requireText(value, "Password", 8, 32);
    }

    public static BigDecimal requireNonNegativePrice(
            BigDecimal price
    ) {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Price must be greater than or equal to zero"
            );
        }

        return price;
    }

    public static int requirePositiveDuration(Integer duration) {
        if (duration == null || duration <= 0) {
            throw new IllegalArgumentException(
                    "Duration must be greater than zero"
            );
        }

        return duration;
    }

    public static int requireDateOfWeek(Integer dateOfWeek) {
        if (dateOfWeek == null
                || dateOfWeek < 0
                || dateOfWeek > 6) {
            throw new IllegalArgumentException(
                    "dateOfWeek must be between 0 and 6"
            );
        }

        return dateOfWeek;
    }

    public static void requireValidTimeRange(
            LocalTime startTime,
            LocalTime endTime
    ) {
        if (startTime == null || endTime == null
                || !startTime.isBefore(endTime)) {
            throw new IllegalArgumentException(
                    "End time must be after start time"
            );
        }
    }

    public static void requireValidPagination(
            int page,
            int limit
    ) {
        if (page < 1) {
            throw new IllegalArgumentException(
                    "Page must be greater than or equal to 1"
            );
        }

        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException(
                    "Limit must be between 1 and 100"
            );
        }
    }
}
