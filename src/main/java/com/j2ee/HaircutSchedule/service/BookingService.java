package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.dto.response.BookingResponse;
import com.j2ee.HaircutSchedule.dto.response.PageResponse;
import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.enums.BookingStatus;
import org.springframework.graphql.data.ArgumentValue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface BookingService {
    PageResponse<BookingResponse> getAllBookings(
            User currentUser,
            Integer page,
            Integer limit,
            LocalDate bookingDate,
            BookingStatus status,
            Integer barberId,
            Integer customerId
    );
    BookingResponse getBookingById(
            User currentUser,
            Integer id
    );
    BookingResponse createBooking(
            User currentUser,
            Integer barberId,
            LocalDate bookingDate,
            LocalTime startTime,
            List<Integer> serviceIds
    );
    BookingResponse updateBookingStatus(
            User currentUser,
            Integer bookingId,
            BookingStatus status
    );
    BookingResponse updateBookingSchedule(
            User currentUser,
            Integer bookingId,
            ArgumentValue<LocalDate> bookingDate,
            ArgumentValue<LocalTime> startTime
    );
}
