package com.j2ee.HaircutSchedule.controller.graphql;

import com.j2ee.HaircutSchedule.dto.response.BarberScheduleResponse;
import com.j2ee.HaircutSchedule.service.BarberScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BarberScheduleController {
    private final BarberScheduleService barberScheduleService;

    @PreAuthorize("hasAuthority('ADMIN', 'BARBER')")
    @QueryMapping
    public List<BarberScheduleResponse> barberSchedules(
            @Argument String barberId,
            @Argument Integer dateOfWeek
    ) {
        return barberScheduleService.getAllBarberSchedules(barberId, dateOfWeek);
    }
}
