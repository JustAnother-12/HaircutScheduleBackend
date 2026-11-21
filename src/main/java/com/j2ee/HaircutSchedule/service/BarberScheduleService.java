package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.dto.response.AvailableSlotResponse;
import com.j2ee.HaircutSchedule.dto.response.BarberScheduleResponse;
import com.j2ee.HaircutSchedule.dto.response.PageResponse;
import com.j2ee.HaircutSchedule.entity.User;
import org.springframework.graphql.data.ArgumentValue;

import java.time.LocalDate;
import java.time.LocalTime;

public interface BarberScheduleService {
    PageResponse<BarberScheduleResponse> getAllBarberSchedules(
            User currentUser,
            Integer barberId,
            Integer page,
            Integer limit,
            Integer dateOfWeek,
            Boolean isOff
    );

    BarberScheduleResponse getBarberScheduleById(Integer id);

    BarberScheduleResponse createBarberSchedule(
            User currentUser,
            Integer barberId,
            Integer dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            Boolean isOff
    );

    BarberScheduleResponse updateBarberSchedule(
            User currentUser,
            Integer barberId,
            Integer scheduleId,
            ArgumentValue<Integer> dayOfWeek,
            ArgumentValue<LocalTime> startTime,
            ArgumentValue<LocalTime> endTime,
            ArgumentValue<Boolean> isOff
    );

    boolean deleteBarberSchedule(
            User currentUser,
            Integer barberId,
            Integer scheduleId
    );

    AvailableSlotResponse getAvailableSlots(
            Integer barberId,
            LocalDate targetDate,
            int slotDurationMinutes
    );
}
