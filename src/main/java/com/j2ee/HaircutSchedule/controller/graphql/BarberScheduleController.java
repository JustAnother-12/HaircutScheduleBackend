package com.j2ee.HaircutSchedule.controller.graphql;

import com.j2ee.HaircutSchedule.dto.request.CreateBarberScheduleInput;
import com.j2ee.HaircutSchedule.dto.request.UpdateBarberScheduleInput;
import com.j2ee.HaircutSchedule.dto.response.AvailableSlotResponse;
import com.j2ee.HaircutSchedule.dto.response.BarberScheduleResponse;
import com.j2ee.HaircutSchedule.dto.response.PageResponse;
import com.j2ee.HaircutSchedule.security.CustomUserDetails;
import com.j2ee.HaircutSchedule.service.BarberScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

import static com.j2ee.HaircutSchedule.util.Helpers.parseId;

@Controller
@RequiredArgsConstructor
public class BarberScheduleController {
    private final BarberScheduleService barberScheduleService;

    @QueryMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'BARBER')")
    public PageResponse<BarberScheduleResponse> barberSchedules(
            @Argument String barberId,
            @Argument Integer page,
            @Argument Integer limit,
            @Argument Integer dateOfWeek,
            @Argument Boolean isOff,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return barberScheduleService.getAllBarberSchedules(
                principal.getUser(),
                parseId(barberId),
                page,
                limit,
                dateOfWeek,
                isOff
        );
    }

    @MutationMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'BARBER')")
    public BarberScheduleResponse createBarberSchedule(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Argument String barberId,
            @Argument CreateBarberScheduleInput input
            ){
        return barberScheduleService.createBarberSchedule(
                principal.getUser(),
                parseId(barberId),
                input.dateOfWeek(),
                input.startTime(),
                input.endTime(),
                input.isOff()
        );
    }

    @MutationMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'BARBER')")
    public BarberScheduleResponse updateBarberSchedule(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Argument String barberId,
            @Argument String scheduleId,
            @Argument UpdateBarberScheduleInput input
    ){
        return barberScheduleService.updateBarberSchedule(
                principal.getUser(),
                parseId(barberId),
                parseId(scheduleId),
                input.dateOfWeek(),
                input.startTime(),
                input.endTime(),
                input.isOff()
        );
    }

    @MutationMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'BARBER')")
    public boolean deleteBarberSchedule(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Argument String barberId,
            @Argument String scheduleId
    ){
        return barberScheduleService.deleteBarberSchedule(
                principal.getUser(),
                parseId(barberId),
                parseId(scheduleId)
        );
    }

    @QueryMapping
    public AvailableSlotResponse availableSlots(
            @Argument String barberId,
            @Argument LocalDate bookingDate,
            @Argument Integer slotDurationMinutes
    ) {
        int duration = slotDurationMinutes == null
                ? 30
                : slotDurationMinutes;

        return barberScheduleService.getAvailableSlots(
                parseId(barberId),
                bookingDate,
                duration
        );
    }
}
