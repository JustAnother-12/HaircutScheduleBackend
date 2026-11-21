package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.dto.response.BookingResponse;
import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.enums.BookingStatus;
import org.springframework.graphql.data.ArgumentValue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface BookingService {
    List<BookingResponse> getAllBookings();
    BookingResponse getBookingById(Integer id);
    BookingResponse createBooking(
            User currentUser,
            Integer barberId,
            LocalDate bookingDate,
            LocalTime startTime,
            List<Integer> serviceIds
    );
    BookingResponse updateBookingStatus(
            Integer bookingId,
            BookingStatus status
    );
    BookingResponse updateBookingSchedule(
            Integer bookingId,
            ArgumentValue<LocalDate> bookingDate,
            ArgumentValue<LocalTime> startTime
    );
}
