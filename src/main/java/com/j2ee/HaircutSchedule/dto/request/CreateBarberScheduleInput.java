package com.j2ee.HaircutSchedule.dto.request;

import java.time.LocalTime;

public record CreateBarberScheduleInput(
        Integer dateOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        Boolean isOff
) {}
