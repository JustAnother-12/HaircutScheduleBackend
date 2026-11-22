package com.j2ee.HaircutSchedule.service.impl;


import com.j2ee.HaircutSchedule.dto.response.BookingResponse;
import com.j2ee.HaircutSchedule.dto.response.PageResponse;
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
import com.j2ee.HaircutSchedule.util.PageUtils;
import com.j2ee.HaircutSchedule.util.ValidationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.HashSet;
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
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> getAllBookings(
            User currentUser,
            Integer page,
            Integer limit,
            LocalDate bookingDate,
            BookingStatus status,
            Integer barberId,
            Integer customerId
    ) {
        if (currentUser == null) {
            throw new AccessDeniedException(
                    "Authentication required"
            );
        }

        Specification<Booking> spec =
                (root, query, cb) -> cb.conjunction();

        switch (currentUser.getRole()) {

            case ADMIN -> {
                // ADMIN can view all bookings
            }

            case CUSTOMER -> {
                if (customerId != null
                        && !customerId.equals(currentUser.getId())) {
                    throw new AccessDeniedException(
                            "Customer can only view their own bookings"
                    );
                }

                spec = spec.and((root, query, cb) ->
                        cb.equal(
                                root.get("customer").get("id"),
                                currentUser.getId()
                        )
                );
            }

            case BARBER -> {
                if (barberId != null
                        && !barberId.equals(currentUser.getId())) {
                    throw new AccessDeniedException(
                            "Barber can only view their assigned bookings"
                    );
                }

                spec = spec.and((root, query, cb) ->
                        cb.equal(
                                root.get("barber").get("id"),
                                currentUser.getId()
                        )
                );
            }

            default -> throw new AccessDeniedException(
                    "Unsupported user role"
            );
        }

        if (bookingDate != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("bookingDate"), bookingDate)
            );
        }

        if (status != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("status"), status)
            );
        }

        if (barberId != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(
                            root.get("barber").get("id"),
                            barberId
                    )
            );
        }

        if (customerId != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(
                            root.get("customer").get("id"),
                            customerId
                    )
            );
        }

        Page<Booking> result = bookingRepository.findAll(
                spec,
                PageUtils.pageable(
                        page,
                        limit,
                        Sort.by(
                                Sort.Order.desc("bookingDate"),
                                Sort.Order.asc("startTime"),
                                Sort.Order.asc("id")
                        )
                )
        );

        return PageResponse.from(result, bookingMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBookingById(
            User currentUser,
            Integer id
    ) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Cannot find booking with id: " + id
                        )
                );

        assertCanAccessBooking(currentUser, booking);

        return bookingMapper.toResponse(booking);
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

        if (serviceIds.isEmpty())
            throw new IllegalArgumentException("At least one service is required");

        if (new HashSet<>(serviceIds).size() != serviceIds.size())
            throw new IllegalArgumentException("Duplicate service IDs are not allowed");

        List<com.j2ee.HaircutSchedule.entity.Service> services = serviceRepository.findAllById(serviceIds);
        if(services.size() != serviceIds.size())
            throw new IllegalArgumentException("One of the services does not exist");

        if (services.stream().anyMatch(service -> !service.isActive()))
            throw new IllegalArgumentException("Inactive services cannot be booked");

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
            User currentUser,
            Integer bookingId,
            BookingStatus status
    ) {
        if(status == null)
            throw new IllegalArgumentException("Status cannot be null");

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Cannot find booking with id: " + bookingId));

        assertCanUpdateBookingStatus(currentUser, booking);

        booking.setStatus(status);

        return bookingMapper.toResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponse cancelBooking(
            User currentUser,
            Integer bookingId
    ) {

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Cannot find booking with id: " + bookingId));

        assertCanAccessBooking(currentUser, booking);

        if (booking.getStatus() == BookingStatus.COMPLETED
                || booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalArgumentException(
                    "This booking cannot be cancelled"
            );
        }

        booking.setStatus(BookingStatus.CANCELLED);

        return bookingMapper.toResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponse updateBookingSchedule(
            User currentUser,
            Integer bookingId,
            ArgumentValue<LocalDate> bookingDate,
            ArgumentValue<LocalTime> startTime
    ) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Cannot find booking with id: " + bookingId));

        assertCanUpdateBookingSchedule(currentUser, booking);

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

    private void assertCanAccessBooking(
            User currentUser,
            Booking booking
    ) {
        if (currentUser.getRole() == UserRole.ADMIN) {
            return;
        }

        boolean isCustomerOwner =
                currentUser.getRole() == UserRole.CUSTOMER
                        && currentUser.getId().equals(
                        booking.getCustomer().getId()
                );

        boolean isAssignedBarber =
                currentUser.getRole() == UserRole.BARBER
                        && currentUser.getId().equals(
                        booking.getBarber().getId()
                );

        if (!isCustomerOwner && !isAssignedBarber) {
            throw new AccessDeniedException(
                    "You cannot access this booking"
            );
        }
    }

    private void assertCanUpdateBookingStatus(
            User currentUser,
            Booking booking
    ) {
        if (currentUser.getRole() == UserRole.ADMIN) {
            return;
        }

        boolean isAssignedBarber =
                currentUser.getRole() == UserRole.BARBER
                        && currentUser.getId().equals(
                        booking.getBarber().getId()
                );

        if (!isAssignedBarber) {
            throw new AccessDeniedException(
                    "Only the assigned barber or admin can update booking status"
            );
        }
    }

    private void assertCanUpdateBookingSchedule(
            User currentUser,
            Booking booking
    ) {
        boolean isImmutable= booking.getStatus().equals(BookingStatus.COMPLETED) || booking.getStatus().equals(BookingStatus.CANCELLED);

        if (isImmutable) {
            throw new IllegalArgumentException(
                    "Booking was completed or cancelled"
            );
        }

        if (currentUser.getRole() == UserRole.ADMIN) {
            return;
        }

        boolean isOwnerCustomer =
                currentUser.getRole() == UserRole.CUSTOMER
                        && currentUser.getId().equals(
                        booking.getCustomer().getId()
                );

        if (!isOwnerCustomer) {
            throw new AccessDeniedException(
                    "Only the owner customer or admin can update booking schedule"
            );
        }
    }

}
