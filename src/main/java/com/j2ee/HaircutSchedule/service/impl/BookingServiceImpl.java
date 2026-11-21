package com.j2ee.HaircutSchedule.service.impl;


import com.j2ee.HaircutSchedule.dto.response.BookingResponse;
import com.j2ee.HaircutSchedule.entity.BarberSchedule;
import com.j2ee.HaircutSchedule.entity.Booking;
import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.enums.BookingStatus;
import com.j2ee.HaircutSchedule.enums.UserRole;
import com.j2ee.HaircutSchedule.exception.ResourceNotFoundException;
import com.j2ee.HaircutSchedule.mapper.BookingMapper;
import com.j2ee.HaircutSchedule.repository.BarberScheduleRepository;
import com.j2ee.HaircutSchedule.repository.BookingRepository;
import com.j2ee.HaircutSchedule.repository.ServiceRepository;
import com.j2ee.HaircutSchedule.repository.UserRepository;
import com.j2ee.HaircutSchedule.service.BookingService;
import com.j2ee.HaircutSchedule.util.ValidationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {
    private final BookingRepository bookingRepository;
    private final BarberScheduleRepository scheduleRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final BookingMapper bookingMapper;
    private final Clock clock;
    public static int BUFFER_MINUTES = 10;


    @Override
    public List<BookingResponse> getAllBookings() {
        List<Booking> bookings = bookingRepository.findAllByOrderByBookingDateDescStartTimeAsc();

        return bookings.stream()
                .map(bookingMapper::toResponse)
                .toList();
    }

    @Override
    public BookingResponse getBookingById(Integer id) {
        return bookingRepository.findById(id)
                .map(bookingMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Cannot find booking with id: " + id));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public BookingResponse createBooking(
            User currentUser,
            Integer barberId,
            LocalDate bookingDate,
            LocalTime startTime,
            List<Integer> serviceIds
    ) {
        User barber = userRepository.findByIdAndRole(barberId, UserRole.BARBER)
                .orElseThrow(() -> new ResourceNotFoundException("Cannot find barber with id: " + barberId));

        if(!barber.isActive())
            throw new IllegalArgumentException("The requested Barber is inactive");

        if(bookingDate.isBefore(LocalDate.now(clock)))
            throw new IllegalArgumentException("Booking date must not be from the past");

        List<com.j2ee.HaircutSchedule.entity.Service> services = serviceRepository.findAllById(serviceIds);
        if(services.size() != serviceIds.size())
            throw new IllegalArgumentException("One of the services does not exist");


        BigDecimal totalPrice = BigDecimal.ZERO;
        int totalDuration = 0;
        for(com.j2ee.HaircutSchedule.entity.Service service : services){
            totalPrice = totalPrice.add(service.getPrice());
            totalDuration += service.getDurationMinutes();
        }

        LocalDateTime startDateTime = bookingDate.atTime(startTime);
        LocalDateTime endDateTime = startDateTime.plusMinutes(totalDuration);

        if (!endDateTime.toLocalDate().equals(bookingDate)) {
            throw new IllegalArgumentException(
                    "Booking cannot extend into the next day"
            );
        }

        LocalTime endTime = endDateTime.toLocalTime();

        List<BarberSchedule> schedules = checkForValidBookingDate(
                bookingDate,
                barberId
        );

        checkForWithinSchedule(
                schedules,
                startTime,
                endTime
        );

        checkForBookingOverlapping(
                bookingDate,
                barberId,
                startTime,
                endTime,
                null
        );

        Booking booking = Booking.builder()
                .customer(currentUser)
                .barber(barber)
                .bookingDate(bookingDate)
                .startTime(startTime)
                .endTime(endTime)
                .totalPrice(totalPrice)
                .status(BookingStatus.PENDING)
                .build();

        for (com.j2ee.HaircutSchedule.entity.Service service : services) {
            booking.addBookingService(
                    com.j2ee.HaircutSchedule.entity.BookingService.builder()
                            .service(service)
                            .priceAtBooking(service.getPrice())
                            .build()
            );
        }

        Booking savedBooking = bookingRepository.save(booking);

        return bookingMapper.toResponse(savedBooking);
    }

    @Override
    @Transactional
    public BookingResponse updateBookingStatus(
            Integer bookingId,
            BookingStatus status
    ) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Cannot find booking with id: " + bookingId));

        if (status != null) {
            booking.setStatus(status);
        }

        return bookingMapper.toResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponse updateBookingSchedule(
            Integer bookingId,
            ArgumentValue<LocalDate> bookingDate,
            ArgumentValue<LocalTime> startTime
    ) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Cannot find booking with id: " + bookingId));

        LocalDate effectiveDate =
                ValidationUtils.isProvided(bookingDate)
                        ? bookingDate.value()
                        : booking.getBookingDate();

        LocalTime effectiveStart =
                ValidationUtils.isProvided(startTime)
                        ? startTime.value()
                        : booking.getStartTime();

        if (effectiveDate == null || effectiveStart == null) {
            throw new IllegalArgumentException(
                    "Booking date and start time cannot be null"
            );
        }

        long durationMinutes = Duration.between(
                booking.getStartTime(),
                booking.getEndTime()
        ).toMinutes();

        LocalDateTime newStart = effectiveDate.atTime(effectiveStart);
        LocalDateTime newEnd = newStart.plusMinutes(durationMinutes);

        if (!newEnd.toLocalDate().equals(effectiveDate)) {
            throw new IllegalArgumentException(
                    "Booking cannot extend into the next day"
            );
        }

        LocalTime effectiveEnd = newEnd.toLocalTime();

        List<BarberSchedule> schedules = checkForValidBookingDate(
                effectiveDate,
                booking.getBarber().getId()
        );

        checkForWithinSchedule(
                schedules,
                effectiveStart,
                effectiveEnd
        );

        checkForBookingOverlapping(
                effectiveDate,
                booking.getBarber().getId(),
                effectiveStart,
                effectiveEnd,
                booking.getId()
        );

        booking.setBookingDate(effectiveDate);
        booking.setStartTime(effectiveStart);
        booking.setEndTime(effectiveEnd);

        return bookingMapper.toResponse(booking);
    }

    private List<BarberSchedule> checkForValidBookingDate(LocalDate bookingDate, Integer barberId){
        List<BarberSchedule> schedules = scheduleRepository.findByBarber_IdAndDateOfWeekAndIsOffOrderByStartTimeAsc(
                barberId,
                bookingDate.getDayOfWeek().getValue() - 1,
                false
        );

        if (schedules.isEmpty())
            throw new IllegalArgumentException("Barber does not work on the requested date");

        return schedules;
    }

    private void checkForWithinSchedule(
            List<BarberSchedule> schedules,
            LocalTime startTime,
            LocalTime endTime
    ){
        boolean isWithinAnySchedule = schedules.stream()
                .anyMatch(schedule ->
                        !startTime.isBefore(schedule.getStartTime()) && !endTime.isAfter(schedule.getEndTime())
                );

        if (!isWithinAnySchedule) {
            throw new IllegalArgumentException(
                    "The requested booking time is not within any schedule"
            );
        }
    }

    private void checkForBookingOverlapping(
            LocalDate bookingDate,
            Integer barberId,
            LocalTime startTime,
            LocalTime endTime,
            Integer excludedBookingId
    ){
        List<Booking> existedBookings = bookingRepository.findAllByBookingDateAndBarber_IdAndStatusIn(
                bookingDate,
                barberId,
                List.of(BookingStatus.CONFIRMED, BookingStatus.PENDING)
        );

        if(existedBookings.isEmpty())
            return;

        LocalDateTime requestedStart = bookingDate.atTime(startTime);
        LocalDateTime requestedEnd = bookingDate.atTime(endTime);


        for(Booking booking : existedBookings){
            if (excludedBookingId != null && excludedBookingId.equals(booking.getId())) {
                continue;
            }

            LocalDateTime existedStart = bookingDate.atTime(booking.getStartTime());
            LocalDateTime existedEnd = bookingDate.atTime(booking.getEndTime()).plusMinutes(BUFFER_MINUTES);
            boolean isOverlapping =
                    requestedStart.isBefore(existedEnd)
                            && requestedEnd.isAfter(existedStart);

            if (isOverlapping) {
                throw new IllegalArgumentException(
                        "The requested booking time is overlapping with existed bookings"
                );
            }
        }
    }
}
