package com.j2ee.HaircutSchedule.dto.response;

import lombok.Builder;

import java.time.LocalTime;

@Builder
public record BarberScheduleResponse(
        Integer id,
        Integer barberId,
        Integer dateOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        boolean isOff
) {
}
