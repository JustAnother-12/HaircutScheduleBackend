package com.j2ee.HaircutSchedule.service.impl;


import com.j2ee.HaircutSchedule.dto.response.AvailableSlotResponse;
import com.j2ee.HaircutSchedule.dto.response.BarberScheduleResponse;
import com.j2ee.HaircutSchedule.dto.response.PageResponse;
import com.j2ee.HaircutSchedule.entity.BarberSchedule;
import com.j2ee.HaircutSchedule.entity.Booking;
import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.enums.BookingStatus;
import com.j2ee.HaircutSchedule.enums.UserRole;
import com.j2ee.HaircutSchedule.exception.ResourceNotFoundException;
import com.j2ee.HaircutSchedule.mapper.BarberScheduleMapper;
import com.j2ee.HaircutSchedule.repository.BarberScheduleRepository;
import com.j2ee.HaircutSchedule.repository.BookingRepository;
import com.j2ee.HaircutSchedule.repository.UserRepository;
import com.j2ee.HaircutSchedule.service.BarberScheduleService;
import com.j2ee.HaircutSchedule.util.PageUtils;
import com.j2ee.HaircutSchedule.util.ValidationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static com.j2ee.HaircutSchedule.service.impl.BookingServiceImpl.BUFFER_MINUTES;
import static com.j2ee.HaircutSchedule.util.Helpers.parseId;

@Service
@RequiredArgsConstructor
public class BarberScheduleServiceImpl implements BarberScheduleService {
    private final BarberScheduleRepository scheduleRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final BarberScheduleMapper scheduleMapper;

    private record DateTimeSlot(
        LocalDateTime startDt,
        LocalDateTime endDt
    ){}

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BarberScheduleResponse> getAllBarberSchedules(
            User currentUser,
            Integer barberId,
            Integer page,
            Integer limit,
            Integer dateOfWeek,
            Boolean isOff
    ) {
        assertCanAccessBarber(currentUser, barberId);

        if (dateOfWeek != null) {
            ValidationUtils.requireDateOfWeek(dateOfWeek);
        }

        Specification<BarberSchedule> spec =
                (root, query, cb) ->
                        cb.equal(
                                root.get("barber").get("id"),
                                barberId
                        );

        if (dateOfWeek != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("dateOfWeek"), dateOfWeek)
            );
        }

        if (isOff != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("isOff"), isOff)
            );
        }

        Page<BarberSchedule> result = scheduleRepository.findAll(
                spec,
                PageUtils.pageable(
                        page,
                        limit,
                        Sort.by(
                                Sort.Order.asc("dateOfWeek"),
                                Sort.Order.asc("startTime"),
                                Sort.Order.asc("id")
                        )
                )
        );

        return PageResponse.from(result, scheduleMapper::toResponse);
    }

    @Override
    public BarberScheduleResponse getBarberScheduleById(Integer id) {
        return scheduleRepository.findById(id)
                .map(scheduleMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Cannot find schedule with id: " + id));
    }

    @Override
    @Transactional
    public BarberScheduleResponse createBarberSchedule(
            User currentUser,
            Integer barberId,
            Integer dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            Boolean isOff
    ) {
        assertCanAccessBarber(currentUser, barberId);

        if (currentUser.getRole().equals(UserRole.BARBER) && !currentUser.getId().equals(barberId))
            throw new IllegalArgumentException(
                    "Barber can only create their own schedules"
            );

        User barber = getBarberById(barberId);

        Integer validDayOfWeek = ValidationUtils.requireDateOfWeek(dayOfWeek);
        ValidationUtils.requireValidTimeRange(startTime, endTime);

        BarberSchedule schedule = BarberSchedule.builder()
                .barber(barber)
                .dateOfWeek(validDayOfWeek)
                .startTime(startTime)
                .endTime(endTime)
                .isOff(isOff)
                .build();

        BarberSchedule savedSchedule = scheduleRepository.saveAndFlush(schedule);

        if(!schedule.isOff())
            setOtherScheduleOff(savedSchedule);

        return scheduleMapper.toResponse(schedule);
    }

    @Override
    @Transactional
    public BarberScheduleResponse updateBarberSchedule(
            User currentUser,
            Integer barberId,
            Integer scheduleId,
            ArgumentValue<Integer> dayOfWeek,
            ArgumentValue<LocalTime> startTime,
            ArgumentValue<LocalTime> endTime,
            ArgumentValue<Boolean> isOff
    ) {
        assertCanAccessBarber(currentUser, barberId);

        if (currentUser.getRole().equals(UserRole.BARBER) && !currentUser.getId().equals(barberId))
            throw new IllegalArgumentException(
                    "Barber can only update their own schedules"
            );

        BarberSchedule schedule = getBarberScheduleById(scheduleId, barberId);

        boolean changed = false;

        if(ValidationUtils.isProvided(dayOfWeek)){
            schedule.setDateOfWeek(ValidationUtils.requireDateOfWeek(dayOfWeek.value()));
            changed = true;
        }

        if(ValidationUtils.isProvided(isOff)){
            if(isOff.value() == null)
                throw new IllegalArgumentException("Is off cannot be null");

            schedule.setOff(isOff.value());
            changed = true;
        }

        if (ValidationUtils.isProvided(startTime)) {
            if (startTime.value() == null) {
                throw new IllegalArgumentException(
                        "Start time cannot be null"
                );
            }

            schedule.setStartTime(startTime.value());
            changed = true;
        }

        if (ValidationUtils.isProvided(endTime)) {
            if (endTime.value() == null) {
                throw new IllegalArgumentException(
                        "End time cannot be null"
                );
            }

            schedule.setEndTime(endTime.value());
            changed = true;
        }

        ValidationUtils.requireValidTimeRange(
                schedule.getStartTime(),
                schedule.getEndTime()
        );

        if (!changed) {
            throw new IllegalArgumentException(
                    "At least one field must be provided"
            );
        }

        return scheduleMapper.toResponse(schedule);
    }

    @Override
    @Transactional
    public boolean deleteBarberSchedule(
            User currentUser,
            Integer barberId,
            Integer scheduleId
    ) {
        assertCanAccessBarber(currentUser, barberId);

        if (currentUser.getRole().equals(UserRole.BARBER) && !currentUser.getId().equals(barberId))
            throw new IllegalArgumentException(
                    "Barber can only delete their own schedules"
            );

        BarberSchedule schedule = getBarberScheduleById(scheduleId, barberId);

        scheduleRepository.delete(schedule);
        return true;
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
            LocalDateTime endDt = targetDate.atTime(booking.getEndTime()).plusMinutes(BUFFER_MINUTES);
            busyIntervals.add(new DateTimeSlot(startDt, endDt));
        }

        List<AvailableSlotResponse.TimeSlot> availableSlot = new ArrayList<>(List.of());
        for(BarberSchedule schedule : schedules){
            LocalDateTime currentTime = targetDate.atTime(schedule.getStartTime());
            LocalDateTime scheduleEnd = targetDate.atTime(schedule.getEndTime());

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

    private User getBarberById(Integer id){
        return userRepository.findByIdAndRole(id, UserRole.BARBER)
                .orElseThrow(() -> new ResourceNotFoundException("Cannot find Barber with id: " + id));
    }

    private BarberSchedule getBarberScheduleById(Integer id, Integer barberId){
        return scheduleRepository.findByIdAndBarber_Id(id, barberId)
                .orElseThrow(() -> new ResourceNotFoundException("Cannot find Barber's Schedule with id: " + id));
    }

    private void setOtherScheduleOff(BarberSchedule schedule){
        List<BarberSchedule> schedules = scheduleRepository.findByBarber_IdAndDateOfWeekOrderByStartTimeAsc(
                schedule.getBarber().getId(), schedule.getDateOfWeek()
        );

        for(BarberSchedule s : schedules){
            if(s.getId().equals(schedule.getId()))
                continue;
            s.setOff(true);
        }
    }

    private void assertCanAccessBarber(
            User currentUser,
            Integer barberId
    ) {
        if (currentUser == null) {
            throw new AccessDeniedException(
                    "Authentication required"
            );
        }

        if (currentUser.getRole() == UserRole.ADMIN) {
            return;
        }

        if (currentUser.getRole() == UserRole.BARBER
                && currentUser.getId().equals(barberId)) {
            return;
        }

        throw new AccessDeniedException(
                "You cannot access this barber's schedule"
        );
    }
}
