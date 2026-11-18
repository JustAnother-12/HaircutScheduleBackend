package com.j2ee.HaircutSchedule.service.impl;


import com.j2ee.HaircutSchedule.dto.response.BarberScheduleResponse;
import com.j2ee.HaircutSchedule.entity.BarberSchedule;
import com.j2ee.HaircutSchedule.mapper.BarberScheduleMapper;
import com.j2ee.HaircutSchedule.repository.BarberScheduleRepository;
import com.j2ee.HaircutSchedule.service.BarberScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.j2ee.HaircutSchedule.util.helpers.parseId;

@Service
@RequiredArgsConstructor
public class BarberScheduleServiceImpl implements BarberScheduleService {
    private final BarberScheduleRepository scheduleRepository;
    private final BarberScheduleMapper scheduleMapper;

    @Override
    public List<BarberScheduleResponse> getAllBarberSchedules(
            String barberId,
            Integer dateOfWeek
    ) {
        Integer id = parseId(barberId);

        if (dateOfWeek != null && (dateOfWeek < 0 || dateOfWeek > 6)) {
            throw new IllegalArgumentException(
                    "dateOfWeek must be between 0 and 6"
            );
        }

        List<BarberSchedule> schedules;

        if (dateOfWeek == null) {
            schedules = scheduleRepository.findByBarber_IdOrderByDateOfWeekAscStartTimeAsc(id);
        } else {
            schedules = scheduleRepository.findByBarber_IdAndDateOfWeekOrderByStartTimeAsc(id, dateOfWeek);
        }

        return schedules.stream()
                .map(scheduleMapper::toResponse)
                .toList();
    }

    @Override
    public BarberScheduleResponse getBarberScheduleById(Integer id) {

        return scheduleRepository.findById(id)
                .map(scheduleMapper::toResponse)
                .orElse(null);
    }
}
