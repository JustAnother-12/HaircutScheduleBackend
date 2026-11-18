package com.j2ee.HaircutSchedule.mapper;

import com.j2ee.HaircutSchedule.dto.response.BarberScheduleResponse;
import com.j2ee.HaircutSchedule.entity.BarberSchedule;
import org.springframework.stereotype.Component;

@Component
public class BarberScheduleMapper {
    public BarberScheduleResponse toResponse(BarberSchedule schedule) {
        if (schedule == null) {
            return null;
        }

        return BarberScheduleResponse.builder()
                .id(schedule.getId())
                .barberId(schedule.getBarber().getId())
                .dateOfWeek(schedule.getDateOfWeek())
                .startTime(schedule.getStartTime())
                .endTime(schedule.getEndTime())
                .isOff(schedule.isOff())
                .build();
    }
}
