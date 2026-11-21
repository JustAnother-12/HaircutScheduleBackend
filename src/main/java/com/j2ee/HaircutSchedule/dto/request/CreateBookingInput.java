package com.j2ee.HaircutSchedule.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record CreateBookingInput(
        String barberId,
        LocalDate bookingDate,
        LocalTime startTime,
        List<Integer> serviceIds
) {}
