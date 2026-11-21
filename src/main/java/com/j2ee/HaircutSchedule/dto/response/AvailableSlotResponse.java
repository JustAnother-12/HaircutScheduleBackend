package com.j2ee.HaircutSchedule.dto.response;

import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Builder
public record AvailableSlotResponse(
        LocalDate bookingDate,
        int slotDurationMinutes,
        List<TimeSlot> availableSlots
) {
    public record TimeSlot(
            LocalTime startTime,
            LocalTime endTime
    ){}
}
