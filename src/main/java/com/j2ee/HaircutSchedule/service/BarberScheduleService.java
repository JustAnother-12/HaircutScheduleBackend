package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.dto.response.BarberScheduleResponse;
import org.springframework.graphql.data.method.annotation.Argument;

import java.util.List;

public interface BarberScheduleService {
    List<BarberScheduleResponse> getAllBarberSchedules(
            String barberId,
            Integer dateOfWeek
    );

    BarberScheduleResponse getBarberScheduleById(Integer id);
}
