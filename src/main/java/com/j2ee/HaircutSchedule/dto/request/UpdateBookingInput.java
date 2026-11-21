package com.j2ee.HaircutSchedule.dto.request;

import com.j2ee.HaircutSchedule.enums.BookingStatus;
import com.j2ee.HaircutSchedule.enums.UserRole;
import org.springframework.graphql.data.ArgumentValue;

import java.time.LocalDate;
import java.time.LocalTime;

public record UpdateBookingInput(
        ArgumentValue<LocalDate> bookingDate,
        ArgumentValue<LocalTime> startTime,
        ArgumentValue<BookingStatus> status
) {
}
