package com.j2ee.HaircutSchedule.service.impl;


import com.j2ee.HaircutSchedule.dto.response.AvailableSlotResponse;
import com.j2ee.HaircutSchedule.dto.response.BarberScheduleResponse;
import com.j2ee.HaircutSchedule.entity.BarberSchedule;
import com.j2ee.HaircutSchedule.entity.Booking;
import com.j2ee.HaircutSchedule.enums.BookingStatus;
import com.j2ee.HaircutSchedule.exception.ResourceNotFoundException;
import com.j2ee.HaircutSchedule.mapper.BarberScheduleMapper;
import com.j2ee.HaircutSchedule.repository.BarberScheduleRepository;
import com.j2ee.HaircutSchedule.repository.BookingRepository;
import com.j2ee.HaircutSchedule.service.BarberScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.j2ee.HaircutSchedule.service.impl.BookingServiceImpl.BUFFER_MINUTES;
import static com.j2ee.HaircutSchedule.util.Helpers.parseId;

@Service
@RequiredArgsConstructor
public class BarberScheduleServiceImpl implements BarberScheduleService {
    private final BarberScheduleRepository scheduleRepository;
    private final BookingRepository bookingRepository;
    private final BarberScheduleMapper scheduleMapper;

    private record DateTimeSlot(
        LocalDateTime startDt,
        LocalDateTime endDt
    ){}

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
                .orElseThrow(() -> new ResourceNotFoundException("Cannot find schedule with id: " + id));
    }

    @Override
    public AvailableSlotResponse getAvailableSlots(
            Integer barberId,
            LocalDate targetDate,
            int slotDurationMinutes
    ) {
        if (slotDurationMinutes <= 0) {
            throw new IllegalArgumentException(
                    "Slot duration must be greater than zero"
            );
        }

        int dayOfWeek = targetDate.getDayOfWeek().getValue() - 1;
        List<BarberSchedule> schedules = scheduleRepository.findByBarber_IdAndDateOfWeekAndIsOffOrderByStartTimeAsc(
                barberId,
                dayOfWeek,
                false
        );

        if (schedules.isEmpty()) {
            return AvailableSlotResponse.builder()
                    .bookingDate(targetDate)
                    .slotDurationMinutes(slotDurationMinutes)
                    .availableSlots(List.of())
                    .build();
        }

        List<Booking> existedBookings = bookingRepository.findAllByBookingDateAndBarber_IdAndStatusIn(
                targetDate,
                barberId,
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED)
        );

        List<DateTimeSlot> busyIntervals = new ArrayList<>(List.of());
        for(Booking booking : existedBookings){
            LocalDateTime startDt = targetDate.atTime(booking.getStartTime());
            LocalDateTime endDt = targetDate.atTime(booking.getStartTime()).plusMinutes(BUFFER_MINUTES);
            busyIntervals.add(new DateTimeSlot(startDt, endDt));
        }

        List<AvailableSlotResponse.TimeSlot> availableSlot = new ArrayList<>(List.of());
        for(BarberSchedule schedule : schedules){
            LocalDateTime currentTime = targetDate.atTime(schedule.getStartTime());
            LocalDateTime scheduleEnd = targetDate.atTime(schedule.getStartTime());

            while(!currentTime.plusMinutes(slotDurationMinutes).isAfter(scheduleEnd)){
                LocalDateTime slotEnd = currentTime.plusMinutes(slotDurationMinutes);

                LocalDateTime finalCurrentTime = currentTime;
                boolean isOverlapping = busyIntervals.stream()
                        .anyMatch(busy ->
                                finalCurrentTime.isBefore(busy.endDt())
                                        && slotEnd.isAfter(busy.startDt())
                        );

                if(!isOverlapping) {
                    availableSlot.add(
                            new AvailableSlotResponse.TimeSlot(
                                    currentTime.toLocalTime(),
                                    slotEnd.toLocalTime()
                            )
                    );
                }

                currentTime = currentTime.plusMinutes(slotDurationMinutes);
            }
        }


        return AvailableSlotResponse.builder()
                .bookingDate(targetDate)
                .slotDurationMinutes(slotDurationMinutes)
                .availableSlots(availableSlot)
                .build();
    }
}
