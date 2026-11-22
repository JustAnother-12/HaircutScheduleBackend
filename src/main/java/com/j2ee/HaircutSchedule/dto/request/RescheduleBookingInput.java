package com.j2ee.HaircutSchedule.dto.request;

import org.springframework.graphql.data.ArgumentValue;

import java.time.LocalDate;
import java.time.LocalTime;

public record RescheduleBookingInput(
        ArgumentValue<LocalDate> bookingDate,
        ArgumentValue<LocalTime> startTime
) {
}
