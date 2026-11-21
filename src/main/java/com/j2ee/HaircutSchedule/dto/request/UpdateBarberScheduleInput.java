package com.j2ee.HaircutSchedule.dto.request;

import org.springframework.graphql.data.ArgumentValue;

import java.time.LocalTime;

public record UpdateBarberScheduleInput(
        ArgumentValue<Integer> dateOfWeek,
        ArgumentValue<LocalTime> startTime,
        ArgumentValue<LocalTime> endTime,
        ArgumentValue<Boolean> isOff
) {
}
